<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { api } from '../api/client'
import AppMark from '../components/AppMark.vue'

const router = useRouter()

const loading = ref(false)
const sessions = ref<any[]>([])
const searchQuery = ref('')
const sortBy = ref<'time' | 'title'>('time')

const currentPage = ref(0)
const pageSize = ref(50)
const totalSessions = ref(0)
const totalPages = ref(1)

const folders = ref<string[]>(JSON.parse(localStorage.getItem('chat-folders') || '[]'))
const sessionFolders = ref<Record<string, string>>(JSON.parse(localStorage.getItem('chat-session-folders') || '{}'))
const expandedFolders = ref<Record<string, boolean>>({})
folders.value.forEach(f => { expandedFolders.value[f] = true })

const groupedSessions = computed(() => {
  const groups: Record<string, any[]> = {}
  groups['未分类'] = []
  folders.value.forEach(f => { groups[f] = [] })
  filteredSessions.value.forEach(s => {
    const folder = sessionFolders.value[s.id] || '未分类'
    if (!groups[folder]) groups[folder] = []
    groups[folder].push(s)
  })
  Object.keys(groups).forEach(key => {
    if (groups[key].length === 0) delete groups[key]
  })
  return groups
})

const filteredSessions = computed(() => {
  let list = [...sessions.value]
  if (searchQuery.value) {
    const q = searchQuery.value.toLowerCase()
    list = list.filter(s => (s.title || '').toLowerCase().includes(q) || (s.agentName || '').toLowerCase().includes(q))
  }
  if (sortBy.value === 'time') {
    list.sort((a, b) => new Date(b.updatedAt).getTime() - new Date(a.updatedAt).getTime())
  } else {
    list.sort((a, b) => (a.title || '').localeCompare(b.title || ''))
  }
  return list
})

function toggleFolder(folder: string) {
  expandedFolders.value[folder] = !expandedFolders.value[folder]
}

async function loadSessions(page = 0) {
  loading.value = true
  try {
    const result = await api.listAllSessions(page, pageSize.value)
    sessions.value = result.sessions
    totalSessions.value = result.total
    currentPage.value = result.page
    totalPages.value = result.totalPages
  } catch (e) {
    console.error('加载失败', e)
  } finally {
    loading.value = false
  }
}

async function goToPage(page: number) {
  if (page >= 0 && page < totalPages.value) await loadSessions(page)
}

function formatTime(iso: string): string {
  if (!iso) return ''
  const diff = (Date.now() - new Date(iso).getTime()) / 1000
  if (diff < 60) return '刚刚'
  if (diff < 3600) return Math.floor(diff / 60) + '分钟前'
  if (diff < 86400) return Math.floor(diff / 3600) + '小时前'
  return new Date(iso).toLocaleDateString('zh-CN', { month: '2-digit', day: '2-digit' })
}

onMounted(() => loadSessions())
</script>

<template>
  <div class="page">
    <header class="bar">
      <div class="bar-l">
        <button class="back" @click="router.push('/chat')" title="返回">
          <svg viewBox="0 0 16 16" width="14" height="14"><path d="M10 3L5 8l5 5" fill="none" stroke="currentColor" stroke-width="1.5" stroke-linecap="round" stroke-linejoin="round"/></svg>
        </button>
        <AppMark variant="product" size="sm" />
        <b>全部历史</b>
        <span class="badge">{{ totalSessions }}</span>
      </div>
      <div class="bar-r">
        <div class="search">
          <svg viewBox="0 0 16 16" width="12" height="12"><circle cx="7" cy="7" r="5" fill="none" stroke="currentColor" stroke-width="1.4"/><path d="M11 11l3.5 3.5" fill="none" stroke="currentColor" stroke-width="1.4" stroke-linecap="round"/></svg>
          <input v-model="searchQuery" placeholder="搜索..." />
        </div>
        <div class="sort">
          <button :class="{ on: sortBy === 'time' }" @click="sortBy = 'time'">时间</button>
          <button :class="{ on: sortBy === 'title' }" @click="sortBy = 'title'">标题</button>
        </div>
      </div>
    </header>

    <main class="body">
      <div v-if="loading" class="msg">加载中…</div>
      <div v-else-if="filteredSessions.length === 0" class="msg">{{ searchQuery ? '无匹配' : '暂无历史' }}</div>
      <div v-else class="list">
        <div v-for="(items, folder) in groupedSessions" :key="folder" class="grp">
          <div class="grp-hd" @click="toggleFolder(folder as string)">
            <span class="arrow">{{ expandedFolders[folder as string] ? '▾' : '▸' }}</span>
            <span>{{ folder }}</span>
            <span class="cnt">{{ items.length }}</span>
          </div>
          <template v-if="expandedFolders[folder as string]">
            <div v-for="s in items" :key="s.id" class="row" @click="router.push('/chat/' + s.id)">
              <span class="t1" :title="s.title">{{ s.title || '未命名' }}</span>
              <span class="t2">{{ s.agentName || '基础模型' }}</span>
              <span class="t3">{{ formatTime(s.updatedAt) }}</span>
            </div>
          </template>
        </div>
      </div>
    </main>

    <footer v-if="totalPages > 1" class="pg">
      <button :disabled="currentPage === 0" @click="goToPage(currentPage - 1)">‹</button>
      <span>{{ currentPage + 1 }} / {{ totalPages }}</span>
      <button :disabled="currentPage >= totalPages - 1" @click="goToPage(currentPage + 1)">›</button>
    </footer>
  </div>
</template>

<style scoped>
.page { display: flex; flex-direction: column; height: 100vh; background: var(--c-bg); overflow: hidden; }

/* 顶栏 */
.bar { display: flex; align-items: center; justify-content: space-between; padding: 8px 14px; background: var(--c-surface); border-bottom: 1px solid var(--c-border); flex-shrink: 0; gap: 10px; }
.bar-l { display: flex; align-items: center; gap: 8px; }
.bar-l b { font-size: 14px; }
.badge { font-size: 10px; padding: 1px 5px; background: var(--c-bg); border-radius: 8px; color: var(--c-text-muted); }
.bar-r { display: flex; align-items: center; gap: 6px; }
.back { display: flex; align-items: center; justify-content: center; width: 26px; height: 26px; padding: 0; border: 1px solid var(--c-border); border-radius: 5px; background: var(--c-surface); color: var(--c-text); cursor: pointer; }
.back:hover { background: var(--c-bg); color: var(--c-primary); }

.search { display: flex; align-items: center; gap: 5px; padding: 4px 8px; border: 1px solid var(--c-border); border-radius: 5px; }
.search svg { color: var(--c-text-muted); }
.search input { border: none; outline: none; background: transparent; font-size: 12px; width: 140px; color: var(--c-text); }

.sort { display: flex; border: 1px solid var(--c-border); border-radius: 5px; overflow: hidden; }
.sort button { padding: 4px 8px; border: none; background: var(--c-surface); color: var(--c-text-muted); cursor: pointer; font-size: 11px; }
.sort button.on { background: var(--c-primary); color: #fff; }
.sort button:first-child { border-right: 1px solid var(--c-border); }

/* 内容 */
.body { flex: 1; overflow-y: auto; padding: 8px 14px; }
.msg { display: flex; align-items: center; justify-content: center; height: 160px; color: var(--c-text-muted); font-size: 13px; }
.list { max-width: 900px; margin: 0 auto; }

/* 目录组 */
.grp { margin-bottom: 6px; border: 1px solid var(--c-border); border-radius: 6px; overflow: hidden; }
.grp-hd { display: flex; align-items: center; gap: 5px; padding: 6px 10px; background: var(--c-bg); cursor: pointer; font-size: 12px; font-weight: 600; }
.grp-hd:hover { background: var(--c-primary-soft); }
.arrow { font-size: 10px; color: var(--c-text-muted); width: 10px; }
.cnt { margin-left: auto; font-size: 10px; color: var(--c-text-muted); font-weight: 400; }

/* 行 */
.row { display: flex; align-items: center; padding: 5px 10px 5px 24px; cursor: pointer; border-top: 1px solid var(--c-border); font-size: 12px; }
.row:hover { background: var(--c-primary-soft); }
.t1 { flex: 1; min-width: 0; white-space: nowrap; overflow: hidden; text-overflow: ellipsis; }
.t2 { width: 80px; flex-shrink: 0; text-align: center; color: var(--c-text-muted); font-size: 11px; }
.t3 { width: 70px; flex-shrink: 0; text-align: right; color: var(--c-text-muted); font-size: 11px; }

/* 分页 */
.pg { display: flex; align-items: center; justify-content: center; gap: 10px; padding: 6px 14px; background: var(--c-surface); border-top: 1px solid var(--c-border); flex-shrink: 0; }
.pg button { display: flex; align-items: center; justify-content: center; width: 26px; height: 26px; border: 1px solid var(--c-border); border-radius: 5px; background: var(--c-surface); color: var(--c-text); cursor: pointer; font-size: 15px; }
.pg button:hover:not(:disabled) { background: var(--c-primary); color: #fff; border-color: var(--c-primary); }
.pg button:disabled { opacity: 0.35; cursor: not-allowed; }
.pg span { font-size: 11px; color: var(--c-text-muted); }

@media (max-width: 600px) {
  .search input { width: 90px; }
  .t2 { display: none; }
  .t3 { width: 55px; }
}
</style>