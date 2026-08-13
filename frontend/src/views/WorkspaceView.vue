<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useModelStore } from '../stores/model'
import { useAgentStore } from '../stores/agent'
import { useChatStore } from '../stores/chat'
import ModelPicker from '../components/ModelPicker.vue'
import AssistantPicker from '../components/AssistantPicker.vue'
import MessageList from '../components/MessageList.vue'
import Composer from '../components/Composer.vue'

const modelStore = useModelStore(); const agentStore = useAgentStore(); const chatStore = useChatStore()
const showWorkflows = ref(false)
const selectedModel = computed({ get: () => modelStore.selectedId, set: (v) => { modelStore.selectedId = v } })
const hasMessages = computed(() => chatStore.modelMessages.length > 0)

onMounted(() => { if (!modelStore.models.length) modelStore.fetchModels(); if (!agentStore.agents.length) agentStore.fetchAgents() })
async function send(content: string) {
  if (modelStore.selectedId) await chatStore.sendModelMessage(modelStore.selectedId, content)
  else chatStore.error = '当前没有可用模型，请稍后重试'
}
</script>
<template>
  <div class="workspace">
    <header class="topbar">
      <div class="topbar-copy"><strong>对话</strong><span class="subtle">直接使用平台模型，或选择一个已发布的专用能力</span></div>
      <div class="topbar-controls">
        <p v-if="modelStore.error || chatStore.error" class="status-error">{{ modelStore.error || chatStore.error }}</p>
        <ModelPicker v-model="selectedModel" :models="modelStore.models" :loading="modelStore.loading" />
      </div>
      <button class="workflow-toggle" type="button" @click="showWorkflows = !showWorkflows">{{ showWorkflows ? '收起专用能力' : '选择专用能力' }}</button>
    </header>
    <aside v-if="showWorkflows" class="workflow-panel">
      <div class="panel-title"><strong>已发布的专用能力</strong><button type="button" @click="showWorkflows = false">关闭</button></div>
      <AssistantPicker :model-value="null" @select="(a) => { chatStore.error = `专用能力「${a.name}」将在独立对话中启用`; showWorkflows = false }" />
      <p v-if="agentStore.error" class="subtle">专用能力暂时不可用，请稍后重试。</p>
    </aside>
    <main class="conversation" :class="{ empty: !hasMessages }">
      <section v-if="!hasMessages" class="welcome">
        <div class="welcome-mark">✦</div><h1>你想聊点什么？</h1>
        <p>直接输入问题，使用 Schema Platform 的模型开始对话。</p>
      </section>
      <MessageList v-else :messages="chatStore.modelMessages" :loading="false" :current-run="null" />
      <Composer :disabled="chatStore.sending" :placeholder="modelStore.selected() ? `使用 ${modelStore.selected()!.name} 对话…` : '输入消息…'" @send="send" />
    </main>
  </div>
</template>
<style scoped>
.workspace { display:flex; flex-direction:column; height:100%; min-height:0; background:var(--c-bg); }
.topbar { min-height:68px; display:flex; justify-content:space-between; align-items:center; gap:16px; padding:12px 32px; border-bottom:1px solid var(--c-border); background:var(--c-surface); }.topbar-copy,.topbar-controls{display:flex;align-items:center;gap:12px;min-width:0}.topbar-copy{flex-direction:column;align-items:flex-start;gap:0}.topbar-controls{margin-left:auto}.status-error{margin:0;color:var(--c-danger);font-size:12px;white-space:nowrap;overflow:hidden;text-overflow:ellipsis;max-width:320px}
.topbar strong { font-size:16px; }.subtle { display:block; margin-top:4px; color:var(--c-text-muted); font-size:12px; }.workflow-toggle,.panel-title button { border:0; background:transparent; color:var(--c-primary); cursor:pointer; font-size:13px; }
.workflow-panel { width:min(760px,calc(100% - 48px)); margin:18px auto 0; padding:16px; border:1px solid var(--c-border); border-radius:6px; background:var(--c-surface); }.panel-title { display:flex; justify-content:space-between; margin-bottom:12px; }
.conversation { flex:1; display:flex; flex-direction:column; min-height:0; overflow:hidden; }.welcome { margin:auto auto 24px; width:min(720px,calc(100% - 48px)); text-align:center; }.welcome-mark { color:var(--c-accent); font-size:28px; }.welcome h1 { margin:12px 0 8px; font-size:30px; }.welcome p { margin:0 0 20px; color:var(--c-text-secondary); }
@media(max-width:767px){.topbar{padding:12px 16px}.subtle{display:none}.topbar-controls{margin-left:auto}.status-error{position:absolute;top:70px;left:16px;right:16px;max-width:none;padding:8px 10px;background:var(--c-danger-soft);border:1px solid #f0c4be;border-radius:6px;white-space:normal}.workflow-panel{width:calc(100% - 32px)}.welcome{width:calc(100% - 32px)}.welcome h1{font-size:26px}}
</style>
