<script setup lang="ts">
import { computed, ref } from 'vue'
import { useRouter } from 'vue-router'
import { useAuthStore } from '../stores/auth'
import { useSessionStore } from '../stores/session'
import { useChatStore } from '../stores/chat'

const auth = useAuthStore()
const sessionStore = useSessionStore()
const chatStore = useChatStore()
const router = useRouter()
const open = ref(false)

const label = computed(() =>
  auth.user?.displayName || auth.user?.username || '用户',
)
const initial = computed(() => label.value.slice(0, 1).toUpperCase())

/**
 * 退出并清空本机会话列表缓存。
 */
async function logout() {
  open.value = false
  await auth.logout()
  sessionStore.sessions = []
  sessionStore.currentSessionId = null
  chatStore.reset()
  await router.replace({ name: 'login' })
}
</script>

<template>
  <div class="user-menu" v-if="auth.isAuthenticated">
    <button
      class="user-trigger"
      type="button"
      :aria-expanded="open"
      :aria-label="`当前用户 ${label}`"
      @click="open = !open"
    >
      <span class="avatar">{{ initial }}</span>
      <span class="name">
        <strong>{{ label }}</strong>
        <small v-if="auth.user?.username && auth.user.username !== label">
          @{{ auth.user.username }}
        </small>
      </span>
    </button>
    <div v-if="open" class="menu" role="menu">
      <button type="button" role="menuitem" @click="logout">退出登录</button>
    </div>
    <div v-if="open" class="mask" @click="open = false" />
  </div>
</template>

<style scoped>
.user-menu {
  position: relative;
  flex: none;
  padding: 10px 12px 14px;
  border-top: 1px solid var(--c-border-soft);
  background: var(--c-surface);
}
.user-trigger {
  display: flex;
  align-items: center;
  gap: 10px;
  width: 100%;
  padding: 8px 10px;
  border: 1px solid transparent;
  border-radius: var(--radius);
  background: transparent;
  cursor: pointer;
  text-align: left;
  color: var(--c-text);
}
.user-trigger:hover {
  background: var(--c-bg);
  border-color: var(--c-border-soft);
}
.avatar {
  flex: none;
  width: 32px;
  height: 32px;
  border-radius: 999px;
  display: grid;
  place-items: center;
  background: var(--c-primary);
  color: #fff;
  font-size: 13px;
  font-weight: 700;
}
.name {
  min-width: 0;
  display: flex;
  flex-direction: column;
  gap: 1px;
}
.name strong {
  font-size: 13px;
  font-weight: 600;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}
.name small {
  font-size: 11px;
  color: var(--c-text-muted);
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}
.menu {
  position: absolute;
  left: 12px;
  right: 12px;
  bottom: calc(100% - 4px);
  background: var(--c-surface);
  border: 1px solid var(--c-border);
  border-radius: var(--radius);
  box-shadow: var(--shadow);
  padding: 6px;
  z-index: 20;
}
.menu button {
  width: 100%;
  text-align: left;
  border: 0;
  background: transparent;
  padding: 8px 10px;
  border-radius: var(--radius-sm);
  cursor: pointer;
  font-size: 13px;
  color: var(--c-text);
}
.menu button:hover {
  background: var(--c-bg);
  color: var(--c-danger);
}
.mask {
  position: fixed;
  inset: 0;
  z-index: 10;
}
</style>
