import { createRouter, createWebHistory } from 'vue-router'
import { useAuthStore } from '../stores/auth'

const router = createRouter({
  history: createWebHistory(import.meta.env.BASE_URL),
  routes: [
    {
      path: '/login',
      name: 'login',
      component: () => import('../views/LoginView.vue'),
      meta: { public: true },
    },
    { path: '/', redirect: '/chat' },
    {
      path: '/chat',
      name: 'workspace',
      component: () => import('../views/WorkspaceView.vue'),
    },
    {
      path: '/chat/:sessionId',
      name: 'session',
      component: () => import('../views/WorkspaceView.vue'),
    },
    {
      path: '/history',
      name: 'history',
      component: () => import('../views/HistoryView.vue'),
    },
  ],
})

router.beforeEach(async (to) => {
  const auth = useAuthStore()
  if (!auth.bootstrapped) {
    await auth.bootstrap()
  }
  // 公开页：未登录可进；已登录访问 /login 才回对话（避免未鉴权完成时误进 Chat）
  if (to.meta.public) {
    if (auth.isAuthenticated && to.name === 'login') {
      return { path: '/chat' }
    }
    return true
  }
  if (!auth.isAuthenticated) {
    return { name: 'login', query: { redirect: to.fullPath } }
  }
  return true
})

export default router