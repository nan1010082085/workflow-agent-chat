<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useModelStore } from '../stores/model'
import { useAgentStore } from '../stores/agent'
import { useChatStore } from '../stores/chat'
import { titleFromContent, useSessionStore } from '../stores/session'
import type { Agent } from '../types'
import ModelPicker from '../components/ModelPicker.vue'
import AssistantPicker from '../components/AssistantPicker.vue'
import MessageList from '../components/MessageList.vue'
import Composer from '../components/Composer.vue'

const route = useRoute()
const router = useRouter()
const modelStore = useModelStore()
const agentStore = useAgentStore()
const chatStore = useChatStore()
const sessionStore = useSessionStore()

const showWorkflows = ref(false)
const mode = ref<'model' | 'agent'>('model')
const selectedAgent = ref<Agent | null>(null)
const selectedModel = computed({
  get: () => modelStore.selectedId,
  set: (v) => { modelStore.selectedId = v },
})
const hasMessages = computed(() => chatStore.messages.length > 0)
const loadingSession = ref(false)

onMounted(() => {
  if (!modelStore.models.length) modelStore.fetchModels()
  if (!agentStore.agents.length) agentStore.fetchAgents()
})

/**
 * 根据路由加载会话；无 sessionId 时回到空白对话。
 */
async function openSessionFromRoute(sessionId: string | undefined) {
  if (!sessionId) {
    // 进入空白对话页只清会话与消息；mode/选中智能体由调用方决定
    sessionStore.currentSessionId = null
    chatStore.reset()
    return
  }

  // 当前会话正在发送或已有本地消息时，不要用路由回载覆盖
  if (
    sessionStore.currentSessionId === sessionId
    && (chatStore.sending || chatStore.messages.length > 0)
  ) {
    return
  }

  loadingSession.value = true
  try {
    if (!sessionStore.sessions.length) await sessionStore.fetchSessions()
    sessionStore.select(sessionId)
    const summary = sessionStore.current
    await chatStore.resumeFromSession(sessionId)
    if (summary?.agentId) {
      mode.value = 'agent'
      selectedAgent.value = agentStore.agents.find((a) => a.id === summary.agentId) || {
        id: summary.agentId,
        slug: summary.agentId,
        name: summary.agentName || '智能体',
        description: '',
        icon: '✦',
        supportedInputs: ['text'],
        hitlCapable: false,
        version: '',
        updatedAt: '',
        published: true,
      }
    } else {
      mode.value = 'model'
      selectedAgent.value = null
    }
  } catch (e: any) {
    chatStore.error = e?.message || '加载会话失败'
  } finally {
    loadingSession.value = false
  }
}

watch(
  () => route.params.sessionId as string | undefined,
  (sessionId) => { void openSessionFromRoute(sessionId) },
  { immediate: true },
)

async function ensureSession(title?: string, agentId?: string, agentName?: string) {
  let sessionId = sessionStore.currentSessionId
  if (sessionId) return { sessionId, created: false }
  const session = await sessionStore.createSession(agentId, agentName, title)
  // 注意：不要在此处立刻 router.replace，否则会与首条发送竞态，把本地消息冲掉
  return { sessionId: session.id, created: true }
}

async function send(content: string) {
  showWorkflows.value = false
  if (mode.value === 'agent' && selectedAgent.value) {
    const { sessionId, created } = await ensureSession(
      titleFromContent(content),
      selectedAgent.value.id,
      selectedAgent.value.name,
    )
    const result = await chatStore.sendMessage(sessionId, selectedAgent.value.id, content)
    if (result?.sessionTitle) {
      sessionStore.bumpSession(sessionId, { title: result.sessionTitle })
    } else {
      sessionStore.bumpSession(sessionId)
    }
    if (created) await router.replace(`/chat/${sessionId}`)
    return
  }

  if (!modelStore.selectedId) {
    modelStore.error = '当前没有可用模型，请稍后重试'
    return
  }
  const model = modelStore.selected()
  const { sessionId, created } = await ensureSession(
    titleFromContent(content),
    undefined,
    model?.name || '基础模型',
  )
  try {
    const result = await chatStore.sendModelMessage(sessionId, modelStore.selectedId, content)
    if (result?.sessionTitle) {
      sessionStore.bumpSession(sessionId, { title: result.sessionTitle })
    } else {
      sessionStore.bumpSession(sessionId)
    }
  } catch {
    sessionStore.bumpSession(sessionId)
  }
  if (created) await router.replace(`/chat/${sessionId}`)
}

function selectAgent(agent: Agent) {
  showWorkflows.value = false
  sessionStore.currentSessionId = null
  chatStore.reset()
  const apply = () => {
    selectedAgent.value = agent
    mode.value = 'agent'
  }
  apply()
  // 从已有会话切出时，等路由回到 /chat 后再确认 mode，避免被清空逻辑覆盖
  if (route.params.sessionId) {
    void router.replace('/chat').then(apply)
  }
}

function useBaseModel() {
  mode.value = 'model'
  selectedAgent.value = null
  sessionStore.currentSessionId = null
  chatStore.reset()
  if (route.params.sessionId) void router.replace('/chat')
}
</script>
<template>
  <div class="workspace">
    <header class="topbar">
      <div class="topbar-copy">
        <strong>对话</strong>
        <span class="subtle">{{ mode === 'agent' ? `当前使用：${selectedAgent?.name}` : '当前使用：基础模型' }}</span>
      </div>
      <p v-if="modelStore.error || chatStore.error || sessionStore.error" class="status-error">
        {{ modelStore.error || chatStore.error || sessionStore.error }}
      </p>
    </header>
    <main class="conversation" :class="{ empty: !hasMessages }">
      <div v-if="loadingSession" class="empty-content"><p class="subtle">正在加载会话…</p></div>
      <div v-else-if="!hasMessages" class="empty-content">
        <section class="welcome">
          <div class="welcome-mark">✦</div><h1>你想聊点什么？</h1>
          <p>{{ mode === 'agent' ? `向「${selectedAgent?.name}」描述你要完成的任务。` : '直接输入问题，开始对话。' }}</p>
        </section>
        <Composer :disabled="chatStore.sending" :panel-open="showWorkflows" :placeholder="mode === 'agent' ? `使用 ${selectedAgent?.name} 处理任务…` : (modelStore.selected() ? `使用 ${modelStore.selected()!.name} 对话…` : '输入消息…')" @send="send" @close-panel="showWorkflows = false">
          <template #tools>
            <ModelPicker v-if="mode === 'model'" v-model="selectedModel" :models="modelStore.models" :loading="modelStore.loading" />
            <button v-if="mode === 'agent'" class="mode-action" type="button" @click="useBaseModel">取消选择</button>
            <el-tooltip content="智能体" placement="top" :show-after="200">
              <button class="workflow-toggle" type="button" aria-label="智能体" :aria-expanded="showWorkflows" @click="showWorkflows = !showWorkflows">
                <svg viewBox="0 0 16 16" width="14" height="14" aria-hidden="true"><path d="M8 2.5v11M2.5 8h11" fill="none" stroke="currentColor" stroke-width="1.8" stroke-linecap="round"/></svg>
              </button>
            </el-tooltip>
          </template>
          <template #panel>
            <div class="panel-title"><strong>已发布的智能体</strong><button type="button" @click="showWorkflows = false">关闭</button></div>
            <AssistantPicker :model-value="selectedAgent?.id || null" @select="selectAgent" />
            <p v-if="agentStore.error" class="subtle">智能体暂时不可用，请稍后重试。</p>
          </template>
        </Composer>
      </div>
      <MessageList v-else :messages="chatStore.messages" :loading="false" :current-run="mode === 'agent' ? chatStore.currentRun : null" @resume="(action, payload) => chatStore.currentRun && chatStore.resumeRun(chatStore.currentRun.runId, action, payload)" @cancel="() => chatStore.currentRun && chatStore.cancelRun(chatStore.currentRun.runId)" />
      <Composer v-if="hasMessages" :disabled="chatStore.sending" :panel-open="showWorkflows" :placeholder="mode === 'agent' ? `使用 ${selectedAgent?.name} 处理任务…` : (modelStore.selected() ? `使用 ${modelStore.selected()!.name} 对话…` : '输入消息…')" @send="send" @close-panel="showWorkflows = false">
        <template #tools>
          <ModelPicker v-if="mode === 'model'" v-model="selectedModel" :models="modelStore.models" :loading="modelStore.loading" />
          <button v-if="mode === 'agent'" class="mode-action" type="button" @click="useBaseModel">取消选择</button>
          <el-tooltip content="智能体" placement="top" :show-after="200">
            <button class="workflow-toggle" type="button" aria-label="智能体" :aria-expanded="showWorkflows" @click="showWorkflows = !showWorkflows">
              <svg viewBox="0 0 16 16" width="14" height="14" aria-hidden="true"><path d="M8 2.5v11M2.5 8h11" fill="none" stroke="currentColor" stroke-width="1.8" stroke-linecap="round"/></svg>
            </button>
          </el-tooltip>
        </template>
        <template #panel>
          <div class="panel-title"><strong>已发布的智能体</strong><button type="button" @click="showWorkflows = false">关闭</button></div>
          <AssistantPicker :model-value="selectedAgent?.id || null" @select="selectAgent" />
          <p v-if="agentStore.error" class="subtle">智能体暂时不可用，请稍后重试。</p>
        </template>
      </Composer>
    </main>
  </div>
</template>
<style scoped>
.workspace { display:flex; flex-direction:column; height:100%; min-height:0; background:#eef3f4 url('/workflow-agent-chat/chat-canvas.svg') center / cover no-repeat; }
.topbar { min-height:68px; display:flex; justify-content:space-between; align-items:center; gap:16px; padding:12px 32px; background:var(--c-surface); }.topbar-copy{display:flex;align-items:flex-start;flex-direction:column;gap:0;min-width:0}.status-error{margin:0 0 0 auto;color:var(--c-danger);font-size:12px;white-space:nowrap;overflow:hidden;text-overflow:ellipsis;max-width:420px}
.topbar strong { font-size:16px; }.subtle { display:block; margin-top:4px; color:var(--c-text-muted); font-size:12px; }.workflow-toggle,.panel-title button,.mode-action { border:0; background:transparent; color:var(--c-primary); cursor:pointer; font-size:13px; }
.workflow-toggle {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  flex: none;
  width: 30px;
  height: 30px;
  padding: 0;
  border: 1px solid var(--c-border);
  border-radius: 50%;
  background: var(--c-surface);
  line-height: 0;
}
.workflow-toggle svg { display: block; }
.workflow-toggle:hover,
.workflow-toggle[aria-expanded='true'] { border-color: var(--c-primary); background: var(--c-primary-soft); }
.mode-action { padding: 7px 10px; border: 1px solid var(--c-border); border-radius: 6px; background: var(--c-surface); }
.panel-title { display: flex; flex: none; justify-content: space-between; align-items: center; margin-bottom: 12px; }
.conversation { flex:1; display:flex; flex-direction:column; min-height:0; overflow:hidden; background: transparent; }
.empty-content { flex:1; min-height:0; display:flex; flex-direction:column; align-items:center; justify-content:center; width:100%; padding:24px 0; }
.welcome { width:min(860px,calc(100% - 48px)); padding:0 28px 20px; text-align:center; }
.welcome-mark { color:var(--c-accent); font-size:28px; }
.welcome h1 { margin:12px 0 8px; font-size:30px; }
.welcome p { margin:0; color:var(--c-text-secondary); }
.empty-content :deep(.composer) { margin:0; }
@media(max-width:767px){.topbar{padding:12px 16px}.subtle{display:none}.topbar-controls{margin-left:auto}.status-error{position:absolute;top:70px;left:16px;right:16px;max-width:none;padding:8px 10px;background:var(--c-danger-soft);border:1px solid #f0c4be;border-radius:6px;white-space:normal}.workflow-panel{width:calc(100% - 32px)}.welcome{width:calc(100% - 32px)}.welcome h1{font-size:26px}}
</style>
