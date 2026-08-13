<script setup lang="ts">
import { ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useAuthStore } from '../stores/auth'

const auth = useAuthStore()
const router = useRouter()
const route = useRoute()

const mode = ref<'login' | 'register'>('login')
const username = ref('')
const password = ref('')
const displayName = ref('')
const tenantCode = ref('')
const submitting = ref(false)
const localError = ref('')

/**
 * 登录或注册成功后进入对话。
 */
async function goWorkspace() {
  const redirect = typeof route.query.redirect === 'string' ? route.query.redirect : '/chat'
  await router.replace(redirect || '/chat')
}

/**
 * 使用平台账号登录 Chat（UI 为 Chat 自建）。
 */
async function onLogin() {
  localError.value = ''
  if (!username.value.trim() || !password.value) {
    localError.value = '请输入用户名和密码'
    return
  }
  submitting.value = true
  try {
    await auth.login(
      username.value.trim(),
      password.value,
      tenantCode.value.trim() || undefined,
    )
    await goWorkspace()
  } catch (e: unknown) {
    localError.value = e instanceof Error ? e.message : '登录失败'
  } finally {
    submitting.value = false
  }
}

/**
 * 在平台注册账号并自动登录。
 */
async function onRegister() {
  localError.value = ''
  if (!username.value.trim() || !password.value) {
    localError.value = '请输入用户名和密码'
    return
  }
  submitting.value = true
  try {
    await auth.register({
      username: username.value.trim(),
      password: password.value,
      displayName: displayName.value.trim() || undefined,
    })
    await goWorkspace()
  } catch (e: unknown) {
    localError.value = e instanceof Error ? e.message : '注册失败'
  } finally {
    submitting.value = false
  }
}

function switchMode(next: 'login' | 'register') {
  mode.value = next
  localError.value = ''
  auth.error = null
}
</script>

<template>
  <div class="login-page">
    <form
      class="login-card"
      @submit.prevent="mode === 'login' ? onLogin() : onRegister()"
    >
      <div class="brand">
        <span class="brand-mark">W</span>
        <div>
          <h1>任务对话</h1>
          <p>{{ mode === 'login' ? '登录后继续你的助手与模型对话' : '注册平台账号，即可开始对话' }}</p>
        </div>
      </div>

      <div class="tabs" role="tablist">
        <button
          type="button"
          role="tab"
          :aria-selected="mode === 'login'"
          :class="{ active: mode === 'login' }"
          @click="switchMode('login')"
        >
          登录
        </button>
        <button
          type="button"
          role="tab"
          :aria-selected="mode === 'register'"
          :class="{ active: mode === 'register' }"
          @click="switchMode('register')"
        >
          注册
        </button>
      </div>

      <label>
        <span>用户名</span>
        <input v-model="username" autocomplete="username" autofocus />
      </label>
      <label v-if="mode === 'register'">
        <span>显示名称 <em>可选</em></span>
        <input v-model="displayName" autocomplete="nickname" placeholder="默认与用户名相同" />
      </label>
      <label>
        <span>密码</span>
        <input v-model="password" type="password" :autocomplete="mode === 'login' ? 'current-password' : 'new-password'" />
      </label>
      <p v-if="mode === 'register'" class="hint">
        密码至少 8 位，需包含大小写字母和数字（平台策略）。
      </p>
      <label v-if="mode === 'login'" class="optional">
        <span>租户编码 <em>可选</em></span>
        <input v-model="tenantCode" placeholder="默认与平台一致" autocomplete="organization" />
      </label>

      <p v-if="localError || auth.error" class="err" role="alert">{{ localError || auth.error }}</p>

      <button type="submit" class="btn btn-primary submit" :disabled="submitting">
        <template v-if="mode === 'login'">{{ submitting ? '登录中…' : '进入对话' }}</template>
        <template v-else>{{ submitting ? '注册中…' : '注册并进入' }}</template>
      </button>
    </form>
  </div>
</template>

<style scoped>
.login-page {
  min-height: 100vh;
  display: grid;
  place-items: center;
  padding: 24px;
  background:
    radial-gradient(900px 420px at 12% -8%, var(--c-primary-soft) 0%, transparent 55%),
    radial-gradient(700px 360px at 100% 0%, var(--c-accent-soft) 0%, transparent 48%),
    var(--c-bg);
}
.login-card {
  width: min(400px, 100%);
  display: flex;
  flex-direction: column;
  gap: 14px;
  padding: 28px 24px;
  border: 1px solid var(--c-border);
  background: var(--c-surface);
  border-radius: var(--radius-lg);
  box-shadow: var(--shadow);
}
.brand {
  display: flex;
  gap: 12px;
  align-items: center;
  margin-bottom: 2px;
}
.brand-mark {
  width: 40px;
  height: 40px;
  border-radius: var(--radius-lg);
  display: grid;
  place-items: center;
  background: var(--c-primary);
  color: #fff;
  font-weight: 800;
}
h1 {
  margin: 0;
  font-size: 18px;
  color: var(--c-text);
}
.brand p {
  margin: 2px 0 0;
  font-size: 12px;
  color: var(--c-text-muted);
}
.tabs {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 4px;
  padding: 4px;
  background: var(--c-bg);
  border-radius: var(--radius);
}
.tabs button {
  border: 0;
  background: transparent;
  padding: 8px;
  border-radius: var(--radius-sm);
  cursor: pointer;
  font-size: 13px;
  font-weight: 600;
  color: var(--c-text-secondary);
}
.tabs button.active {
  background: var(--c-surface);
  color: var(--c-primary);
  box-shadow: var(--shadow-sm);
}
label {
  display: flex;
  flex-direction: column;
  gap: 6px;
  font-size: 13px;
  color: var(--c-text-secondary);
}
label em {
  font-style: normal;
  color: var(--c-text-muted);
  font-size: 11px;
  margin-left: 4px;
}
input {
  height: 40px;
  border: 1px solid var(--c-border);
  border-radius: var(--radius);
  padding: 0 12px;
  font-size: 14px;
  background: var(--c-surface);
  color: var(--c-text);
}
input:focus {
  outline: 2px solid var(--c-primary-soft);
  border-color: var(--c-primary);
}
.hint {
  margin: -6px 0 0;
  font-size: 12px;
  color: var(--c-text-muted);
  line-height: 1.5;
}
.err {
  margin: 0;
  color: var(--c-danger);
  font-size: 13px;
}
.submit {
  height: 42px;
  justify-content: center;
  font-weight: 600;
  margin-top: 4px;
}
</style>
