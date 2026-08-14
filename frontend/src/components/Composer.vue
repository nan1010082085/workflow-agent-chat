<script setup lang="ts">
import { computed, onBeforeUnmount, ref, nextTick } from 'vue'
import { api } from '../api/client'
import {
  platformWsLabel,
  platformWsTone,
  platformWsDetail,
  platformWsLastError,
  reconnectPlatformSocket,
} from '../api/platformSocket'
import type { MessageAttachment, PendingAttachment } from '../types'

const props = defineProps<{
  disabled: boolean
  agentSelected?: boolean
  placeholder?: string
  panelOpen?: boolean
  /** 当前助手支持的输入能力（text / file / image…） */
  supportedInputs?: string[]
  /** 助手是否支持需要确认 */
  hitlCapable?: boolean
  /** 可选：上传时绑定会话 */
  sessionId?: string | null
}>()
const emit = defineEmits<{
  (e: 'send', content: string, attachmentIds: string[]): void
  (e: 'close-panel'): void
}>()

const input = ref('')
const textareaRef = ref<HTMLTextAreaElement | null>(null)
const fileInputRef = ref<HTMLInputElement | null>(null)
const pending = ref<PendingAttachment[]>([])
const uploading = ref(false)
/** 鼠标短暂离开（如移向滚动条间隙）时延迟关闭，避免误关 */
let leaveTimer: ReturnType<typeof setTimeout> | null = null

const inputs = computed(() => props.supportedInputs || ['text'])
const supportsFile = computed(() =>
  inputs.value.some((i) => i === 'file' || i === 'image' || i === 'document'),
)

const canSend = computed(() => {
  if (props.disabled || uploading.value) return false
  const hasText = Boolean(input.value.trim())
  const hasDone = pending.value.some((p) => p.status === 'done')
  const hasBusy = pending.value.some((p) => p.status === 'uploading')
  return (hasText || hasDone) && !hasBusy
})

/**
 * 关闭智能体面板（发送成功或鼠标离开输入区时调用）
 */
function closePanel() {
  if (!props.panelOpen) return
  emit('close-panel')
}

function clearLeaveTimer() {
  if (!leaveTimer) return
  clearTimeout(leaveTimer)
  leaveTimer = null
}

function onComposerEnter() {
  clearLeaveTimer()
}

function onComposerLeave() {
  if (!props.panelOpen) return
  clearLeaveTimer()
  leaveTimer = setTimeout(() => {
    leaveTimer = null
    closePanel()
  }, 120)
}

/**
 * 触发系统文件选择。
 */
function triggerUpload() {
  if (props.disabled || !supportsFile.value) return
  fileInputRef.value?.click()
}

/**
 * 处理选中的本地文件并上传。
 */
async function onFileChange(event: Event) {
  const el = event.target as HTMLInputElement
  const files = el.files
  if (!files?.length) return
  for (const file of Array.from(files)) {
    await uploadOne(file)
  }
  el.value = ''
}

async function uploadOne(file: File) {
  if (file.size > 10 * 1024 * 1024) {
    pending.value.push({
      id: `err-${Date.now()}`,
      filename: file.name,
      mimetype: file.type || 'application/octet-stream',
      size: file.size,
      status: 'error',
      error: '文件过大（上限 10MB）',
    })
    return
  }
  const localId = `local-${Date.now()}-${Math.random().toString(36).slice(2, 7)}`
  const previewUrl = file.type.startsWith('image/') ? URL.createObjectURL(file) : undefined
  pending.value.push({
    id: localId,
    filename: file.name,
    mimetype: file.type || 'application/octet-stream',
    size: file.size,
    status: 'uploading',
    previewUrl,
  })
  uploading.value = true
  try {
    const result: MessageAttachment = await api.uploadFile(file, props.sessionId || undefined)
    const idx = pending.value.findIndex((p) => p.id === localId)
    if (idx >= 0) {
      if (pending.value[idx].previewUrl) URL.revokeObjectURL(pending.value[idx].previewUrl!)
      pending.value[idx] = {
        id: result.id,
        filename: result.filename,
        mimetype: result.mimetype,
        size: result.size || file.size,
        status: 'done',
        previewUrl: result.mimetype.startsWith('image/')
          ? undefined
          : undefined,
      }
    }
  } catch (e: any) {
    const idx = pending.value.findIndex((p) => p.id === localId)
    if (idx >= 0) {
      pending.value[idx] = {
        ...pending.value[idx],
        status: 'error',
        error: e?.message || '上传失败',
      }
    }
  } finally {
    uploading.value = pending.value.some((p) => p.status === 'uploading')
  }
}

/**
 * 移除待发送附件。
 */
function removePending(id: string) {
  const item = pending.value.find((p) => p.id === id)
  if (item?.previewUrl) URL.revokeObjectURL(item.previewUrl)
  pending.value = pending.value.filter((p) => p.id !== id)
}

async function send() {
  if (!canSend.value) return
  const content = input.value.trim()
  const attachmentIds = pending.value.filter((p) => p.status === 'done').map((p) => p.id)
  closePanel()
  emit('send', content, attachmentIds)
  input.value = ''
  pending.value.forEach((p) => { if (p.previewUrl) URL.revokeObjectURL(p.previewUrl) })
  pending.value = []
  await nextTick()
  if (textareaRef.value) textareaRef.value.style.height = 'auto'
}

function autoResize(e: Event) {
  const el = e.target as HTMLTextAreaElement
  el.style.height = 'auto'
  el.style.height = Math.min(el.scrollHeight, 220) + 'px'
}

function onEnter(e: KeyboardEvent) {
  if (e.key === 'Enter' && !e.shiftKey) {
    e.preventDefault()
    send()
  }
}

onBeforeUnmount(() => {
  clearLeaveTimer()
  pending.value.forEach((p) => { if (p.previewUrl) URL.revokeObjectURL(p.previewUrl) })
})

/** WS 状态悬停文案（圆点仅作状态指示） */
const wsTooltipLines = computed(() => {
  const label = platformWsLabel.value || '平台模型实时通道'
  const lines = [label]
  const err = platformWsLastError.value
  if (err && !label.includes(err)) lines.push(err)
  lines.push('点击可重连')
  return lines
})

/**
 * 点击状态点：异常/断开时手动重连。
 */
function onWsClick() {
  reconnectPlatformSocket()
}
</script>

<template>
  <form class="composer" @submit.prevent="send" @mouseenter="onComposerEnter" @mouseleave="onComposerLeave">
    <div v-if="panelOpen && $slots.panel" class="composer-panel">
      <slot name="panel" />
    </div>
    <div class="composer-field" :class="{ disabled }">
      <!-- 贴合边框的双向流光（顺/逆时针对向绕行） -->
      <svg class="stream-ring" aria-hidden="true" focusable="false">
        <rect class="stream-ring-trail stream-cw" pathLength="100" />
        <rect class="stream-ring-path stream-cw" pathLength="100" />
        <rect class="stream-ring-trail stream-ccw" pathLength="100" />
        <rect class="stream-ring-path stream-ccw" pathLength="100" />
      </svg>
      <div v-if="pending.length" class="pending-list">
        <div
          v-for="att in pending"
          :key="att.id"
          class="pending-chip"
          :class="att.status"
        >
          <img v-if="att.previewUrl" :src="att.previewUrl" alt="" class="pending-thumb" />
          <span class="pending-name">{{ att.filename }}</span>
          <span v-if="att.status === 'uploading'" class="pending-status">上传中</span>
          <span v-else-if="att.status === 'error'" class="pending-status error">{{ att.error || '失败' }}</span>
          <button type="button" class="pending-remove" aria-label="移除附件" @click="removePending(att.id)">×</button>
        </div>
      </div>
      <textarea
        ref="textareaRef"
        v-model="input"
        :placeholder="placeholder || '输入消息…'"
        :disabled="disabled"
        @input="autoResize"
        @keydown="onEnter"
      />
      <div class="composer-footer">
        <div class="composer-tools">
          <slot name="tools" />
          <input
            ref="fileInputRef"
            type="file"
            class="file-input"
            multiple
            accept="image/*,.pdf,.txt,.md,.csv,.json,.doc,.docx,.xls,.xlsx"
            @change="onFileChange"
          />
          <el-tooltip
            v-if="supportsFile"
            content="添加文件或图片"
            placement="top"
            :show-after="200"
          >
            <button
              class="cap-btn enabled"
              type="button"
              aria-label="添加文件"
              :disabled="disabled || uploading"
              @click="triggerUpload"
            >
              <svg viewBox="0 0 16 16" width="14" height="14" aria-hidden="true">
                <path d="M9.2 2.8 4.4 7.6a2.6 2.6 0 0 0 3.7 3.7l5.2-5.2a1.8 1.8 0 0 0-2.5-2.5L5.6 8.8" fill="none" stroke="currentColor" stroke-width="1.4" stroke-linecap="round" stroke-linejoin="round" />
              </svg>
            </button>
          </el-tooltip>
        </div>
        <button type="submit" class="send-btn" :disabled="!canSend" title="发送">↗</button>
      </div>
    </div>
    <div class="composer-meta">
      <div class="cap-row" aria-label="当前输入能力">
        <span class="cap-chip">文本</span>
        <span v-if="supportsFile" class="cap-chip">文件</span>
        <span v-if="hitlCapable" class="cap-chip">需要确认</span>
      </div>
      <div class="meta-right">
        <el-tooltip placement="top" :show-after="200">
          <template #content>
            <div class="ws-tip">
              <p>{{ wsTooltipLines[0] }}</p>
              <p
                v-for="(line, i) in wsTooltipLines.slice(1)"
                :key="i"
                class="ws-tip-sub"
              >{{ line }}</p>
            </div>
          </template>
          <button
            type="button"
            class="ws-status"
            :class="platformWsTone"
            :aria-label="wsTooltipLines.join('，')"
            @click="onWsClick"
          >
            <i class="ws-dot" aria-hidden="true" />
          </button>
        </el-tooltip>
        <small class="hint">Enter 发送 · Shift+Enter 换行</small>
      </div>
    </div>
  </form>
</template>

<style scoped>
.composer { position: relative; flex: none; width: min(960px, calc(100% - 48px)); margin: 0 auto 20px; padding-top: 14px; }
.composer::before { content: none; }
.composer-field {
  position: relative;
  display: flex;
  flex-direction: column;
  border: 1.5px solid var(--c-border);
  border-radius: var(--radius-lg);
  background: var(--c-surface);
  overflow: hidden;
  transition: border-color .2s ease, box-shadow .2s ease;
}
.composer-field:focus-within,
.composer-field:hover:not(.disabled) {
  border-color: var(--c-primary);
  box-shadow:
    0 0 0 3px rgba(13, 107, 103, .08),
    0 0 20px rgba(94, 184, 176, .16);
}
.composer-field.disabled { background: #f4f6f6; }
.composer-field.disabled:focus-within,
.composer-field.disabled:hover {
  border-color: var(--c-border);
  box-shadow: none;
}

/** 贴合边框的双向流光层（仅描边，不挡交互） */
.stream-ring {
  position: absolute;
  inset: 0;
  width: 100%;
  height: 100%;
  pointer-events: none;
  overflow: visible;
  opacity: 0;
  z-index: 2;
  transition: opacity .25s ease;
}
.composer-field:focus-within .stream-ring,
.composer-field:hover:not(.disabled) .stream-ring { opacity: 1; }
.composer-field.disabled:focus-within .stream-ring,
.composer-field.disabled:hover .stream-ring { opacity: 0; }
.stream-ring-trail,
.stream-ring-path {
  x: 0.75px;
  y: 0.75px;
  width: calc(100% - 1.5px);
  height: calc(100% - 1.5px);
  rx: 7px;
  ry: 7px;
  fill: none;
  stroke-linecap: round;
  stroke-dashoffset: 0;
}
/** 拖尾：同色青绿，长而轻 */
.stream-ring-trail {
  stroke: rgba(13, 107, 103, .26);
  stroke-width: 1.3;
  stroke-dasharray: 28 72;
}
.stream-ring-trail.stream-ccw {
  stroke: rgba(13, 107, 103, .18);
  stroke-dasharray: 22 78;
}
/** 亮头：青白高光 */
.stream-ring-path {
  stroke: #9fd9d2;
  stroke-width: 2;
  stroke-dasharray: 9 91;
  filter:
    drop-shadow(0 0 2px rgba(159, 217, 210, .7))
    drop-shadow(0 0 6px rgba(13, 107, 103, .26));
}
.stream-ring-path.stream-ccw {
  stroke: #b8e6e0;
  stroke-width: 1.7;
  stroke-dasharray: 7 93;
  filter:
    drop-shadow(0 0 2px rgba(184, 230, 224, .65))
    drop-shadow(0 0 5px rgba(13, 107, 103, .2));
}
.composer-field:focus-within .stream-cw,
.composer-field:hover:not(.disabled) .stream-cw {
  animation: stream-cw 2.8s linear infinite;
}
.composer-field:focus-within .stream-ccw,
.composer-field:hover:not(.disabled) .stream-ccw {
  animation: stream-ccw 3.2s linear infinite;
}

@keyframes stream-cw {
  to { stroke-dashoffset: -100; }
}
@keyframes stream-ccw {
  to { stroke-dashoffset: 100; }
}

@media (prefers-reduced-motion: reduce) {
  .composer-field:focus-within,
  .composer-field:hover:not(.disabled) {
    box-shadow: 0 0 0 3px rgba(13, 107, 103, .1);
  }
  .stream-ring { display: none; }
}
.composer-panel {
  position: absolute;
  right: 0;
  bottom: calc(100% - 4px);
  left: 0;
  z-index: 5;
  display: flex;
  flex-direction: column;
  max-height: min(56vh, 440px);
  padding: 12px;
  overflow: hidden;
  border: 1px solid var(--c-border);
  border-radius: var(--radius);
  background: var(--c-surface);
  box-shadow: 0 8px 24px rgba(20, 40, 40, .12);
}
.composer-panel :deep(.picker) {
  flex: 1;
  min-height: 0;
  overflow-x: hidden;
  overflow-y: auto;
  overscroll-behavior: contain;
  padding: 2px 2px 8px;
}
.pending-list {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  padding: 12px 14px 0;
}
.pending-chip {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  max-width: 100%;
  padding: 4px 8px;
  border: 1px solid var(--c-border);
  border-radius: 999px;
  background: #f4f6f6;
  font-size: 12px;
}
.pending-chip.error { border-color: #e8b4b4; color: #a33; }
.pending-thumb { width: 22px; height: 22px; border-radius: 4px; object-fit: cover; }
.pending-name { max-width: 160px; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.pending-status { color: var(--c-text-muted); font-size: 11px; }
.pending-status.error { color: #a33; }
.pending-remove {
  border: 0;
  background: transparent;
  color: var(--c-text-muted);
  cursor: pointer;
  font-size: 14px;
  line-height: 1;
  padding: 0 2px;
}
.file-input { display: none; }
textarea {
  display: block;
  width: 100%;
  min-height: 96px;
  max-height: 220px;
  resize: none;
  padding: 16px 18px 10px;
  border: 0;
  border-radius: 0;
  outline: none;
  background: transparent;
  line-height: 1.5;
  font-size: 14px;
  box-shadow: none;
}
textarea:disabled { color: var(--c-text-muted); }
.composer-footer {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 10px;
  flex: none;
  min-height: 48px;
  padding: 6px 12px 10px;
  border-top: 1px solid transparent;
}
.composer-tools {
  display: flex;
  align-items: center;
  gap: 8px;
  min-width: 0;
  flex: 1;
}
.cap-btn {
  display: inline-grid;
  place-items: center;
  width: 30px;
  height: 30px;
  padding: 0;
  border: 1px solid var(--c-border);
  border-radius: 50%;
  background: var(--c-surface);
  color: var(--c-text-muted);
  cursor: not-allowed;
  opacity: .72;
}
.cap-btn.enabled {
  cursor: pointer;
  opacity: 1;
  color: var(--c-text-secondary);
}
.cap-btn.enabled:hover:not(:disabled) {
  border-color: var(--c-primary);
  color: var(--c-primary);
}
.cap-btn.enabled:disabled { cursor: not-allowed; opacity: .55; }
.send-btn {
  flex: none;
  width: 36px;
  height: 36px;
  display: grid;
  place-items: center;
  padding: 0;
  border: 0;
  border-radius: 50%;
  background: var(--c-primary);
  color: #fff;
  cursor: pointer;
  font-size: 18px;
  line-height: 1;
}
.send-btn:hover:not(:disabled) { background: var(--c-primary-hover); }
.send-btn:disabled { background: var(--c-border); cursor: not-allowed; }
.composer-meta {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  margin-top: 8px;
  padding: 0 2px;
}
.cap-row { display: flex; flex-wrap: wrap; gap: 6px; min-width: 0; }
.cap-chip {
  display: inline-flex;
  align-items: center;
  padding: 2px 8px;
  border-radius: 999px;
  border: 1px solid var(--c-border);
  background: var(--c-surface);
  color: var(--c-text-secondary);
  font-size: 11px;
}
.meta-right {
  display: flex;
  align-items: center;
  gap: 10px;
  flex: none;
  min-width: 0;
}
.ws-status {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 18px;
  height: 18px;
  padding: 0;
  border: 0;
  border-radius: 50%;
  background: transparent;
  cursor: pointer;
}
.ws-status:hover { background: rgba(13, 107, 103, .06); }
.ws-tip { margin: 0; line-height: 1.4; }
.ws-tip p { margin: 0; }
.ws-tip-sub { margin-top: 2px; opacity: .78; font-size: 12px; }
.ws-dot {
  display: block;
  width: 8px;
  height: 8px;
  border-radius: 50%;
  background: #b7c2c1;
}
.ws-status.ok .ws-dot { background: #2f9e8f; box-shadow: 0 0 0 3px rgba(47, 158, 143, .16); }
.ws-status.pending .ws-dot {
  background: #d4a017;
  animation: ws-pulse 1.1s ease-in-out infinite;
}
.ws-status.streaming .ws-dot {
  background: #5ecfc4;
  box-shadow: 0 0 0 3px rgba(94, 207, 196, .18);
  animation: ws-pulse 0.9s ease-in-out infinite;
}
.ws-status.warn .ws-dot { background: #d4a017; }
.ws-status.err .ws-dot { background: #c44; box-shadow: 0 0 0 3px rgba(204, 68, 68, .14); }
.ws-status.idle .ws-dot { background: #b7c2c1; }
@keyframes ws-pulse {
  0%, 100% { opacity: 1; transform: scale(1); }
  50% { opacity: .45; transform: scale(.85); }
}
.hint {
  flex: none;
  color: var(--c-text-muted);
  font-size: 11px;
  text-align: right;
  white-space: nowrap;
}
@media (max-width: 600px) {
  .composer-tools { gap: 4px; }
  .composer-tools :deep(.model-picker span) { display: none; }
  .composer-tools :deep(.model-picker .el-select) { width: 150px; }
  .composer-panel { max-height: min(56vh, 420px); }
  textarea { min-height: 84px; padding: 14px 14px 8px; }
  .composer-meta { flex-direction: column; align-items: flex-start; }
  .meta-right { width: 100%; justify-content: space-between; }
  .hint { display: none; }
}
</style>
