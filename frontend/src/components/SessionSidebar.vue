<script setup lang="ts">
import { computed } from 'vue'
import { useRouter } from 'vue-router'
import { useSessionStore } from '../stores/session'

const emit = defineEmits<{ (e: 'navigate'): void }>()
const router = useRouter()
const sessionStore = useSessionStore()

const sessions = computed(() => sessionStore.sessions)
const currentId = computed(() => sessionStore.currentSessionId)

function select(id: string) {
  sessionStore.select(id)
  router.push(`/chat/${id}`)
  emit('navigate')
}

async function newChat() {
  router.push('/chat')
  sessionStore.currentSessionId = null
  emit('navigate')
}

function formatTime(iso: string): string {
  if (!iso) return ''
  const d = new Date(iso)
  const now = new Date()
  const diff = (now.getTime() - d.getTime()) / 1000
  if (diff < 60) return '刚刚'
  if (diff < 3600) return Math.floor(diff / 60) + ' 分钟前'
  if (diff < 86400) return Math.floor(diff / 3600) + ' 小时前'
  return d.toLocaleDateString('zh-CN')
}
</script>

<template>
  <div class="nav">
    <div class="brand">
      <span class="brand-mark">W</span>
      <div>
        <strong>任务对话</strong>
        <small>把事情交给合适的助手</small>
      </div>
    </div>
    <div class="session-list">
      <div v-if="sessionStore.loading" class="empty-state" style="padding: 30px;">
        <p>加载会话…</p>
      </div>
      <div v-else-if="!sessions.length" class="empty-state" style="padding: 30px;">
        <p>暂无会话</p>
      </div>
      <button
        v-for="s in sessions" :key="s.id"
        class="session-item" :class="{ active: s.id === currentId }"
        @click="select(s.id)"
      >
        <b>{{ s.title || '未命名会话' }}</b>
        <small>{{ s.agentName || '' }} · {{ formatTime(s.updatedAt) }}</small>
      </button>
    </div>
    <div class="sidebar-footer">
      <button class="new-chat btn btn-primary" type="button" @click="newChat">+ 新建会话</button>
    </div>
  </div>
</template>

<style scoped>
.nav { display: flex; flex-direction: column; height: 100%; }
.brand { display: flex; align-items: center; gap: 12px; padding: 20px 18px 18px; }
.brand-mark { display: grid; place-items: center; width: 34px; height: 34px; color: #fff; background: var(--c-primary); font-weight: 800; border-radius: var(--radius-lg); }
.brand strong, .brand small { display: block; }
.brand small { color: var(--c-text-muted); font-size: 12px; margin-top: 2px; }
.sidebar-footer { flex: none; padding: 12px 14px 16px; background: var(--c-surface); }
.new-chat { width: 100%; justify-content: center; }
.session-list { flex: 1; overflow-y: auto; padding: 0 8px; }
.session-item { display: block; width: 100%; text-align: left; border: 0; background: transparent; padding: 10px 12px; border-radius: var(--radius); cursor: pointer; color: var(--c-text); margin-bottom: 2px; }
.session-item:hover { background: var(--c-bg); }
.session-item.active { background: var(--c-primary-soft); color: var(--c-primary); }
.session-item b { display: block; font-size: 13px; font-weight: 600; white-space: nowrap; overflow: hidden; text-overflow: ellipsis; }
.session-item small { display: block; font-size: 11px; color: var(--c-text-muted); margin-top: 3px; white-space: nowrap; overflow: hidden; text-overflow: ellipsis; }
</style>
