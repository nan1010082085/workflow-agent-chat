<script setup lang="ts">
import { computed, nextTick, reactive, ref, watch } from 'vue'
import type { Message, RunStatusView, WaitingField, WaitingPayload } from '../types'
import AppMark from './AppMark.vue'
import MessageParts from './message/MessageParts.vue'
import DocumentSummaryList from './message/DocumentSummaryList.vue'
import MessageAttachmentList from './message/MessageAttachmentList.vue'
import {
  contentHasQuestionSection,
  normalizeAssistantContent,
} from '../utils/messageContent'

const props = defineProps<{
  message: Message
  run: RunStatusView | null
  sending: boolean
}>()
const emit = defineEmits<{
  (e: 'resume', action: string, payload?: string): void
  (e: 'cancel'): void
  (e: 'retry'): void
  (e: 'open-process'): void
}>()

const isUser = computed(() => props.message.role === 'user')
const isAssistant = computed(() => props.message.role === 'assistant')

/** 轻量处理状态文案（F-05） */
function statusLabel(status: string): string {
  return {
    PENDING: '排队中',
    RUNNING: '正在处理',
    WAITING_INPUT: '等待你的确认',
    COMPLETED: '已完成',
    FAILED: '处理失败',
    CANCELLED: '已取消',
  }[status] || status
}

function statusClass(status: string): string {
  return {
    RUNNING: 'chip-running',
    WAITING_INPUT: 'chip-waiting',
    COMPLETED: 'chip-success',
    FAILED: 'chip-failed',
    CANCELLED: 'chip-cancelled',
  }[status] || 'chip-cancelled'
}

const DEFAULT_WAITING_ACTIONS: WaitingPayload['actions'] = [
  { action: 'approve', label: '确认继续', style: 'primary' },
  { action: 'reject', label: '需要修改', style: 'danger' },
]

/** inline approval：waiting 卡片紧邻助手消息（F-04） */
const waiting = computed<WaitingPayload | null>(() => {
  if (props.message.status !== 'WAITING_INPUT') return null
  const fromRun = props.run?.waiting
  if (fromRun) {
    const actions = fromRun.actions?.length ? fromRun.actions : DEFAULT_WAITING_ACTIONS
    return {
      prompt: fromRun.prompt || '请确认后继续。',
      fields: fromRun.fields || [],
      actions,
      dangerous: Boolean(fromRun.dangerous),
    }
  }
  return {
    prompt: '请确认后继续。',
    fields: [],
    actions: DEFAULT_WAITING_ACTIONS,
    dangerous: false,
  }
})

/** 规范化后的助手正文（过滤节点 JSON dump） */
const displayContent = computed(() => {
  if (isUser.value) return props.message.content || ''
  return normalizeAssistantContent(props.message.content)
})

const hasProcessMeta = computed(() =>
  Boolean(props.message.thinking)
    || Boolean(props.message.toolCalls?.length)
    || Boolean(props.message.workflowExecution),
)

interface ActionField extends WaitingField {
  shortLabel: string
  hint: string
}

/** 确认卡可填写字段：长问题压缩标签，完整文案作提示 */
const actionFields = computed<ActionField[]>(() => {
  const fields = waiting.value?.fields || []
  return fields.map((f, i) => {
    const label = (f.label || f.key || `字段 ${i + 1}`).trim()
    const isLong = label.length > 36 || f.type === 'textarea'
    return {
      ...f,
      shortLabel: isLong && f.type !== 'select' ? `补充 ${i + 1}` : label,
      hint: isLong && f.type !== 'select' ? label : '',
    }
  })
})

/** 正文已含提问区块时，确认卡不再重复列表 */
const showFieldHints = computed(() => {
  if (!waiting.value?.fields?.length) return false
  return !contentHasQuestionSection(displayContent.value)
})

const inputValue = ref('')
const fieldAnswers = reactive<Record<string, string>>({})

/**
 * 记录补充字段输入。
 * @param {Event} e
 * @param {string} [key]
 */
function onInput(e: Event, key?: string) {
  const value = (e.target as HTMLTextAreaElement | HTMLInputElement | HTMLSelectElement).value
  if (key) fieldAnswers[key] = value
  inputValue.value = value
}

/**
 * 提交审批动作；有字段时附带 JSON answers 供平台 resume。
 * @param {string} action
 */
function submit(action: string) {
  const keys = Object.keys(fieldAnswers)
  let payload: string | undefined
  if (keys.length > 0) {
    payload = JSON.stringify({ ...fieldAnswers })
  } else if (inputValue.value.trim()) {
    payload = inputValue.value.trim()
  }
  emit('resume', action, payload)
  inputValue.value = ''
  for (const k of keys) delete fieldAnswers[k]
}

const showTyping = computed(() =>
  isAssistant.value
    && props.message.status === 'RUNNING'
    && !displayContent.value
    && !props.message.attachments?.length
    && !props.message.documentSummaries?.length,
)

/** WAITING 且尚无正文时，用确认说明兜底，避免气泡空洞 */
const waitingFallbackContent = computed(() => {
  if (!waiting.value || displayContent.value) return ''
  const lines = ['## 待确认', '', waiting.value.prompt || '请确认后继续。']
  if (waiting.value.fields?.length) {
    lines.push('', '## 需要你补充', '')
    waiting.value.fields.forEach((f, i) => {
      if (f.label) lines.push(`${i + 1}. ${f.label}`)
    })
  }
  return lines.join('\n')
})

const bodyContent = computed(() => displayContent.value || waitingFallbackContent.value)

const showBody = computed(() =>
  Boolean(bodyContent.value)
    || Boolean(props.message.documentSummaries?.length)
    || Boolean(props.message.attachments?.length),
)

const showWaitingHint = computed(() =>
  isAssistant.value
    && props.message.status === 'WAITING_INPUT'
    && !showBody.value
    && !waiting.value,
)

const showFailedHint = computed(() =>
  isAssistant.value && props.message.status === 'FAILED' && !showBody.value,
)

const showCancelledHint = computed(() =>
  isAssistant.value && props.message.status === 'CANCELLED' && !showBody.value,
)

const bubbleTone = computed(() => {
  if (isUser.value) return 'user'
  if (props.message.status === 'WAITING_INPUT') return 'waiting'
  if (props.message.status === 'FAILED') return 'failed'
  return ''
})

/**
 * 复制当前可见正文。
 */
function copyContent() {
  const text = bodyContent.value || props.message.content
  if (text) navigator.clipboard?.writeText(text)
}

/**
 * 下载整段助手正文。
 */
function downloadContent() {
  const text = bodyContent.value || props.message.content
  if (!text) return
  const blob = new Blob([text], { type: 'text/markdown;charset=utf-8' })
  const url = URL.createObjectURL(blob)
  const a = document.createElement('a')
  a.href = url
  a.download = 'reply.md'
  a.click()
  URL.revokeObjectURL(url)
}

function toolStatus(tool: NonNullable<Message['toolCalls']>[number]): string {
  if (tool.error) return '失败'
  if (tool.result !== undefined) return '已完成'
  return '处理中'
}

/** 思考过程展开态：流式时自动展开，结束后平滑收起 */
const thinkingOpen = ref(false)
/** 用户是否手动切换过折叠，避免自动收起覆盖手动展开 */
const thinkingUserToggled = ref(false)
const thinkingBodyEl = ref<HTMLElement | null>(null)
const toolsOpen = ref(false)

const isThinkingStream = computed(() =>
  isAssistant.value
    && props.message.status === 'RUNNING'
    && Boolean(props.message.thinking),
)

/**
 * 按消息状态同步思考折叠：RUNNING 展开，结束态自动收起。
 */
watch(
  () => [props.message.status, Boolean(props.message.thinking?.trim())] as const,
  ([status, hasThinking]) => {
    if (!hasThinking) {
      thinkingOpen.value = false
      thinkingUserToggled.value = false
      return
    }
    if (status === 'RUNNING') {
      thinkingOpen.value = true
      thinkingUserToggled.value = false
      return
    }
    if (!thinkingUserToggled.value) thinkingOpen.value = false
  },
  { immediate: true },
)

/**
 * 流式思考时把内容滚到底，避免限高后看不到最新片段。
 */
watch(
  () => props.message.thinking,
  async () => {
    if (!isThinkingStream.value) return
    await nextTick()
    const el = thinkingBodyEl.value
    if (el) el.scrollTop = el.scrollHeight
  },
)

/**
 * 切换思考过程展开。
 */
function toggleThinking() {
  thinkingOpen.value = !thinkingOpen.value
  thinkingUserToggled.value = true
}

/**
 * 切换处理步骤展开。
 */
function toggleTools() {
  toolsOpen.value = !toolsOpen.value
}
</script>

<template>
  <div class="message" :class="[message.role, message.status && isAssistant ? `st-${message.status}` : '']">
    <AppMark v-if="isAssistant" variant="ai" size="sm" />
    <div class="bubble-wrap">
      <p v-if="isAssistant && message.tip" class="tip">{{ message.tip }}</p>

      <!-- 1. 正文层：始终优先展示可读内容 -->
      <div v-if="showTyping" class="typing">
        <span class="status-text">正在处理</span>
        <span class="dots"><i /><i /><i /></span>
      </div>

      <div v-else-if="showBody" class="result-wrap">
        <div class="bubble message-content" :class="bubbleTone">
          <MessageAttachmentList
            v-if="message.attachments?.length"
            :attachments="message.attachments"
          />
          <MessageParts v-if="bodyContent" :content="bodyContent" />
          <DocumentSummaryList
            v-if="message.documentSummaries?.length"
            :summaries="message.documentSummaries"
          />
          <p
            v-if="isAssistant && message.status === 'RUNNING' && displayContent"
            class="inline-progress"
          >
            仍在处理…
          </p>
        </div>

        <div v-if="isAssistant && message.status === 'COMPLETED'" class="result-actions">
          <el-tooltip content="复制全文" placement="top" :show-after="200">
            <button class="icon-btn" type="button" aria-label="复制" @click="copyContent">
              <svg viewBox="0 0 16 16" width="14" height="14" aria-hidden="true">
                <rect x="5.5" y="5.5" width="8" height="8" rx="1.5" fill="none" stroke="currentColor" stroke-width="1.4" />
                <path d="M3.5 10.5V3.5h7" fill="none" stroke="currentColor" stroke-width="1.4" stroke-linecap="round" />
              </svg>
            </button>
          </el-tooltip>
          <el-tooltip content="下载" placement="top" :show-after="200">
            <button class="icon-btn" type="button" aria-label="下载" @click="downloadContent">
              <svg viewBox="0 0 16 16" width="14" height="14" aria-hidden="true">
                <path d="M8 2.5v7.2M5.2 7.5 8 10.3l2.8-2.8M3.5 13h9" fill="none" stroke="currentColor" stroke-width="1.4" stroke-linecap="round" stroke-linejoin="round" />
              </svg>
            </button>
          </el-tooltip>
          <el-tooltip v-if="hasProcessMeta" content="处理信息" placement="top" :show-after="200">
            <button class="text-link" type="button" @click="emit('open-process')">处理信息</button>
          </el-tooltip>
        </div>

        <div v-if="isAssistant && message.status === 'FAILED'" class="fail-actions">
          <el-tooltip content="重试" placement="top" :show-after="200">
            <button class="icon-btn" type="button" aria-label="重试" @click="emit('retry')">
              <svg viewBox="0 0 16 16" width="14" height="14" aria-hidden="true">
                <path d="M3.2 8a4.8 4.8 0 0 1 8.3-3.2" fill="none" stroke="currentColor" stroke-width="1.4" stroke-linecap="round" />
                <path d="M12.8 8a4.8 4.8 0 0 1-8.3 3.2" fill="none" stroke="currentColor" stroke-width="1.4" stroke-linecap="round" />
                <path d="M11.2 2.6v2.6h-2.6M4.8 13.4v-2.6h2.6" fill="none" stroke="currentColor" stroke-width="1.4" stroke-linecap="round" stroke-linejoin="round" />
              </svg>
            </button>
          </el-tooltip>
          <el-tooltip content="换一个智能体" placement="top" :show-after="200">
            <button class="icon-btn" type="button" aria-label="换一个智能体" @click="emit('cancel')">
              <svg viewBox="0 0 16 16" width="14" height="14" aria-hidden="true">
                <circle cx="5.2" cy="5" r="1.8" fill="none" stroke="currentColor" stroke-width="1.4" />
                <circle cx="10.8" cy="5" r="1.8" fill="none" stroke="currentColor" stroke-width="1.4" />
                <path d="M2.4 12.4c.4-1.8 1.6-2.8 2.8-2.8h.6c.7 0 1.3.3 1.8.8M13.6 12.4c-.4-1.8-1.6-2.8-2.8-2.8h-.6c-.7 0-1.3.3-1.8.8" fill="none" stroke="currentColor" stroke-width="1.4" stroke-linecap="round" />
              </svg>
            </button>
          </el-tooltip>
        </div>
      </div>

      <div v-else-if="showWaitingHint" class="bubble waiting-hint">
        请在下方确认后继续。
      </div>
      <div v-else-if="showFailedHint" class="bubble failed-hint">
        处理失败
        <div class="fail-actions">
          <el-tooltip content="重试" placement="top" :show-after="200">
            <button class="icon-btn" type="button" aria-label="重试" @click="emit('retry')">
              <svg viewBox="0 0 16 16" width="14" height="14" aria-hidden="true">
                <path d="M3.2 8a4.8 4.8 0 0 1 8.3-3.2" fill="none" stroke="currentColor" stroke-width="1.4" stroke-linecap="round" />
                <path d="M12.8 8a4.8 4.8 0 0 1-8.3 3.2" fill="none" stroke="currentColor" stroke-width="1.4" stroke-linecap="round" />
                <path d="M11.2 2.6v2.6h-2.6M4.8 13.4v-2.6h2.6" fill="none" stroke="currentColor" stroke-width="1.4" stroke-linecap="round" stroke-linejoin="round" />
              </svg>
            </button>
          </el-tooltip>
          <el-tooltip content="换一个智能体" placement="top" :show-after="200">
            <button class="icon-btn" type="button" aria-label="换一个智能体" @click="emit('cancel')">
              <svg viewBox="0 0 16 16" width="14" height="14" aria-hidden="true">
                <circle cx="5.2" cy="5" r="1.8" fill="none" stroke="currentColor" stroke-width="1.4" />
                <circle cx="10.8" cy="5" r="1.8" fill="none" stroke="currentColor" stroke-width="1.4" />
                <path d="M2.4 12.4c.4-1.8 1.6-2.8 2.8-2.8h.6c.7 0 1.3.3 1.8.8M13.6 12.4c-.4-1.8-1.6-2.8-2.8-2.8h-.6c-.7 0-1.3.3-1.8.8" fill="none" stroke="currentColor" stroke-width="1.4" stroke-linecap="round" />
              </svg>
            </button>
          </el-tooltip>
        </div>
      </div>
      <div v-else-if="showCancelledHint" class="bubble cancelled-hint">已取消</div>

      <!-- 2. 过程信息：可动画折叠；流式限高，结束自动收起 -->
      <div
        v-if="isAssistant && message.thinking?.trim()"
        class="detail-block"
        :class="{ open: thinkingOpen, streaming: isThinkingStream }"
      >
        <button
          type="button"
          class="detail-summary"
          :aria-expanded="thinkingOpen"
          @click="toggleThinking"
        >
          <span class="chevron" aria-hidden="true" />
          <span>思考过程</span>
          <span v-if="isThinkingStream" class="live-dot" aria-hidden="true" />
        </button>
        <div class="detail-collapse" :aria-hidden="!thinkingOpen">
          <div class="detail-collapse-inner">
            <div
              ref="thinkingBodyEl"
              class="detail-content"
              :class="{ streaming: isThinkingStream }"
            >{{ message.thinking }}</div>
          </div>
        </div>
      </div>
      <div
        v-if="isAssistant && message.toolCalls?.length"
        class="detail-block"
        :class="{ open: toolsOpen }"
      >
        <button
          type="button"
          class="detail-summary"
          :aria-expanded="toolsOpen"
          @click="toggleTools"
        >
          <span class="chevron" aria-hidden="true" />
          <span>处理步骤（{{ message.toolCalls.length }}）</span>
        </button>
        <div class="detail-collapse">
          <div class="detail-collapse-inner">
            <div
              v-for="tool in message.toolCalls"
              :key="tool.id || tool.name"
              class="tool-row"
            >
              <strong>{{ tool.name }}</strong>
              <span :class="{ err: Boolean(tool.error) }">{{ toolStatus(tool) }}</span>
            </div>
          </div>
        </div>
      </div>

      <!-- 3. 状态芯片 -->
      <span
        v-if="isAssistant && message.status !== 'COMPLETED' && message.status !== 'CANCELLED'"
        class="chip"
        :class="statusClass(message.status)"
      >
        <i />{{ statusLabel(message.status) }}
      </span>
      <span v-else-if="isAssistant && message.status === 'COMPLETED'" class="chip chip-success">
        <i />已完成
      </span>

      <!-- 4. 确认操作层：只负责填写与提交，不重复淹没正文 -->
      <div v-if="waiting" class="inline-approval" :class="{ dangerous: waiting.dangerous }">
        <div class="approval-head">
          <span class="title">需要你的确认</span>
          <span v-if="waiting.dangerous" class="danger-tag">需谨慎</span>
        </div>
        <p class="prompt">{{ waiting.prompt }}</p>
        <p class="revise-hint">若结果不准，可在下方补充正确需求后点「需要修改」；我会取消本次确认并按你的说明继续。</p>
        <ul v-if="showFieldHints" class="question-list">
          <li v-for="f in waiting.fields" :key="f.key">{{ f.label }}</li>
        </ul>
        <div v-for="f in actionFields" :key="'input-' + f.key" class="field">
          <label>{{ f.shortLabel }}</label>
          <p v-if="f.hint" class="field-hint">{{ f.hint }}</p>
          <textarea
            v-if="f.type === 'textarea'"
            rows="2"
            :placeholder="f.hint ? '在此补充…' : `请输入${f.shortLabel}`"
            @input="onInput($event, f.key)"
          />
          <select
            v-else-if="f.type === 'select' && f.options?.length"
            @change="onInput($event, f.key)"
          >
            <option value="" disabled selected>请选择</option>
            <option v-for="o in f.options" :key="o" :value="o">{{ o }}</option>
          </select>
          <input
            v-else
            :placeholder="`请输入${f.shortLabel}`"
            @input="onInput($event, f.key)"
          />
        </div>
        <div class="actions">
          <button
            v-for="a in waiting.actions"
            :key="a.action"
            type="button"
            class="btn"
            :class="a.style === 'danger' ? 'btn-danger' : 'btn-primary'"
            :disabled="sending"
            @click="submit(a.action)"
          >
            {{ a.label }}
          </button>
        </div>
      </div>
    </div>
  </div>
</template>

<style scoped>
.message { display: flex; gap: 12px; max-width: 1040px; margin: 0 auto 22px; align-items: flex-start; }
.message.user { justify-content: flex-end; }
.message :deep(.app-mark) { margin-top: 2px; }
.bubble-wrap { display: flex; flex-direction: column; gap: 8px; max-width: 880px; min-width: 0; width: 100%; }
.message.user .bubble-wrap { align-items: flex-end; max-width: 720px; }
.result-wrap {
  display: flex;
  flex-direction: column;
  align-items: stretch;
  width: fit-content;
  max-width: 100%;
  animation: msg-in .28s ease both;
}
.message.user .result-wrap { align-items: flex-end; }
@keyframes msg-in {
  from { opacity: 0; transform: translateY(6px); }
  to { opacity: 1; transform: none; }
}
.tip {
  margin: 0;
  padding: 0 2px;
  color: var(--c-text-muted);
  font-size: 12px;
}
.bubble {
  margin: 0;
  padding: 12px 16px;
  width: fit-content;
  max-width: 100%;
  font-size: 14px;
  line-height: 1.7;
  background: var(--c-surface);
  border: 1px solid var(--c-border);
  border-radius: 14px;
  word-break: break-word;
  box-shadow: 0 1px 2px rgba(20, 40, 40, .04);
}
.bubble.user {
  background: #e8f3f2;
  border-color: #cfe3e1;
}
.bubble.waiting {
  border-color: #f0d9a8;
  background: linear-gradient(180deg, #fffdf8 0%, #fff 48%);
}
.bubble.failed {
  border-color: #f0c4be;
  background: #fff8f7;
}
.message-content > :first-child { margin-top: 0; }
.message-content > :last-child { margin-bottom: 0; }
.inline-progress {
  margin: 10px 0 0;
  font-size: 12px;
  color: var(--c-running);
}
.detail-block {
  width: fit-content;
  max-width: 100%;
  color: var(--c-text-muted);
  font-size: 12px;
  border: 1px solid var(--c-border-soft, var(--c-border));
  border-radius: 8px;
  background: rgba(255, 255, 255, .55);
  padding: 0 8px;
  transition:
    width .28s ease,
    background .22s ease,
    border-color .22s ease,
    box-shadow .22s ease;
}
.detail-block.open {
  width: 100%;
  background: rgba(255, 255, 255, .72);
}
.detail-block.streaming {
  border-color: color-mix(in srgb, var(--c-running) 28%, var(--c-border));
  box-shadow: 0 0 0 1px color-mix(in srgb, var(--c-running) 10%, transparent);
}
.detail-summary {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  width: 100%;
  margin: 0;
  padding: 5px 2px;
  border: 0;
  background: transparent;
  color: inherit;
  font: inherit;
  font-weight: 600;
  cursor: pointer;
  text-align: left;
  border-radius: 6px;
}
.detail-summary:hover { color: var(--c-text-secondary); }
.chevron {
  display: inline-block;
  width: 0;
  height: 0;
  border-style: solid;
  border-width: 4px 0 4px 6px;
  border-color: transparent transparent transparent currentColor;
  opacity: .7;
  transition: transform .22s ease;
  flex: none;
}
.detail-block.open .chevron { transform: rotate(90deg); }
.live-dot {
  width: 6px;
  height: 6px;
  border-radius: 50%;
  background: var(--c-running);
  animation: blink 1.4s infinite both;
  margin-left: 2px;
}
/** grid 0fr/1fr：折叠高度可插值，避免 details 瞬间塌缩 */
.detail-collapse {
  display: grid;
  grid-template-rows: 0fr;
  overflow: hidden;
  transition: grid-template-rows .3s ease;
}
.detail-block.open .detail-collapse { grid-template-rows: 1fr; }
.detail-collapse-inner {
  overflow: hidden;
  min-height: 0;
}
.detail-content {
  margin: 0;
  padding: 0;
  background: transparent;
  border-radius: 8px;
  white-space: pre-wrap;
  line-height: 1.55;
  color: var(--c-text-secondary);
  max-height: 14em;
  overflow: hidden;
  opacity: 0;
  transform: translateY(-4px);
  transition:
    opacity .22s ease,
    transform .22s ease,
    padding .22s ease,
    margin .22s ease,
    background-color .22s ease;
}
.detail-block.open .detail-content {
  margin: 0 0 8px;
  padding: 8px 10px;
  background: var(--c-bg);
  overflow-y: auto;
  opacity: 1;
  transform: none;
  transition-delay: .04s;
}
/** 流式思考限高，避免消息区被撑得过高；顶部淡出旧内容，底部最新可读 */
.detail-block.open .detail-content.streaming {
  max-height: 6.4em;
  mask-image: linear-gradient(180deg, transparent 0%, #000 22%, #000 100%);
  -webkit-mask-image: linear-gradient(180deg, transparent 0%, #000 22%, #000 100%);
}
.tool-row {
  display: flex;
  justify-content: space-between;
  gap: 16px;
  margin: 0 0 8px;
  padding: 6px 8px;
  background: var(--c-bg);
  border-radius: 8px;
}
.err { color: var(--c-danger); }
.waiting-hint { color: var(--c-warning); background: #fdf2df; border-color: #f0d9a8; }
.failed-hint { color: var(--c-danger); background: var(--c-danger-soft); border-color: #f0c4be; }
.cancelled-hint { color: var(--c-text-muted); background: #eef2f2; border-color: var(--c-border); }
.typing {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 12px 15px;
  background: var(--c-surface);
  border: 1px solid var(--c-border);
  border-radius: 14px;
}
.status-text { font-size: 13px; color: var(--c-text-muted); }
.dots { display: inline-flex; gap: 4px; }
.dots i { width: 6px; height: 6px; background: var(--c-running); border-radius: 50%; animation: blink 1.4s infinite both; }
.dots i:nth-child(2) { animation-delay: .2s; }
.dots i:nth-child(3) { animation-delay: .4s; }
@keyframes blink { 0%, 80%, 100% { opacity: .3; } 40% { opacity: 1; } }
.result-actions, .fail-actions { display: flex; align-items: center; gap: 4px; margin-top: 4px; }
.text-link {
  border: 0;
  background: transparent;
  color: var(--c-text-muted);
  font-size: 12px;
  cursor: pointer;
  padding: 4px 6px;
  border-radius: 6px;
}
.text-link:hover { color: var(--c-primary); background: var(--c-primary-soft); }
.icon-btn {
  display: inline-grid;
  place-items: center;
  width: 28px;
  height: 28px;
  padding: 0;
  border: 0;
  border-radius: 6px;
  background: transparent;
  color: var(--c-text-muted);
  cursor: pointer;
}
.icon-btn:hover { color: var(--c-primary); background: var(--c-primary-soft); }
.inline-approval {
  margin-top: 4px;
  padding: 14px;
  width: min(100%, 520px);
  background: var(--c-surface);
  border: 1px solid var(--c-border);
  border-left: 3px solid var(--c-warning);
  border-radius: var(--radius);
  box-shadow: 0 4px 14px rgba(40, 30, 10, .04);
}
.inline-approval.dangerous { border-left-color: var(--c-danger); background: var(--c-danger-soft); }
.approval-head { display: flex; align-items: center; gap: 8px; margin-bottom: 6px; }
.title { font-weight: 700; font-size: 13px; }
.danger-tag { font-size: 10px; font-weight: 700; color: #fff; background: var(--c-danger); padding: 1px 6px; border-radius: 3px; }
.prompt { margin: 0 0 10px; font-size: 13px; line-height: 1.55; color: var(--c-text-secondary); }
.revise-hint {
  margin: -4px 0 10px;
  font-size: 12px;
  line-height: 1.5;
  color: var(--c-text-muted);
}
.question-list {
  margin: 0 0 12px;
  padding: 8px 10px 8px 28px;
  background: var(--c-bg);
  border-radius: 8px;
  color: var(--c-text);
  font-size: 13px;
  line-height: 1.55;
}
.question-list li { margin: 4px 0; }
.field { margin-bottom: 10px; }
.field label { display: block; font-size: 12px; font-weight: 600; color: var(--c-text); margin-bottom: 4px; }
.field-hint {
  margin: 0 0 6px;
  font-size: 12px;
  line-height: 1.5;
  color: var(--c-text-secondary);
}
.field input, .field textarea, .field select {
  display: block;
  width: 100%;
  padding: 8px 10px;
  border: 1px solid var(--c-border);
  border-radius: var(--radius);
  font-size: 13px;
  outline: none;
  background: var(--c-surface);
}
.field input:focus, .field textarea:focus, .field select:focus {
  border-color: var(--c-primary);
  box-shadow: 0 0 0 3px rgba(13, 107, 103, .12);
}
.actions { display: flex; gap: 8px; justify-content: flex-end; flex-wrap: wrap; }
.chip {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  width: fit-content;
  padding: 3px 8px;
  border-radius: 999px;
  font-size: 11px;
  font-weight: 600;
  background: #eef2f2;
  color: var(--c-text-secondary);
  animation: chip-in .24s ease both;
}
.chip i { width: 6px; height: 6px; border-radius: 50%; background: currentColor; }
.chip-running { color: var(--c-running); background: #e8f4ff; }
.chip-waiting { color: var(--c-warning); background: #fdf2df; }
.chip-success { color: var(--c-primary); background: var(--c-primary-soft); }
.chip-failed { color: var(--c-danger); background: var(--c-danger-soft); }
.chip-cancelled { color: var(--c-text-muted); }
@keyframes chip-in {
  from { opacity: 0; transform: translateY(3px); }
  to { opacity: 1; transform: none; }
}
@media (prefers-reduced-motion: reduce) {
  .result-wrap,
  .chip,
  .live-dot,
  .dots i {
    animation: none;
  }
  .detail-block,
  .detail-collapse,
  .detail-content,
  .chevron {
    transition: none;
  }
}
</style>
