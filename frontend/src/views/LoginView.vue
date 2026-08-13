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
    <!-- 氛围背景：渐变雾 + 漂移光斑 + 点阵 -->
    <div class="bg" aria-hidden="true">
      <div class="bg-wash" />
      <div class="orb orb-a" />
      <div class="orb orb-b" />
      <div class="orb orb-c" />
      <div class="bg-grid" />
      <div class="bg-grain" />
    </div>

    <div class="stage">
      <header class="hero">
        <span class="brand-mark" aria-hidden="true">澄</span>
        <h1 class="brand-name">澄语</h1>
        <p class="tagline">和助手聊聊，把事情办完</p>
      </header>

      <form
        class="login-card"
        @submit.prevent="mode === 'login' ? onLogin() : onRegister()"
      >
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

        <p class="mode-hint">
          {{ mode === 'login' ? '登录后继续你的对话' : '注册账号，即可开始对话' }}
        </p>

        <label class="field">
          <span>用户名</span>
          <input v-model="username" autocomplete="username" autofocus />
        </label>
        <label v-if="mode === 'register'" class="field">
          <span>显示名称 <em>可选</em></span>
          <input v-model="displayName" autocomplete="nickname" placeholder="默认与用户名相同" />
        </label>
        <label class="field">
          <span>密码</span>
          <input
            v-model="password"
            type="password"
            :autocomplete="mode === 'login' ? 'current-password' : 'new-password'"
          />
        </label>
        <p v-if="mode === 'register'" class="hint">
          密码至少 8 位，需包含大小写字母和数字（平台策略）。
        </p>
        <label v-if="mode === 'login'" class="field optional">
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
  </div>
</template>

<style scoped>
@import url('https://fonts.googleapis.com/css2?family=Noto+Serif+SC:wght@600;700&display=swap');

.login-page {
  position: relative;
  isolation: isolate;
  min-height: 100vh;
  display: grid;
  place-items: center;
  padding: 28px 20px;
  overflow: hidden;
  background: #e8f1f0;
}

/* —— 背景层 —— */
.bg {
  position: absolute;
  inset: 0;
  z-index: 0;
  pointer-events: none;
  overflow: hidden;
}
.bg-wash {
  position: absolute;
  inset: -20%;
  background:
    radial-gradient(ellipse 70% 55% at 18% 22%, rgba(13, 107, 103, 0.22), transparent 58%),
    radial-gradient(ellipse 55% 45% at 88% 12%, rgba(243, 155, 69, 0.18), transparent 55%),
    radial-gradient(ellipse 60% 50% at 70% 88%, rgba(43, 122, 179, 0.12), transparent 60%),
    linear-gradient(165deg, #eef6f5 0%, #e4eeed 42%, #f3f0ea 100%);
  animation: wash-drift 18s ease-in-out infinite alternate;
}
.orb {
  position: absolute;
  border-radius: 50%;
  filter: blur(48px);
  opacity: 0.55;
  will-change: transform;
}
.orb-a {
  width: min(42vw, 420px);
  height: min(42vw, 420px);
  left: -8%;
  top: 8%;
  background: rgba(13, 107, 103, 0.35);
  animation: orb-float-a 14s ease-in-out infinite;
}
.orb-b {
  width: min(36vw, 360px);
  height: min(36vw, 360px);
  right: -6%;
  top: 18%;
  background: rgba(243, 155, 69, 0.28);
  animation: orb-float-b 16s ease-in-out infinite;
}
.orb-c {
  width: min(48vw, 480px);
  height: min(48vw, 480px);
  left: 28%;
  bottom: -18%;
  background: rgba(47, 158, 116, 0.2);
  animation: orb-float-c 20s ease-in-out infinite;
}
.bg-grid {
  position: absolute;
  inset: 0;
  opacity: 0.35;
  background-image:
    radial-gradient(circle at 1px 1px, rgba(13, 107, 103, 0.22) 1px, transparent 0);
  background-size: 28px 28px;
  mask-image: radial-gradient(ellipse 75% 70% at 50% 45%, #000 20%, transparent 75%);
  animation: grid-pulse 10s ease-in-out infinite;
}
.bg-grain {
  position: absolute;
  inset: 0;
  opacity: 0.04;
  background-image: url("data:image/svg+xml,%3Csvg viewBox='0 0 200 200' xmlns='http://www.w3.org/2000/svg'%3E%3Cfilter id='n'%3E%3CfeTurbulence type='fractalNoise' baseFrequency='0.85' numOctaves='3' stitchTiles='stitch'/%3E%3C/filter%3E%3Crect width='100%25' height='100%25' filter='url(%23n)'/%3E%3C/svg%3E");
  background-size: 180px 180px;
}

.stage {
  position: relative;
  z-index: 1;
  width: min(420px, 100%);
  display: flex;
  flex-direction: column;
  align-items: stretch;
  gap: 22px;
}

/* —— 品牌区（首屏主角） —— */
.hero {
  text-align: center;
  animation: rise-in 0.7s cubic-bezier(0.22, 1, 0.36, 1) both;
}
.brand-mark {
  display: grid;
  place-items: center;
  width: 56px;
  height: 56px;
  margin: 0 auto 14px;
  border-radius: 14px;
  background: linear-gradient(145deg, var(--c-primary) 0%, #0a8a84 100%);
  color: #fff;
  font-family: 'Noto Serif SC', 'Songti SC', serif;
  font-size: 26px;
  font-weight: 700;
  letter-spacing: 0.02em;
  box-shadow: 0 10px 28px rgba(13, 107, 103, 0.28);
  animation: mark-breathe 4.5s ease-in-out infinite;
}
.brand-name {
  margin: 0;
  font-family: 'Noto Serif SC', 'Songti SC', serif;
  font-size: clamp(32px, 6vw, 40px);
  font-weight: 700;
  letter-spacing: 0.12em;
  color: var(--c-text);
  line-height: 1.15;
}
.tagline {
  margin: 10px 0 0;
  font-size: 14px;
  color: var(--c-text-secondary);
  letter-spacing: 0.04em;
  animation: rise-in 0.75s cubic-bezier(0.22, 1, 0.36, 1) 0.08s both;
}

.login-card {
  display: flex;
  flex-direction: column;
  gap: 14px;
  padding: 24px 22px 22px;
  border: 1px solid rgba(223, 231, 232, 0.9);
  background: rgba(255, 255, 255, 0.82);
  backdrop-filter: blur(14px);
  -webkit-backdrop-filter: blur(14px);
  border-radius: 12px;
  box-shadow:
    0 1px 0 rgba(255, 255, 255, 0.7) inset,
    0 18px 40px rgba(23, 33, 43, 0.08);
  animation: rise-in 0.8s cubic-bezier(0.22, 1, 0.36, 1) 0.14s both;
}

.tabs {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 4px;
  padding: 4px;
  background: rgba(244, 247, 248, 0.9);
  border-radius: var(--radius);
}
.tabs button {
  border: 0;
  background: transparent;
  padding: 9px;
  border-radius: var(--radius-sm);
  cursor: pointer;
  font-size: 13px;
  font-weight: 600;
  color: var(--c-text-secondary);
  transition: color 0.2s ease, background 0.2s ease, box-shadow 0.2s ease, transform 0.15s ease;
}
.tabs button:hover {
  color: var(--c-text);
}
.tabs button.active {
  background: var(--c-surface);
  color: var(--c-primary);
  box-shadow: var(--shadow-sm);
}
.tabs button:active {
  transform: scale(0.98);
}

.mode-hint {
  margin: -2px 0 2px;
  font-size: 12px;
  color: var(--c-text-muted);
  text-align: center;
}

.field {
  display: flex;
  flex-direction: column;
  gap: 6px;
  font-size: 13px;
  color: var(--c-text-secondary);
}
.field em {
  font-style: normal;
  color: var(--c-text-muted);
  font-size: 11px;
  margin-left: 4px;
}
input {
  height: 42px;
  border: 1px solid var(--c-border);
  border-radius: var(--radius);
  padding: 0 12px;
  font-size: 14px;
  background: rgba(255, 255, 255, 0.95);
  color: var(--c-text);
  transition: border-color 0.2s ease, box-shadow 0.2s ease;
}
input:focus {
  outline: none;
  border-color: var(--c-primary);
  box-shadow: 0 0 0 3px rgba(13, 107, 103, 0.14);
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
  animation: shake 0.4s ease;
}
.submit {
  height: 44px;
  justify-content: center;
  font-weight: 600;
  margin-top: 4px;
  transition: transform 0.15s ease, background 0.2s ease, box-shadow 0.2s ease;
  box-shadow: 0 8px 20px rgba(13, 107, 103, 0.22);
}
.submit:hover:not(:disabled) {
  transform: translateY(-1px);
  box-shadow: 0 10px 24px rgba(13, 107, 103, 0.28);
}
.submit:active:not(:disabled) {
  transform: translateY(0);
}

@keyframes rise-in {
  from {
    opacity: 0;
    transform: translateY(18px);
  }
  to {
    opacity: 1;
    transform: translateY(0);
  }
}
@keyframes mark-breathe {
  0%, 100% { transform: translateY(0); box-shadow: 0 10px 28px rgba(13, 107, 103, 0.28); }
  50% { transform: translateY(-3px); box-shadow: 0 14px 32px rgba(13, 107, 103, 0.34); }
}
@keyframes wash-drift {
  from { transform: translate3d(0, 0, 0) scale(1); }
  to { transform: translate3d(-2%, 1.5%, 0) scale(1.04); }
}
@keyframes orb-float-a {
  0%, 100% { transform: translate(0, 0); }
  50% { transform: translate(6%, 8%); }
}
@keyframes orb-float-b {
  0%, 100% { transform: translate(0, 0); }
  50% { transform: translate(-8%, 6%); }
}
@keyframes orb-float-c {
  0%, 100% { transform: translate(0, 0); }
  50% { transform: translate(4%, -6%); }
}
@keyframes grid-pulse {
  0%, 100% { opacity: 0.28; }
  50% { opacity: 0.42; }
}
@keyframes shake {
  0%, 100% { transform: translateX(0); }
  25% { transform: translateX(-4px); }
  75% { transform: translateX(4px); }
}

@media (prefers-reduced-motion: reduce) {
  .bg-wash,
  .orb,
  .bg-grid,
  .brand-mark,
  .hero,
  .tagline,
  .login-card,
  .err {
    animation: none !important;
  }
  .hero,
  .tagline,
  .login-card {
    opacity: 1;
    transform: none;
  }
}

@media (max-width: 480px) {
  .login-card {
    padding: 20px 16px 18px;
  }
  .brand-mark {
    width: 48px;
    height: 48px;
    font-size: 22px;
  }
}
</style>
