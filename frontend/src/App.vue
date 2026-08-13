<script setup lang="ts">
import { RouterView } from 'vue-router'
import { computed, ref, watch } from 'vue'
import SessionSidebar from './components/SessionSidebar.vue'
import { useSessionStore } from './stores/session'
import { useAgentStore } from './stores/agent'
import { useAuthStore } from './stores/auth'
import { useRoute } from 'vue-router'

const sessionStore = useSessionStore()
const agentStore = useAgentStore()
const auth = useAuthStore()
const route = useRoute()
const sidebarOpen = ref(false)

const showShell = computed(() => route.name !== 'login')

watch(
  () => auth.isAuthenticated,
  (ok) => {
    if (ok) {
      agentStore.fetchAgents()
      sessionStore.fetchSessions()
    }
  },
  { immediate: true },
)
</script>

<template>
  <RouterView v-if="!showShell" />
  <div v-else class="app-shell">
    <aside class="sidebar" :class="{ open: sidebarOpen }">
      <SessionSidebar @navigate="sidebarOpen = false" />
    </aside>
    <main class="main">
      <RouterView />
    </main>
  </div>
</template>
