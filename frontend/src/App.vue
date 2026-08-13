<script setup lang="ts">
import { ref } from 'vue'

const agents = ref([
  { id: 'expense-audit', name: '报销审核', description: '审核报销材料并识别风险' },
  { id: 'document-summary', name: '文档摘要', description: '提取长文档中的重点结论' },
])
const selectedAgent = ref(agents.value[0])
const input = ref('')
const messages = ref<{ role: 'user' | 'assistant'; content: string }[]>([])

function send() {
  const content = input.value.trim()
  if (!content) return
  messages.value.push({ role: 'user', content })
  messages.value.push({ role: 'assistant', content: `已提交给「${selectedAgent.value.name}」Agent。Runtime 接入完成后将在这里显示执行结果。` })
  input.value = ''
}
</script>

<template>
  <main class="shell">
    <aside class="agents">
      <div class="brand"><span class="brand-mark">W</span><div><strong>Workflow Chat</strong><small>Agent workspace</small></div></div>
      <button v-for="agent in agents" :key="agent.id" class="agent" :class="{ active: selectedAgent.id === agent.id }" @click="selectedAgent = agent">
        <span class="agent-icon">✦</span><span><b>{{ agent.name }}</b><small>{{ agent.description }}</small></span>
      </button>
    </aside>
    <section class="conversation">
      <header><div><span class="eyebrow">ACTIVE AGENT</span><h1>{{ selectedAgent.name }}</h1></div><span class="status"><i /> Published</span></header>
      <div class="messages">
        <div v-if="!messages.length" class="empty"><span class="empty-mark">✦</span><h2>和 Workflow Agent 对话</h2><p>{{ selectedAgent.description }}。选择左侧 Agent，开始一个任务。</p></div>
        <div v-for="(message, index) in messages" :key="index" class="message" :class="message.role"><span class="avatar">{{ message.role === 'user' ? '你' : 'W' }}</span><p>{{ message.content }}</p></div>
      </div>
      <form class="composer" @submit.prevent="send"><textarea v-model="input" placeholder="描述你想完成的任务..." @keydown.enter.exact.prevent="send" /><button title="发送" type="submit">↗</button><small>Enter 发送 · Workflow 执行状态将实时显示</small></form>
    </section>
  </main>
</template>
