<script setup lang="ts">
import { ref, nextTick } from 'vue'

const props = defineProps<{ disabled: boolean; agentSelected?: boolean; placeholder?: string }>()
const emit = defineEmits<{ (e: 'send', content: string): void }>()

const input = ref('')
const textareaRef = ref<HTMLTextAreaElement | null>(null)

async function send() {
  const content = input.value.trim()
  if (!content || props.disabled) return
  emit('send', content)
  input.value = ''
  await nextTick()
  if (textareaRef.value) textareaRef.value.style.height = 'auto'
}

function autoResize(e: Event) {
  const el = e.target as HTMLTextAreaElement
  el.style.height = 'auto'
  el.style.height = Math.min(el.scrollHeight, 160) + 'px'
}

function onEnter(e: KeyboardEvent) {
  if (e.key === 'Enter' && !e.shiftKey) {
    e.preventDefault()
    send()
  }
}
</script>

<template>
  <form class="composer" @submit.prevent="send">
    <textarea
      ref="textareaRef"
      v-model="input"
      :placeholder="placeholder || '输入消息…'"
      :disabled="disabled"
      @input="autoResize"
      @keydown="onEnter"
    />
    <button type="submit" class="send-btn" :disabled="disabled || !input.trim()" title="发送">↗</button>
    <small class="hint">Enter 发送 · Shift+Enter 换行</small>
  </form>
</template>

<style scoped>
.composer { position: relative; flex: none; width: min(720px, calc(100% - 48px)); margin: 0 auto 20px; padding-top: 14px; }
.composer::before { content: ''; position: absolute; top: 0; left: 50%; width: 100vw; height: 1px; transform: translateX(-50%); background: var(--c-border); }
textarea { display: block; width: 100%; min-height: 48px; max-height: 160px; resize: none; padding: 14px 54px 26px 16px; border: 1px solid var(--c-border); border-radius: var(--radius-lg); outline: none; background: var(--c-surface); line-height: 1.5; font-size: 14px; }
textarea:focus { border-color: var(--c-primary); box-shadow: 0 0 0 3px rgba(13, 107, 103, .12); }
textarea:disabled { background: #f4f6f6; color: var(--c-text-muted); }
.send-btn { position: absolute; right: 12px; bottom: 26px; width: 34px; height: 34px; border: 0; border-radius: var(--radius); background: var(--c-primary); color: #fff; cursor: pointer; font-size: 18px; display: grid; place-items: center; }
.send-btn:hover:not(:disabled) { background: var(--c-primary-hover); }
.send-btn:disabled { background: var(--c-border); cursor: not-allowed; }
.hint { position: absolute; left: 16px; bottom: 8px; color: var(--c-text-muted); font-size: 11px; }
</style>
