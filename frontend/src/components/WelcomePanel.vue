<script setup lang="ts">
import AssistantPicker from './AssistantPicker.vue'
import type { Agent } from '../types'

defineProps<{ selectedAgentId: string | null }>()
const emit = defineEmits<{ select: [agent: Agent]; useGeneralChat: [] }>()
</script>

<template>
  <section class="welcome-panel">
    <div class="welcome-intro">
      <span class="welcome-mark">✦</span>
      <p class="welcome-kicker">任务对话</p>
      <h1>你今天想完成什么？</h1>
      <p class="welcome-description">选择一个助手，它会用适合这项任务的方式帮你完成工作。</p>
    </div>
    <div class="assistant-section">
      <div class="section-heading">
        <strong>选择助手</strong>
        <span>按任务选择，结果更可靠</span>
      </div>
      <AssistantPicker :model-value="selectedAgentId" @select="emit('select', $event)" />
    </div>
    <div class="general-chat-note">
      <span>没有合适的助手？</span>
      <button type="button" @click="emit('useGeneralChat')">使用通用对话</button>
    </div>
  </section>
</template>

<style scoped>
.welcome-panel { width: min(680px, calc(100% - 48px)); margin: 0 auto; padding: clamp(42px, 9vh, 92px) 0 28px; }
.welcome-intro { max-width: 520px; margin-bottom: 34px; }
.welcome-mark { display: block; margin-bottom: 18px; color: var(--c-accent); font-size: 28px; }
.welcome-kicker { margin: 0 0 9px; color: var(--c-primary); font-size: 12px; font-weight: 700; letter-spacing: .04em; }
.welcome-intro h1 { margin: 0 0 10px; color: var(--c-text); font-size: clamp(27px, 3vw, 36px); letter-spacing: 0; line-height: 1.15; }
.welcome-description { max-width: 460px; margin: 0; color: var(--c-text-secondary); font-size: 15px; line-height: 1.7; }
.assistant-section { padding-top: 18px; border-top: 1px solid var(--c-border); }
.section-heading { display: flex; align-items: baseline; justify-content: space-between; gap: 12px; margin-bottom: 10px; }
.section-heading strong { font-size: 14px; }
.section-heading span { color: var(--c-text-muted); font-size: 12px; }
.general-chat-note { display: flex; align-items: center; justify-content: center; gap: 5px; margin-top: 20px; color: var(--c-text-muted); font-size: 12px; }
.general-chat-note button { border: 0; background: transparent; color: var(--c-primary); cursor: pointer; font-size: 12px; padding: 0; }
.general-chat-note button:hover { text-decoration: underline; }
@media (max-width: 767px) { .welcome-panel { width: calc(100% - 32px); padding-top: 38px; } .section-heading { display: block; } .section-heading span { display: block; margin-top: 4px; } }
</style>
