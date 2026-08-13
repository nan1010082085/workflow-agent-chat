<script setup lang="ts">
import { ref } from 'vue'
import type { WaitingPayload } from '../types'

const props = defineProps<{ waiting: WaitingPayload | null; disabled: boolean }>()
const emit = defineEmits<{ (e: 'resume', action: string, payload?: string): void }>()

const inputValue = ref('')

function submit(action: string) {
  emit('resume', action, inputValue.value || undefined)
  inputValue.value = ''
}
</script>

<template>
  <div v-if="waiting" class="approval-card" :class="{ dangerous: waiting.dangerous }">
    <div class="header">
      <span class="title">需要你的操作</span>
      <span v-if="waiting.dangerous" class="danger-tag">危险操作</span>
    </div>
    <p class="prompt">{{ waiting.prompt }}</p>
    <div v-for="f in waiting.fields" :key="f.key" class="field">
      <label>{{ f.label }}</label>
      <textarea v-if="f.type === 'textarea'" v-model="inputValue" :placeholder="'请输入' + f.label" rows="2"></textarea>
      <select v-else-if="f.type === 'select' && f.options.length">
        <option v-for="o in f.options" :key="o" :value="o">{{ o }}</option>
      </select>
      <input v-else v-model="inputValue" :placeholder="'请输入' + f.label" />
    </div>
    <div class="actions">
      <button
        v-for="a in waiting.actions" :key="a.action"
        class="btn"
        :class="a.style === 'danger' ? 'btn-danger' : 'btn-primary'"
        :disabled="disabled"
        @click="submit(a.action)"
      >{{ a.label }}</button>
    </div>
  </div>
</template>

<style scoped>
.approval-card { margin: 0 16px 16px; padding: 16px; background: var(--c-surface); border: 1px solid var(--c-border); border-left: 3px solid var(--c-primary); border-radius: var(--radius); }
.approval-card.dangerous { border-left-color: var(--c-danger); background: var(--c-danger-soft); }
.header { display: flex; align-items: center; gap: 8px; margin-bottom: 8px; }
.title { font-weight: 700; font-size: 14px; }
.danger-tag { font-size: 11px; font-weight: 700; color: #fff; background: var(--c-danger); padding: 1px 6px; border-radius: 3px; }
.prompt { margin: 0 0 12px; font-size: 13px; line-height: 1.6; color: var(--c-text-secondary); }
.field { margin-bottom: 10px; }
.field label { display: block; font-size: 12px; color: var(--c-text-muted); margin-bottom: 4px; }
.field input, .field textarea, .field select { display: block; width: 100%; padding: 8px 10px; border: 1px solid var(--c-border); border-radius: var(--radius); font-size: 13px; outline: none; background: var(--c-surface); }
.field input:focus, .field textarea:focus, .field select:focus { border-color: var(--c-primary); }
.actions { display: flex; gap: 8px; justify-content: flex-end; }
</style>
