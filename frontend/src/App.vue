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

/**
 * 仅在鉴权完成且已登录、且非登录页时展示 Chat 壳。
 * 避免 /login 刷新时先闪出侧栏/对话入口。
 */
const showShell = computed(() => {
  if (!auth.bootstrapped) return false
  if (route.name === 'login' || route.meta.public) return false
  return auth.isAuthenticated
})

const showBoot = computed(() => !auth.bootstrapped)

watch(
  () => [auth.bootstrapped, auth.isAuthenticated, route.name] as const,
  ([ready, ok, name]) => {
    if (!ready || !ok || name === 'login') return
    agentStore.fetchAgents()
    sessionStore.fetchSessions()
  },
  { immediate: true },
)
</script>

<template>
  <div v-if="showBoot" class="boot-screen" aria-busy="true" aria-label="正在加载">
    <div class="boot-mark" />
  </div>
  <RouterView v-else-if="!showShell" />
  <div v-else class="app-shell">
    <aside class="sidebar" :class="{ open: sidebarOpen }">
      <SessionSidebar @navigate="sidebarOpen = false" />
    </aside>
    <main class="main">
      <RouterView />
    </main>
  </div>
</template>

<style scoped>
.boot-screen {
  min-height: 100vh;
  display: grid;
  place-items: center;
  background: #e8f1f0;
}
.boot-mark {
  width: 28px;
  height: 28px;
  border-radius: 8px;
  background: linear-gradient(135deg, #0d6b67, #2f9e74);
  animation: boot-pulse 1.1s ease-in-out infinite;
}
@keyframes boot-pulse {
  0%, 100% { opacity: 0.45; transform: scale(0.92); }
  50% { opacity: 1; transform: scale(1); }
}
@media (prefers-reduced-motion: reduce) {
  .boot-mark { animation: none; opacity: 0.8; }
}
</style>
