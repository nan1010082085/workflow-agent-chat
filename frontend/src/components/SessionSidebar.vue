<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { useRouter } from 'vue-router'
import { useSessionStore } from '../stores/session'

const emit = defineEmits<{ (e: 'navigate'): void }>()
const router = useRouter()
const sessionStore = useSessionStore()

const sessions = computed(() => sessionStore.sessions)
const currentId = computed(() => sessionStore.currentSessionId)
const folders = ref<string[]>(JSON.parse(localStorage.getItem('chat-folders') || '[]'))
const sessionFolders = ref<Record<string, string>>(JSON.parse(localStorage.getItem('chat-session-folders') || '{}'))
const collapsed = ref<Record<string, boolean>>(JSON.parse(localStorage.getItem('chat-collapsed-folders') || '{}'))
const showFolderInput = ref(false)
const folderName = ref('')

watch(folders, (value) => localStorage.setItem('chat-folders', JSON.stringify(value)), { deep: true })
watch(sessionFolders, (value) => localStorage.setItem('chat-session-folders', JSON.stringify(value)), { deep: true })
watch(collapsed, (value) => localStorage.setItem('chat-collapsed-folders', JSON.stringify(value)), { deep: true })

const unfiledSessions = computed(() => sessions.value.filter((s) => !sessionFolders.value[s.id]))
function folderSessions(folder: string) { return sessions.value.filter((s) => sessionFolders.value[s.id] === folder) }
function addFolder() {
  const name = folderName.value.trim()
  if (name && !folders.value.includes(name)) folders.value.push(name)
  folderName.value = ''
  showFolderInput.value = false
}
function moveSession(id: string, folder: string) {
  if (folder) sessionFolders.value[id] = folder
  else delete sessionFolders.value[id]
}
function toggleFolder(folder: string) { collapsed.value[folder] = !collapsed.value[folder] }

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
        <small>把事情交给合适的智能体</small>
      </div>
    </div>
    <div class="session-list">
      <div v-if="sessionStore.loading" class="empty-state" style="padding: 30px;">
        <p>加载会话…</p>
      </div>
      <div v-else-if="!sessions.length" class="empty-state" style="padding: 30px;">
        <p>暂无会话</p>
      </div>
      <template v-if="unfiledSessions.length">
        <div class="section-label">最近会话</div>
        <button v-for="s in unfiledSessions" :key="s.id" class="session-item" :class="{ active: s.id === currentId }" @click="select(s.id)">
          <b>{{ s.title || '未命名会话' }}</b><small>{{ s.agentName || '基础模型' }} · {{ formatTime(s.updatedAt) }}</small>
          <select class="move-select" aria-label="移动会话" @click.stop @change="moveSession(s.id, ($event.target as HTMLSelectElement).value)">
            <option value="">移动到…</option><option v-for="folder in folders" :key="folder" :value="folder">{{ folder }}</option>
          </select>
        </button>
      </template>
      <section v-for="folder in folders" :key="folder" class="folder-section">
        <button class="folder-header" type="button" @click="toggleFolder(folder)"><span>{{ collapsed[folder] ? '▸' : '▾' }} {{ folder }}</span><small>{{ folderSessions(folder).length }}</small></button>
        <template v-if="!collapsed[folder]">
          <button v-for="s in folderSessions(folder)" :key="s.id" class="session-item" :class="{ active: s.id === currentId }" @click="select(s.id)">
            <b>{{ s.title || '未命名会话' }}</b><small>{{ s.agentName || '基础模型' }} · {{ formatTime(s.updatedAt) }}</small>
            <select class="move-select" aria-label="移动会话" @click.stop @change="moveSession(s.id, ($event.target as HTMLSelectElement).value)">
              <option value="">移出目录</option><option v-for="target in folders" :key="target" :value="target">{{ target }}</option>
            </select>
          </button>
        </template>
      </section>
    </div>
    <div class="sidebar-footer">
      <div v-if="showFolderInput" class="folder-create"><input v-model="folderName" placeholder="目录名称" @keyup.enter="addFolder" /><button type="button" @click="addFolder">创建</button></div>
      <button class="folder-action" type="button" @click="showFolderInput = !showFolderInput">+ 新建目录</button>
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
.folder-action { width: 100%; padding: 6px; margin-bottom: 6px; border: 0; background: transparent; color: var(--c-primary); cursor: pointer; font-size: 12px; text-align: left; }
.folder-create { display: flex; gap: 5px; margin-bottom: 8px; }
.folder-create input { min-width: 0; flex: 1; padding: 7px 8px; border: 1px solid var(--c-border); border-radius: var(--radius); }
.folder-create button { border: 1px solid var(--c-border); border-radius: var(--radius); background: var(--c-surface); color: var(--c-primary); cursor: pointer; }
.new-chat { width: 100%; justify-content: center; }
.session-list { flex: 1; overflow-y: auto; padding: 0 8px; }
.section-label { padding: 10px 12px 6px; color: var(--c-text-muted); font-size: 11px; font-weight: 700; text-transform: uppercase; }
.folder-section { margin-top: 8px; }
.folder-header { display: flex; justify-content: space-between; width: 100%; padding: 7px 12px; border: 0; background: transparent; color: var(--c-text-secondary); cursor: pointer; font-size: 12px; text-align: left; }
.folder-header small { color: var(--c-text-muted); }
.session-item { display: block; width: 100%; text-align: left; border: 0; background: transparent; padding: 10px 12px; border-radius: var(--radius); cursor: pointer; color: var(--c-text); margin-bottom: 2px; }
.session-item:hover { background: var(--c-bg); }
.session-item.active { background: var(--c-primary-soft); color: var(--c-primary); }
.session-item b { display: block; font-size: 13px; font-weight: 600; white-space: nowrap; overflow: hidden; text-overflow: ellipsis; }
.session-item small { display: block; font-size: 11px; color: var(--c-text-muted); margin-top: 3px; white-space: nowrap; overflow: hidden; text-overflow: ellipsis; }
.move-select { display: block; width: 100%; margin-top: 7px; padding: 3px 5px; border: 1px solid var(--c-border-soft); border-radius: 4px; background: transparent; color: var(--c-text-muted); font-size: 10px; opacity: 0; }
.session-item:hover .move-select, .session-item:focus-within .move-select { opacity: 1; }
</style>
