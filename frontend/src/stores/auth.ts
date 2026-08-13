import { defineStore } from 'pinia'
import { computed, ref } from 'vue'
import { api, setAccessTokenProvider, setRefreshHandler } from '../api/client'
import { setPlatformSocketTokenProvider } from '../api/platformSocket'

const ACCESS_KEY = 'wac_access_token'
const REFRESH_KEY = 'wac_refresh_token'
const USER_KEY = 'wac_user'

export interface AuthUserInfo {
  id: string
  username: string
  displayName?: string
  tenantId?: string
  roles?: string[]
}

/**
 * 登录态：代理平台 JWT，按用户隔离会话历史。
 */
export const useAuthStore = defineStore('auth', () => {
  const accessToken = ref<string | null>(localStorage.getItem(ACCESS_KEY))
  const refreshToken = ref<string | null>(localStorage.getItem(REFRESH_KEY))
  const user = ref<AuthUserInfo | null>(readUser())
  const bootstrapped = ref(false)
  const loading = ref(false)
  const error = ref<string | null>(null)

  const isAuthenticated = computed(() => Boolean(accessToken.value && user.value))

  setAccessTokenProvider(() => accessToken.value)
  setPlatformSocketTokenProvider(() => accessToken.value)
  setRefreshHandler(() => tryRefresh())

  function readUser(): AuthUserInfo | null {
    try {
      const raw = localStorage.getItem(USER_KEY)
      return raw ? JSON.parse(raw) as AuthUserInfo : null
    } catch {
      return null
    }
  }

  function persist() {
    if (accessToken.value) localStorage.setItem(ACCESS_KEY, accessToken.value)
    else localStorage.removeItem(ACCESS_KEY)
    if (refreshToken.value) localStorage.setItem(REFRESH_KEY, refreshToken.value)
    else localStorage.removeItem(REFRESH_KEY)
    if (user.value) localStorage.setItem(USER_KEY, JSON.stringify(user.value))
    else localStorage.removeItem(USER_KEY)
  }

  function clearSession() {
    accessToken.value = null
    refreshToken.value = null
    user.value = null
    persist()
  }

  function applyLoginPayload(data: Record<string, unknown>) {
    accessToken.value = String(data.accessToken || '')
    refreshToken.value = String(data.refreshToken || '')
    const u = data.user as Record<string, unknown> | undefined
    if (u) {
      user.value = {
        id: String(u.id || u._id || ''),
        username: String(u.username || ''),
        displayName: String(u.displayName || u.username || ''),
        tenantId: u.tenantId != null ? String(u.tenantId) : undefined,
        roles: Array.isArray(u.roles) ? u.roles.map(String) : [],
      }
    }
    persist()
  }

  /**
   * 启动时恢复会话；access 失效则尝试 refresh。
   */
  async function bootstrap() {
    if (bootstrapped.value) return
    loading.value = true
    try {
      if (!accessToken.value) {
        bootstrapped.value = true
        return
      }
      try {
        const me = await api.me() as unknown as AuthUserInfo
        user.value = {
          id: String(me.id),
          username: String(me.username || ''),
          displayName: String(me.displayName || me.username || ''),
          tenantId: me.tenantId,
          roles: me.roles || [],
        }
        persist()
      } catch {
        if (refreshToken.value) {
          const ok = await tryRefresh()
          if (!ok) clearSession()
        } else {
          clearSession()
        }
      }
    } finally {
      bootstrapped.value = true
      loading.value = false
    }
  }

  async function login(username: string, password: string, tenantCode?: string) {
    loading.value = true
    error.value = null
    try {
      const data = await api.login({ username, password, tenantCode }) as Record<string, unknown>
      applyLoginPayload(data)
      if (!user.value) {
        const me = await api.me() as unknown as AuthUserInfo
        user.value = {
          id: String(me.id),
          username: String(me.username || ''),
          displayName: String(me.displayName || me.username || ''),
          tenantId: me.tenantId,
          roles: me.roles || [],
        }
        persist()
      }
    } catch (e: unknown) {
      clearSession()
      error.value = e instanceof Error ? e.message : '登录失败'
      throw e
    } finally {
      loading.value = false
    }
  }

  /**
   * 在平台注册账号；成功后自动登录。
   */
  async function register(payload: {
    username: string
    password: string
    displayName?: string
    phone?: string
  }) {
    loading.value = true
    error.value = null
    try {
      await api.register(payload)
      await login(payload.username, payload.password)
    } catch (e: unknown) {
      error.value = e instanceof Error ? e.message : '注册失败'
      throw e
    } finally {
      loading.value = false
    }
  }

  async function tryRefresh(): Promise<boolean> {
    if (!refreshToken.value) return false
    try {
      const data = await api.refresh(refreshToken.value) as Record<string, unknown>
      if (data.accessToken) accessToken.value = String(data.accessToken)
      if (data.refreshToken) refreshToken.value = String(data.refreshToken)
      persist()
      const me = await api.me() as unknown as AuthUserInfo
      user.value = {
        id: String(me.id),
        username: String(me.username || ''),
        displayName: String(me.displayName || me.username || ''),
        tenantId: me.tenantId,
        roles: me.roles || [],
      }
      persist()
      return true
    } catch {
      return false
    }
  }

  async function logout() {
    try {
      await api.logout()
    } catch {
      /* ignore */
    }
    clearSession()
  }

  return {
    accessToken,
    refreshToken,
    user,
    bootstrapped,
    loading,
    error,
    isAuthenticated,
    bootstrap,
    login,
    register,
    logout,
    tryRefresh,
    clearSession,
  }
})
