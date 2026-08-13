<script setup lang="ts">
import { onBeforeUnmount, ref, nextTick } from 'vue'

const props = defineProps<{ disabled: boolean; agentSelected?: boolean; placeholder?: string; panelOpen?: boolean }>()
const emit = defineEmits<{ (e: 'send', content: string): void; (e: 'close-panel'): void }>()

const input = ref('')
const textareaRef = ref<HTMLTextAreaElement | null>(null)
/** 鼠标短暂离开（如移向滚动条间隙）时延迟关闭，避免误关 */
let leaveTimer: ReturnType<typeof setTimeout> | null = null

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
    <div class="composer-field">
      <textarea
        ref="textareaRef"
        v-model="input"
        :placeholder="placeholder || '输入消息…'"
        :disabled="disabled"
        @input="autoResize"
        @keydown="onEnter"
      />
      <div class="composer-tools">
        <slot name="tools" />
      </div>
      <button type="submit" class="send-btn" :disabled="disabled || !input.trim()" title="发送">↗</button>
    </div>
    <small class="hint">Enter 发送 · Shift+Enter 换行</small>
  </form>
</template>

<style scoped>
.composer { position: relative; flex: none; width: min(840px, calc(100% - 48px)); margin: 0 auto 20px; padding-top: 14px; }
.composer::before { content: none; }
.composer-field { position: relative; }
.composer-tools { position: absolute; left: 14px; bottom: 12px; display: flex; align-items: center; gap: 8px; max-width: calc(100% - 72px); min-height: 30px; }
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
  min-height: 112px;
  max-height: 240px;
  resize: none;
  padding: 18px 58px 52px 18px;
  border: 1px solid var(--c-border);
  border-radius: var(--radius-lg);
  outline: none;
  background: var(--c-surface);
  line-height: 1.5;
  font-size: 14px;
}
textarea:focus { border-color: var(--c-primary); box-shadow: 0 0 0 3px rgba(13, 107, 103, .12); }
textarea:disabled { background: #f4f6f6; color: var(--c-text-muted); }
.send-btn {
  position: absolute;
  top: 50%;
  right: 14px;
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
  transform: translateY(-50%);
}
.send-btn:hover:not(:disabled) { background: var(--c-primary-hover); }
.send-btn:disabled { background: var(--c-border); cursor: not-allowed; }
.hint {
  display: block;
  margin-top: 8px;
  padding-right: 2px;
  color: var(--c-text-muted);
  font-size: 11px;
  text-align: right;
}
@media (max-width: 600px) {
  .composer-tools { gap: 4px; max-width: calc(100% - 62px); }
  .composer-tools :deep(.model-picker span) { display: none; }
  .composer-tools :deep(.model-picker .el-select) { width: 150px; }
  .composer-panel { max-height: min(56vh, 420px); }
  textarea { min-height: 96px; padding: 16px 54px 48px 14px; }
  .hint { display: none; }
}
</style>
