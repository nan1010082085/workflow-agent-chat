<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { useSessionStore } from '../stores/session'
import { api } from '../api/client'
import type { SessionSummary } from '../types'
import AppMark from '../components/AppMark.vue'

const router = useRouter()
const sessionStore = useSessionStore()

const loading = ref(false)
const sessions = ref<any[]>([])
const searchQuery = ref('')
const sortBy = ref<'time' | 'title'>('time')

// 从 localStorage 恢复目录配置
const folders = ref<string[]>(JSON.parse(localStorage.getItem('chat-folders') || '[]'))
const sessionFolders = ref<Record<string, string>>(JSON.parse(localStorage.getItem('chat-session-folders') || '{}'))

// 按目录分组的会话
const groupedSessions = computed(() => {
  const groups: Record<string, SessionSummary[]> = {}
  
  // 未分组的会话
  groups['未分类'] = []
  
  // 初始化目录
  folders.value.forEach(f => { groups[f] = [] })
  
  // 分组
  filteredSessions.value.forEach(s => {
    const folder = sessionFolders.value[s.id] || '未分类'
    if (!groups[folder]) groups[folder] = []
    groups[folder].push(s)
  })
  
  // 移除空分组
  Object.keys(groups).forEach(key => {
    if (groups[key].length === 0) delete groups[key]
  })
  
  return groups
})

// 过滤和排序后的会话
const filteredSessions = computed(() => {
  let sessionsList = [...sessions.value]
  
  // 搜索过滤
  if (searchQuery.value) {
    const query = searchQuery.value.toLowerCase()
    sessionsList = sessionsList.filter(s => 
      (s.title || '').toLowerCase().includes(query) ||
      (s.agentName || '').toLowerCase().includes(query)
    )
  }
  
  // 排序
  if (sortBy.value === 'time') {
    sessionsList.sort((a, b) => new Date(b.updatedAt).getTime() - new Date(a.updatedAt).getTime())
  } else {
    sessionsList.sort((a, b) => (a.title || '').localeCompare(b.title || ''))
  }
  
  return sessionsList
})

// 展开状态
const expandedFolders = ref<Record<string, boolean>>({})
folders.value.forEach(f => { expandedFolders.value[f] = true })

function toggleFolder(folder: string) {
  expandedFolders.value[folder] = !expandedFolders.value[folder]
}

async function navigateToSession(sessionId: string) {
  await router.push(`/chat/${sessionId}`)
}

function formatTime(iso: string): string {
  if (!iso) return ''
  const d = new Date(iso)
  const now = new Date()
  const diff = (now.getTime() - d.getTime()) / 1000
  if (diff < 60) return '刚刚'
  if (diff < 3600) return Math.floor(diff / 60) + ' 分钟前'
  if (diff < 86400) return Math.floor(diff / 3600) + ' 小时前'
  if (diff < 604800) return Math.floor(diff / 86400) + ' 天前'
  return d.toLocaleDateString('zh-CN', { year: 'numeric', month: '2-digit', day: '2-digit' })
}

function formatFullTime(iso: string): string {
  if (!iso) return ''
  return new Date(iso).toLocaleString('zh-CN', {
    year: 'numeric',
    month: '2-digit',
    day: '2-digit',
    hour: '2-digit',
    minute: '2-digit'
  })
}

function goBack() {
  router.push('/chat')
}

// 分页状态
const currentPage = ref(0)
const pageSize = ref(50)
const totalSessions = ref(0)
const totalPages = ref(1)

async function loadSessions(page = 0) {
  loading.value = true
  try {
    const result = await api.listAllSessions(page, pageSize.value)
    sessions.value = result.sessions
    totalSessions.value = result.total
    currentPage.value = result.page
    totalPages.value = result.totalPages
  } catch (e) {
    console.error('加载历史会话失败', e)
  } finally {
    loading.value = false
  }
}

async function goToPage(page: number) {
  if (page >= 0 && page < totalPages.value) {
    await loadSessions(page)
  }
}

onMounted(() => loadSessions())
</script>

<template>
  <div class="history-page">
    <header class="history-header">
      <div class="header-left">
        <button class="back-btn" type="button" @click="goBack">
          <svg viewBox="0 0 16 16" width="16" height="16">
            <path d="M10 3L5 8l5 5" fill="none" stroke="currentColor" stroke-width="1.5" stroke-linecap="round" stroke-linejoin="round"/>
          </svg>
          返回
        </button>
        <AppMark variant="product" size="sm" />
        <h1>全部历史</h1>
      </div>
      <div class="header-right">
        <div class="search-box">
          <svg viewBox="0 0 16 16" width="14" height="14">
            <circle cx="7" cy="7" r="5" fill="none" stroke="currentColor" stroke-width="1.4"/>
            <path d="M11 11l3.5 3.5" fill="none" stroke="currentColor" stroke-width="1.4" stroke-linecap="round"/>
          </svg>
          <input v-model="searchQuery" placeholder="搜索会话标题或助手名称..." />
        </div>
        <div class="sort-toggle">
          <button
            :class="{ active: sortBy === 'time' }"
            @click="sortBy = 'time'"
          >
            按时间
          </button>
          <button
            :class="{ active: sortBy === 'title' }"
            @click="sortBy = 'title'"
          >
            按标题
          </button>
        </div>
      </div>
    </header>

    <main class="history-content">
      <div v-if="loading" class="loading-state">
        <p>加载中...</p>
      </div>
      <div v-else-if="sessions.length === 0" class="empty-state">
        <p>加载中...</p>
      </div>
      <div v-else-if="filteredSessions.length === 0" class="empty-state">
        <p>{{ searchQuery ? '没有找到匹配的会话' : '暂无历史会话' }}</p>
      </div>
      <div v-else class="tree-table">
        <div
          v-for="(sessions, folder) in groupedSessions"
          :key="folder"
          class="folder-group"
        >
          <div
            class="folder-header"
            @click="toggleFolder(folder)"
          >
            <span class="folder-icon">{{ expandedFolders[folder] ? '▾' : '▸' }}</span>
            <span class="folder-name">{{ folder }}</span>
            <span class="folder-count">{{ sessions.length }}</span>
          </div>
          <div v-if="expandedFolders[folder]" class="folder-content">
            <div class="table-header">
              <span class="col-title">标题</span>
              <span class="col-agent">助手</span>
              <span class="col-time">更新时间</span>
            </div>
            <div
              v-for="session in sessions"
              :key="session.id"
              class="session-row"
              @click="navigateToSession(session.id)"
            >
              <span class="col-title" :title="session.title || '未命名会话'">
                {{ session.title || '未命名会话' }}
              </span>
              <span class="col-agent">
                <span class="agent-badge">{{ session.agentName || '基础模型' }}</span>
              </span>
              <span class="col-time" :title="formatFullTime(session.updatedAt)">
                {{ formatTime(session.updatedAt) }}
              </span>
            </div>
          </div>
        </div>
      </div>
    
      <div v-if="totalPages > 1" class="pagination">
        <button 
          :disabled="currentPage === 0"
          @click="goToPage(currentPage - 1)"
          class="page-btn"
        >
          上一页
        </button>
        <span class="page-info">
          第 {{ currentPage + 1 }} / {{ totalPages }} 页 · 共 {{ totalSessions }} 条
        </span>
        <button 
          :disabled="currentPage >= totalPages - 1"
          @click="goToPage(currentPage + 1)"
          class="page-btn"
        >
          下一页
        </button>
      </div>
    </main>
  </div>
</template>

<style scoped>
.history-page {
  display: flex;
  flex-direction: column;
  height: 100vh;
  background: var(--c-bg);
}

.history-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 16px 24px;
  background: var(--c-surface);
  border-bottom: 1px solid var(--c-border);
  flex-wrap: wrap;
  gap: 12px;
}

.header-left {
  display: flex;
  align-items: center;
  gap: 12px;
}

.back-btn {
  display: flex;
  align-items: center;
  gap: 4px;
  padding: 6px 12px;
  border: 1px solid var(--c-border);
  border-radius: var(--radius);
  background: var(--c-surface);
  color: var(--c-text);
  cursor: pointer;
  font-size: 13px;
  transition: all 0.2s;
}

.back-btn:hover {
  background: var(--c-bg);
  color: var(--c-primary);
  border-color: var(--c-primary);
}

.header-left h1 {
  margin: 0;
  font-size: 18px;
  font-weight: 600;
}

.header-right {
  display: flex;
  align-items: center;
  gap: 12px;
}

.search-box {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 8px 12px;
  border: 1px solid var(--c-border);
  border-radius: var(--radius);
  background: var(--c-surface);
  min-width: 240px;
}

.search-box svg {
  color: var(--c-text-muted);
  flex-shrink: 0;
}

.search-box input {
  border: none;
  outline: none;
  background: transparent;
  font-size: 13px;
  width: 100%;
  color: var(--c-text);
}

.sort-toggle {
  display: flex;
  border: 1px solid var(--c-border);
  border-radius: var(--radius);
  overflow: hidden;
}

.sort-toggle button {
  padding: 8px 12px;
  border: none;
  background: var(--c-surface);
  color: var(--c-text-muted);
  cursor: pointer;
  font-size: 13px;
  transition: all 0.2s;
}

.sort-toggle button.active {
  background: var(--c-primary);
  color: white;
}

.sort-toggle button:not(:last-child) {
  border-right: 1px solid var(--c-border);
}

.history-content {
  flex: 1;
  overflow-y: auto;
  padding: 20px 24px;
}

.loading-state,
.empty-state {
  display: flex;
  align-items: center;
  justify-content: center;
  height: 200px;
  color: var(--c-text-muted);
}

.tree-table {
  max-width: 1000px;
  margin: 0 auto;
}

.folder-group {
  margin-bottom: 16px;
  background: var(--c-surface);
  border: 1px solid var(--c-border);
  border-radius: var(--radius);
  overflow: hidden;
}

.folder-header {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 12px 16px;
  cursor: pointer;
  background: var(--c-bg);
  border-bottom: 1px solid var(--c-border);
  user-select: none;
}

.folder-header:hover {
  background: var(--c-primary-soft);
}

.folder-icon {
  color: var(--c-text-muted);
  font-size: 12px;
}

.folder-name {
  font-weight: 600;
  font-size: 14px;
}

.folder-count {
  margin-left: auto;
  font-size: 12px;
  color: var(--c-text-muted);
  background: var(--c-bg);
  padding: 2px 8px;
  border-radius: 10px;
}

.folder-content {
  animation: slideDown 0.2s ease;
}

@keyframes slideDown {
  from {
    opacity: 0;
    max-height: 0;
  }
  to {
    opacity: 1;
    max-height: 1000px;
  }
}

.table-header {
  display: flex;
  align-items: center;
  padding: 8px 16px;
  background: var(--c-surface);
  border-bottom: 1px solid var(--c-border);
  font-size: 11px;
  font-weight: 600;
  color: var(--c-text-muted);
  text-transform: uppercase;
}

.session-row {
  display: flex;
  align-items: center;
  padding: 12px 16px;
  cursor: pointer;
  transition: background 0.2s;
  border-bottom: 1px solid var(--c-border);
}

.session-row:last-child {
  border-bottom: none;
}

.session-row:hover {
  background: var(--c-primary-soft);
}

.col-title {
  flex: 1;
  min-width: 0;
  font-size: 13px;
  font-weight: 500;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.col-agent {
  width: 120px;
  flex-shrink: 0;
}

.agent-badge {
  display: inline-block;
  padding: 2px 8px;
  font-size: 11px;
  background: var(--c-bg);
  border: 1px solid var(--c-border);
  border-radius: 10px;
  color: var(--c-text-secondary);
}

.col-time {
  width: 120px;
  flex-shrink: 0;
  font-size: 12px;
  color: var(--c-text-muted);
  text-align: right;
}

@media (max-width: 768px) {
  .history-header {
    flex-direction: column;
    align-items: flex-start;
  }
  
  .header-right {
    width: 100%;
    flex-wrap: wrap;
  }
  
  .search-box {
    flex: 1;
    min-width: 200px;
  }
  
  .col-agent {
    display: none;
  }
  
  .col-time {
    width: 100px;
  }
}

.pagination {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 16px;
  padding: 20px 0;
  margin-top: 20px;
}

.page-btn {
  padding: 8px 16px;
  border: 1px solid var(--c-border);
  border-radius: var(--radius);
  background: var(--c-surface);
  color: var(--c-text);
  cursor: pointer;
  font-size: 13px;
  transition: all 0.2s;
}

.page-btn:hover:not(:disabled) {
  background: var(--c-primary);
  color: white;
  border-color: var(--c-primary);
}

.page-btn:disabled {
  opacity: 0.5;
  cursor: not-allowed;
}

.page-info {
  font-size: 13px;
  color: var(--c-text-muted);
}
</style>