<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useSessionStore } from '../stores/session'
import { useChatStore } from '../stores/chat'
import UserMenu from './UserMenu.vue'

const emit = defineEmits<{ (e: 'navigate'): void }>()
const route = useRoute()
const router = useRouter()
const sessionStore = useSessionStore()
const chatStore = useChatStore()

const sessions = computed(() => sessionStore.sessions)
const currentId = computed(() => sessionStore.currentSessionId)
const folders = ref<string[]>(JSON.parse(localStorage.getItem('chat-folders') || '[]'))
const sessionFolders = ref<Record<string, string>>(JSON.parse(localStorage.getItem('chat-session-folders') || '{}'))
const collapsed = ref<Record<string, boolean>>(JSON.parse(localStorage.getItem('chat-collapsed-folders') || '{}'))
const showFolderInput = ref(false)
const folderName = ref('')

/** 有目录时才允许移动会话 */
const hasFolders = computed(() => folders.value.length > 0)

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
/**
 * 移动会话到目录；空字符串表示移出目录。
 */
function moveSession(id: string, folder: string) {
  if (folder) sessionFolders.value[id] = folder
  else delete sessionFolders.value[id]
}
function toggleFolder(folder: string) { collapsed.value[folder] = !collapsed.value[folder] }

async function select(id: string) {
  const sameRoute = route.params.sessionId === id
  sessionStore.select(id)
  if (sameRoute) {
    // 同路由再次点击时 vue-router 不会触发导航，需手动恢复消息
    await chatStore.resumeFromSession(id)
  } else {
    await router.push(`/chat/${id}`)
  }
  emit('navigate')
}

async function newChat() {
  sessionStore.currentSessionId = null
  chatStore.reset()
  if (route.params.sessionId) await router.push('/chat')
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
        <strong>澄语</strong>
        <small>和助手聊聊，把事情办完</small>
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
        <div
          v-for="s in unfiledSessions"
          :key="s.id"
          class="session-item"
          :class="{ active: s.id === currentId }"
          role="button"
          tabindex="0"
          @click="select(s.id)"
          @keydown.enter.prevent="select(s.id)"
        >
          <div class="session-main">
            <b>{{ s.title || '未命名会话' }}</b>
            <small>{{ s.agentName || '基础模型' }} · {{ formatTime(s.updatedAt) }}</small>
          </div>
          <el-dropdown
            v-if="hasFolders"
            trigger="click"
            @command="(folder: string) => moveSession(s.id, folder)"
          >
            <button class="move-btn" type="button" aria-label="移动会话" @click.stop>
              移动
            </button>
            <template #dropdown>
              <el-dropdown-menu>
                <el-dropdown-item
                  v-for="folder in folders"
                  :key="folder"
                  :command="folder"
                >
                  {{ folder }}
                </el-dropdown-item>
              </el-dropdown-menu>
            </template>
          </el-dropdown>
        </div>
      </template>
      <section v-for="folder in folders" :key="folder" class="folder-section">
        <button class="folder-header" type="button" @click="toggleFolder(folder)">
          <span>{{ collapsed[folder] ? '▸' : '▾' }} {{ folder }}</span>
          <small>{{ folderSessions(folder).length }}</small>
        </button>
        <template v-if="!collapsed[folder]">
          <div
            v-for="s in folderSessions(folder)"
            :key="s.id"
            class="session-item"
            :class="{ active: s.id === currentId }"
            role="button"
            tabindex="0"
            @click="select(s.id)"
            @keydown.enter.prevent="select(s.id)"
          >
            <div class="session-main">
              <b>{{ s.title || '未命名会话' }}</b>
              <small>{{ s.agentName || '基础模型' }} · {{ formatTime(s.updatedAt) }}</small>
            </div>
            <el-dropdown
              trigger="click"
              @command="(target: string) => moveSession(s.id, target)"
            >
              <button class="move-btn" type="button" aria-label="移动会话" @click.stop>
                移动
              </button>
              <template #dropdown>
                <el-dropdown-menu>
                  <el-dropdown-item
                    v-for="target in folders.filter((f) => f !== folder)"
                    :key="target"
                    :command="target"
                  >
                    {{ target }}
                  </el-dropdown-item>
                  <el-dropdown-item divided command="">移出目录</el-dropdown-item>
                </el-dropdown-menu>
              </template>
            </el-dropdown>
          </div>
        </template>
      </section>
    </div>
    <div class="sidebar-footer">
      <div v-if="showFolderInput" class="folder-create">
        <input v-model="folderName" placeholder="目录名称" @keyup.enter="addFolder" />
        <button type="button" @click="addFolder">创建</button>
      </div>
      <button class="folder-action" type="button" @click="showFolderInput = !showFolderInput">+ 新建目录</button>
      <button class="new-chat btn btn-primary" type="button" @click="newChat">+ 新建会话</button>
    </div>
    <UserMenu />
  </div>
</template>

<style scoped>
.nav { display: flex; flex-direction: column; height: 100%; }
.brand { display: flex; align-items: center; gap: 12px; padding: 20px 18px 18px; }
.brand-mark { display: grid; place-items: center; width: 34px; height: 34px; color: #fff; background: var(--c-primary); font-weight: 800; border-radius: var(--radius-lg); }
.brand strong, .brand small { display: block; }
.brand small { color: var(--c-text-muted); font-size: 12px; margin-top: 2px; }
.sidebar-footer { flex: none; padding: 12px 14px 8px; background: var(--c-surface); }
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
.session-item {
  display: flex;
  align-items: flex-start;
  gap: 8px;
  width: 100%;
  text-align: left;
  border: 0;
  background: transparent;
  padding: 10px 12px;
  border-radius: var(--radius);
  cursor: pointer;
  color: var(--c-text);
  margin-bottom: 2px;
}
.session-item:hover { background: var(--c-bg); }
.session-item.active { background: var(--c-primary-soft); color: var(--c-primary); }
.session-main { min-width: 0; flex: 1; }
.session-item b { display: block; font-size: 13px; font-weight: 600; white-space: nowrap; overflow: hidden; text-overflow: ellipsis; }
.session-item small { display: block; font-size: 11px; color: var(--c-text-muted); margin-top: 3px; white-space: nowrap; overflow: hidden; text-overflow: ellipsis; }
.move-btn {
  flex: none;
  margin-top: 1px;
  padding: 2px 8px;
  border: 1px solid var(--c-border);
  border-radius: 4px;
  background: var(--c-surface);
  color: var(--c-text-muted);
  font-size: 11px;
  cursor: pointer;
  opacity: 0;
}
.session-item:hover .move-btn,
.session-item:focus-within .move-btn { opacity: 1; }
.move-btn:hover { color: var(--c-primary); border-color: var(--c-primary); }
</style>
