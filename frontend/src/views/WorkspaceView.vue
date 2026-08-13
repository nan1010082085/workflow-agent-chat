<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useModelStore } from '../stores/model'
import { useAgentStore } from '../stores/agent'
import { useChatStore } from '../stores/chat'
import { useSessionStore } from '../stores/session'
import type { Agent } from '../types'
import ModelPicker from '../components/ModelPicker.vue'
import AssistantPicker from '../components/AssistantPicker.vue'
import MessageList from '../components/MessageList.vue'
import Composer from '../components/Composer.vue'

const modelStore = useModelStore(); const agentStore = useAgentStore(); const chatStore = useChatStore(); const sessionStore = useSessionStore()
const showWorkflows = ref(false)
const mode = ref<'model' | 'agent'>('model')
const selectedAgent = ref<Agent | null>(null)
const selectedModel = computed({ get: () => modelStore.selectedId, set: (v) => { modelStore.selectedId = v } })
const activeMessages = computed(() => mode.value === 'agent' ? chatStore.messages : chatStore.modelMessages)
const hasMessages = computed(() => activeMessages.value.length > 0)

onMounted(() => { if (!modelStore.models.length) modelStore.fetchModels(); if (!agentStore.agents.length) agentStore.fetchAgents() })
async function send(content: string) {
  if (mode.value === 'agent' && selectedAgent.value) {
    let sessionId = sessionStore.currentSessionId
    if (!sessionId) {
      const session = await sessionStore.createSession(selectedAgent.value.id, selectedAgent.value.name)
      sessionId = session.id
    }
    await chatStore.sendMessage(sessionId, selectedAgent.value.id, content)
    return
  }
  if (modelStore.selectedId) await chatStore.sendModelMessage(modelStore.selectedId, content)
  else modelStore.error = '当前没有可用模型，请稍后重试'
}

function selectAgent(agent: Agent) {
  selectedAgent.value = agent
  mode.value = 'agent'
  showWorkflows.value = false
  sessionStore.currentSessionId = null
  chatStore.clearAgentConversation()
}

function useBaseModel() {
  mode.value = 'model'
  selectedAgent.value = null
  chatStore.clearAgentConversation()
}
</script>
<template>
  <div class="workspace">
    <header class="topbar">
      <div class="topbar-copy"><strong>对话</strong><span class="subtle">{{ mode === 'agent' ? `当前使用：${selectedAgent?.name}` : '当前使用：基础模型' }}</span></div>
      <p v-if="modelStore.error || chatStore.error" class="status-error">{{ modelStore.error || chatStore.error }}</p>
    </header>
    <main class="conversation" :class="{ empty: !hasMessages }">
      <section v-if="!hasMessages" class="welcome">
        <div class="welcome-mark">✦</div><h1>你想聊点什么？</h1>
        <p>{{ mode === 'agent' ? `向「${selectedAgent?.name}」描述你要完成的任务。` : '直接输入问题，开始对话。' }}</p>
      </section>
      <MessageList v-else :messages="activeMessages" :loading="false" :current-run="mode === 'agent' ? chatStore.currentRun : null" @resume="(action, payload) => chatStore.currentRun && chatStore.resumeRun(chatStore.currentRun.runId, action, payload)" @cancel="() => chatStore.currentRun && chatStore.cancelRun(chatStore.currentRun.runId)" />
      <Composer :disabled="chatStore.sending" :panel-open="showWorkflows" :placeholder="mode === 'agent' ? `使用 ${selectedAgent?.name} 处理任务…` : (modelStore.selected() ? `使用 ${modelStore.selected()!.name} 对话…` : '输入消息…')" @send="send">
        <template #tools>
          <ModelPicker v-if="mode === 'model'" v-model="selectedModel" :models="modelStore.models" :loading="modelStore.loading" />
          <button v-if="mode === 'agent'" class="mode-action" type="button" @click="useBaseModel">返回基础模型</button>
          <button class="workflow-toggle" type="button" :aria-expanded="showWorkflows" @click="showWorkflows = !showWorkflows">{{ showWorkflows ? '收起专用能力' : '选择专用能力' }}</button>
        </template>
        <template #panel>
          <div class="panel-title"><strong>已发布的专用能力</strong><button type="button" @click="showWorkflows = false">关闭</button></div>
          <AssistantPicker :model-value="selectedAgent?.id || null" @select="selectAgent" />
          <p v-if="agentStore.error" class="subtle">专用能力暂时不可用，请稍后重试。</p>
        </template>
      </Composer>
    </main>
  </div>
</template>
<style scoped>
.workspace { display:flex; flex-direction:column; height:100%; min-height:0; background:var(--c-bg); }
.topbar { min-height:68px; display:flex; justify-content:space-between; align-items:center; gap:16px; padding:12px 32px; background:var(--c-surface); }.topbar-copy{display:flex;align-items:flex-start;flex-direction:column;gap:0;min-width:0}.status-error{margin:0 0 0 auto;color:var(--c-danger);font-size:12px;white-space:nowrap;overflow:hidden;text-overflow:ellipsis;max-width:420px}
.topbar strong { font-size:16px; }.subtle { display:block; margin-top:4px; color:var(--c-text-muted); font-size:12px; }.workflow-toggle,.panel-title button,.mode-action { border:0; background:transparent; color:var(--c-primary); cursor:pointer; font-size:13px; }.mode-action { padding:7px 10px; border:1px solid var(--c-border); border-radius:6px; background:var(--c-surface); }
.panel-title { display:flex; justify-content:space-between; margin-bottom:12px; }
.conversation { flex:1; display:flex; flex-direction:column; min-height:0; overflow:hidden; }.welcome { margin:auto auto 24px; width:min(720px,calc(100% - 48px)); text-align:center; }.welcome-mark { color:var(--c-accent); font-size:28px; }.welcome h1 { margin:12px 0 8px; font-size:30px; }.welcome p { margin:0 0 20px; color:var(--c-text-secondary); }
@media(max-width:767px){.topbar{padding:12px 16px}.subtle{display:none}.topbar-controls{margin-left:auto}.status-error{position:absolute;top:70px;left:16px;right:16px;max-width:none;padding:8px 10px;background:var(--c-danger-soft);border:1px solid #f0c4be;border-radius:6px;white-space:normal}.workflow-panel{width:calc(100% - 32px)}.welcome{width:calc(100% - 32px)}.welcome h1{font-size:26px}}
</style>
