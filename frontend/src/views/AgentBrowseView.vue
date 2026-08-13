<script setup lang="ts">
import { onMounted, computed } from 'vue'
import { useRouter } from 'vue-router'
import { useAgentStore } from '../stores/agent'
import { useSessionStore } from '../stores/session'
import type { Agent } from '../types'

const router = useRouter()
const agentStore = useAgentStore()
const sessionStore = useSessionStore()

const agents = computed(() => agentStore.agents)

onMounted(() => {
  if (!agentStore.agents.length) agentStore.fetchAgents()
})

// F-08：开始对话创建新会话
async function startChat(agent: Agent) {
  const s = await sessionStore.createSession(agent.id, agent.name)
  router.push(`/chat/${s.id}`)
}

// 用户术语能力标签，不展示 version/HITL/Published
function caps(a: Agent): string[] {
  const caps: string[] = ['文本']
  if (a.supportedInputs?.includes('file')) caps.push('文件')
  if (a.hitlCapable) caps.push('需要确认')
  return caps
}
function updateTime(a: Agent): string {
  if (!a.updatedAt) return ''
  return new Date(a.updatedAt).toLocaleDateString('zh-CN')
}
</script>

<template>
  <div class="assistant-browse">
    <header class="page-head">
      <h1>选择智能体</h1>
      <p>这里用于浏览可用能力；开始使用后会进入新的对话。</p>
    </header>
    <div class="grid">
      <div v-if="agentStore.loading" class="card skeleton" style="height: 140px;"></div>
      <div v-else-if="agentStore.error" class="empty-state">
        <p>智能体列表暂时不可用</p>
        <p style="font-size: 12px">{{ agentStore.error }}</p>
      </div>
      <div v-else-if="!agents.length" class="empty-state">
        <p>暂无可用智能体</p>
      </div>
      <div v-for="a in agents" :key="a.id" class="card">
        <div class="card-head">
          <span class="icon">{{ a.icon || '✦' }}</span>
          <div>
            <b>{{ a.name }}</b>
            <small>更新于 {{ updateTime(a) }}</small>
          </div>
        </div>
        <p class="desc">{{ a.description }}</p>
        <div class="caps">
          <span v-for="c in caps(a)" :key="c" class="cap" :class="{ confirm: c === '需要确认' }">{{ c }}</span>
        </div>
        <button class="btn btn-primary start-btn" @click="startChat(a)">开始对话</button>
      </div>
    </div>
  </div>
</template>

<style scoped>
.assistant-browse { max-width: 960px; margin: 0 auto; padding: 32px; }
.page-head h1 { font-size: 22px; margin: 0 0 6px; }
.page-head p { color: var(--c-text-muted); margin: 0 0 22px; font-size: 14px; }
.grid { display: grid; grid-template-columns: repeat(auto-fill, minmax(280px, 1fr)); gap: 16px; }
.card { background: var(--c-surface); border: 1px solid var(--c-border); border-radius: var(--radius-lg); padding: 18px; display: flex; flex-direction: column; gap: 12px; }
.card-head { display: flex; align-items: center; gap: 12px; }
.icon { font-size: 28px; color: var(--c-accent); }
.card-head b { display: block; font-size: 15px; }
.card-head small { display: block; font-size: 11px; color: var(--c-text-muted); margin-top: 2px; }
.desc { margin: 0; font-size: 13px; line-height: 1.6; color: var(--c-text-secondary); flex: 1; }
.caps { display: flex; gap: 6px; }
.cap { font-size: 11px; padding: 2px 8px; background: var(--c-bg); border-radius: 3px; color: var(--c-text-muted); }
.cap.confirm { color: var(--c-warning); background: #fdf2df; }
.start-btn { align-self: flex-start; }
</style>
