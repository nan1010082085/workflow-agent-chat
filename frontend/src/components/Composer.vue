<script setup lang="ts">
import { computed, onBeforeUnmount, ref, nextTick } from 'vue'

const props = defineProps<{
  disabled: boolean
  agentSelected?: boolean
  placeholder?: string
  panelOpen?: boolean
  /** 当前助手支持的输入能力（text / file / image…） */
  supportedInputs?: string[]
  /** 助手是否支持需要确认 */
  hitlCapable?: boolean
}>()
const emit = defineEmits<{ (e: 'send', content: string): void; (e: 'close-panel'): void }>()

const input = ref('')
const textareaRef = ref<HTMLTextAreaElement | null>(null)
/** 鼠标短暂离开（如移向滚动条间隙）时延迟关闭，避免误关 */
let leaveTimer: ReturnType<typeof setTimeout> | null = null

const inputs = computed(() => props.supportedInputs || ['text'])
const supportsFile = computed(() =>
  inputs.value.some((i) => i === 'file' || i === 'image' || i === 'document'),
)

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

async function send() {
  const content = input.value.trim()
  if (!content || props.disabled) return
  closePanel()
  emit('send', content)
  input.value = ''
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

onBeforeUnmount(() => clearLeaveTimer())
</script>

<template>
  <form class="composer" @submit.prevent="send" @mouseenter="onComposerEnter" @mouseleave="onComposerLeave">
    <div v-if="panelOpen && $slots.panel" class="composer-panel">
      <slot name="panel" />
    </div>
    <!-- 文本区与工具栏分离，避免多行内容被底部控件遮挡 -->
    <div class="composer-field" :class="{ disabled }">
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
          <el-tooltip v-if="supportsFile" content="文件能力即将开放" placement="top" :show-after="200">
            <button class="cap-btn" type="button" aria-label="添加文件" disabled>
              <svg viewBox="0 0 16 16" width="14" height="14" aria-hidden="true">
                <path d="M9.2 2.8 4.4 7.6a2.6 2.6 0 0 0 3.7 3.7l5.2-5.2a1.8 1.8 0 0 0-2.5-2.5L5.6 8.8" fill="none" stroke="currentColor" stroke-width="1.4" stroke-linecap="round" stroke-linejoin="round" />
              </svg>
            </button>
          </el-tooltip>
        </div>
        <button type="submit" class="send-btn" :disabled="disabled || !input.trim()" title="发送">↗</button>
      </div>
    </div>
    <div class="composer-meta">
      <div class="cap-row" aria-label="当前输入能力">
        <span class="cap-chip">文本</span>
        <span v-if="supportsFile" class="cap-chip muted">文件 · 即将开放</span>
        <span v-if="hitlCapable" class="cap-chip">需要确认</span>
      </div>
      <small class="hint">Enter 发送 · Shift+Enter 换行</small>
    </div>
  </form>
</template>

<style scoped>
.composer { position: relative; flex: none; width: min(840px, calc(100% - 48px)); margin: 0 auto 20px; padding-top: 14px; }
.composer::before { content: none; }
.composer-field {
  display: flex;
  flex-direction: column;
  border: 1px solid var(--c-border);
  border-radius: var(--radius-lg);
  background: var(--c-surface);
  overflow: hidden;
}
.composer-field:focus-within {
  border-color: var(--c-primary);
  box-shadow: 0 0 0 3px rgba(13, 107, 103, .12);
}
.composer-field.disabled { background: #f4f6f6; }
.composer-panel {
  position: absolute;
  right: 0;
  bottom: calc(100% - 4px);
  left: 0;
  z-index: 5;
  display: flex;
  flex-direction: column;
  max-height: min(48vh, 360px);
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
  overflow-y: auto;
  overscroll-behavior: contain;
  padding-right: 2px;
}
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
.cap-chip.muted { color: var(--c-text-muted); background: #f4f6f6; }
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
  .hint { display: none; }
}
</style>
