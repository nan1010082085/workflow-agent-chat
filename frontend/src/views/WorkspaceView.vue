<script setup lang="ts">
import { ref, watch, computed, onMounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useSessionStore } from '../stores/session'
import { useAgentStore } from '../stores/agent'
import { useChatStore } from '../stores/chat'
import AssistantPicker from '../components/AssistantPicker.vue'
import ConversationHeader from '../components/ConversationHeader.vue'
import WelcomePanel from '../components/WelcomePanel.vue'
import MessageList from '../components/MessageList.vue'
import Composer from '../components/Composer.vue'
import ProcessingDrawer from '../components/ProcessingDrawer.vue'
import type { Agent } from '../types'

const route = useRoute()
const router = useRouter()
const sessionStore = useSessionStore()
const agentStore = useAgentStore()
const chatStore = useChatStore()

const selectedAgentId = ref<string | null>(null)
const pendingContent = ref<string | null>(null)
const showPicker = ref(false)
const showDrawer = ref(false)
const switchHint = ref<string | null>(null) // 换助手提示

const currentAgent = computed<Agent | null>(() => {
  if (!selectedAgentId.value) return null
  return agentStore.getAgent(selectedAgentId.value) || null
})

const currentSession = computed(() => sessionStore.current)

// 加载会话
async function loadSession(id: string) {
  sessionStore.select(id)
  await chatStore.resumeFromSession(id)
  // 用 session 绑定的助手恢复选中
  if (currentSession.value?.agentId) {
    selectedAgentId.value = currentSession.value.agentId
  }
}

watch(() => route.params.sessionId, async (id) => {
  if (id && typeof id === 'string') {
    await loadSession(id)
  } else {
    chatStore.reset()
    selectedAgentId.value = null
  }
}, { immediate: true })

onMounted(() => {
  if (!agentStore.agents.length) agentStore.fetchAgents()
  if (!sessionStore.sessions.length) sessionStore.fetchSessions()
})

// F-02：换助手 = 新建对话
function onSwitchAssistant() {
  if (chatStore.messages.length > 0) {
    switchHint.value = '换一个助手会创建新对话，当前对话将保留。'
  }
  showPicker.value = true
}

async function onAgentSelect(a: Agent) {
  selectedAgentId.value = a.id
  showPicker.value = false
  switchHint.value = null
  // 已有内容或已绑定助手 → 创建新对话
  if (currentSession.value?.agentId && currentSession.value.agentId !== a.id) {
    await startNewChat(a)
  }
  if (pendingContent.value) {
    const content = pendingContent.value
    pendingContent.value = null
    await onSend(content)
  }
}

async function startNewChat(agent?: Agent) {
  const a = agent || currentAgent.value
  if (!a) return
  const s = await sessionStore.createSession(a.id, a.name)
  await router.push(`/chat/${s.id}`)
}

async function onSend(content: string) {
  if (!currentAgent.value) {
    pendingContent.value = content
    showPicker.value = true
    switchHint.value = '先选择一个助手，再发送这条任务。'
    return
  }
  let sessionId = route.params.sessionId as string
  if (!sessionId) {
    const s = await sessionStore.createSession(currentAgent.value.id, currentAgent.value.name)
    sessionId = s.id
    router.replace(`/chat/${sessionId}`)
  }
  await chatStore.sendMessage(sessionId, currentAgent.value.id, content)
}

async function onCancel() {
  if (chatStore.currentRun) await chatStore.cancelRun(chatStore.currentRun.runId)
}

async function onResume(action: string, payload?: string) {
  if (chatStore.currentRun) await chatStore.resumeRun(chatStore.currentRun.runId, action, payload)
}

async function onRetry() {
  // 重试：取最后一条用户消息重发
  const lastUser = [...chatStore.messages].reverse().find((m) => m.role === 'user')
  if (lastUser && route.params.sessionId) {
    await onSend(lastUser.content)
  }
}

function onUseGeneralChat() {
  chatStore.error = '通用对话需要平台模型服务支持，当前环境尚未接入。'
}
</script>

<template>
  <div class="workspace">
    <ConversationHeader
      :agent="currentAgent"
      :has-messages="chatStore.messages.length > 0"
      :processing="chatStore.sending || chatStore.currentRun?.status === 'RUNNING'"
      @change-assistant="onSwitchAssistant"
      @toggle-details="showDrawer = !showDrawer"
    />

    <div v-if="switchHint" class="hint-bar">{{ switchHint }}</div>

    <div class="conversation">
      <div v-if="showPicker && currentAgent" class="inline-picker">
        <div class="inline-picker-head">
          <strong>选择另一个助手</strong>
          <button class="btn" @click="showPicker = false">取消</button>
        </div>
        <AssistantPicker v-model="selectedAgentId" @select="onAgentSelect" />
      </div>
      <WelcomePanel
        v-if="!currentAgent && !chatStore.messages.length"
        :selected-agent-id="selectedAgentId"
        @select="onAgentSelect"
        @use-general-chat="onUseGeneralChat"
      />
      <MessageList v-else
        :messages="chatStore.messages"
        :loading="chatStore.loadingMessages"
        :current-run="chatStore.currentRun"
        @resume="onResume"
        @cancel="onCancel"
        @retry="onRetry"
      />
      <Composer
        :disabled="chatStore.sending"
        :agent-selected="!!currentAgent"
        @send="onSend"
      />
    </div>

    <ProcessingDrawer
      :open="showDrawer"
      :run="chatStore.currentRun"
      :agent="currentAgent"
      @close="showDrawer = false"
    />

    <p v-if="chatStore.error" class="global-error">⚠ {{ chatStore.error }}</p>
  </div>
</template>

<style scoped>
.workspace { display: flex; flex-direction: column; height: 100%; }
.hint-bar { padding: 8px 32px; background: var(--c-accent-soft); color: #8a5a1f; font-size: 13px; border-bottom: 1px solid var(--c-border-soft); }
.inline-picker { max-width: 720px; width: calc(100% - 48px); margin: 20px auto 0; padding: 14px; border: 1px solid var(--c-border); background: var(--c-surface); border-radius: var(--radius); }
.inline-picker-head { display: flex; align-items: center; justify-content: space-between; margin-bottom: 8px; font-size: 14px; }
.conversation { flex: 1; display: flex; flex-direction: column; min-height: 0; overflow: hidden; }
.global-error { position: fixed; bottom: 16px; left: 50%; transform: translateX(-50%); background: var(--c-danger); color: #fff; padding: 8px 16px; border-radius: var(--radius); font-size: 13px; z-index: 40; }
</style>
