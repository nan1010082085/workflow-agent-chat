import { createRouter, createWebHistory } from 'vue-router'

const router = createRouter({
  history: createWebHistory(import.meta.env.BASE_URL),
  routes: [
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
  ],
})

export default router
