<script setup lang="ts">
import { RouterView } from 'vue-router'
import { ref } from 'vue'
import SessionSidebar from './components/SessionSidebar.vue'
import { useSessionStore } from './stores/session'
import { useAgentStore } from './stores/agent'

const sessionStore = useSessionStore()
const agentStore = useAgentStore()
const sidebarOpen = ref(false)

// 启动时加载 agent 和 session
agentStore.fetchAgents()
sessionStore.fetchSessions()
</script>

<template>
  <div class="app-shell">
    <aside class="sidebar" :class="{ open: sidebarOpen }">
      <SessionSidebar @navigate="sidebarOpen = false" />
    </aside>
    <main class="main">
      <RouterView />
    </main>
  </div>
</template>
