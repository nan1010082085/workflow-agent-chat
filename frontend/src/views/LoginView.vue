<script setup lang="ts">
import { computed, nextTick, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useAuthStore } from '../stores/auth'
import AppMark from '../components/AppMark.vue'

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
/** 渐进步骤：身份 → 凭证 →（注册时）资料 */
const step = ref(1)
const showAdvanced = ref(false)
const touched = ref<Record<string, boolean>>({})

const usernameRef = ref<HTMLInputElement | null>(null)
const passwordRef = ref<HTMLInputElement | null>(null)
const displayNameRef = ref<HTMLInputElement | null>(null)

const usernameOk = computed(() => username.value.trim().length >= 2)
const passwordOk = computed(() => password.value.length >= 1)
const passwordStrongOk = computed(() => {
  if (mode.value !== 'register') return passwordOk.value
  const p = password.value
  return p.length >= 8 && /[a-z]/.test(p) && /[A-Z]/.test(p) && /\d/.test(p)
})

const canContinueIdentity = computed(() => usernameOk.value)
const canSubmit = computed(() => {
  if (!usernameOk.value || !passwordOk.value || submitting.value) return false
  if (mode.value === 'register' && !passwordStrongOk.value) return false
  return true
})

const usernameHint = computed(() => {
  if (!touched.value.username || usernameOk.value) return ''
  return '用户名至少 2 个字符'
})
const passwordHint = computed(() => {
  if (!touched.value.password) return ''
  if (!password.value) return '请输入密码'
  if (mode.value === 'register' && !passwordStrongOk.value) {
    return '至少 8 位，需含大小写字母和数字'
  }
  return ''
})

/**
 * 登录或注册成功后进入对话。
 */
async function goWorkspace() {
  const redirect = typeof route.query.redirect === 'string' ? route.query.redirect : '/chat'
  await router.replace(redirect || '/chat')
}

/**
 * 使用平台账号登录 Chat。
 */
async function onLogin() {
  localError.value = ''
  markTouched('username')
  markTouched('password')
  if (!canSubmit.value) {
    localError.value = usernameHint.value || passwordHint.value || '请完善登录信息'
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
  markTouched('username')
  markTouched('password')
  if (!canSubmit.value) {
    localError.value = usernameHint.value || passwordHint.value || '请完善注册信息'
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

function markTouched(key: string) {
  touched.value = { ...touched.value, [key]: true }
}

/**
 * 从身份步进入凭证步。
 */
async function continueToCredentials() {
  markTouched('username')
  if (!canContinueIdentity.value) return
  step.value = Math.max(step.value, 2)
  await nextTick()
  passwordRef.value?.focus()
}

function switchMode(next: 'login' | 'register') {
  mode.value = next
  localError.value = ''
  auth.error = null
  touched.value = {}
  step.value = usernameOk.value ? 2 : 1
  showAdvanced.value = false
}

watch(username, (v) => {
  if (v.trim().length >= 2 && step.value < 2) {
    step.value = 2
  }
})

watch(mode, async () => {
  await nextTick()
  if (step.value >= 2) passwordRef.value?.focus()
  else usernameRef.value?.focus()
})
</script>

<template>
  <div class="login-page">
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
        <AppMark class="hero-mark" variant="product" size="lg" />
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

        <div class="progress" aria-hidden="true">
          <span :class="{ on: step >= 1, done: step > 1 }" />
          <span :class="{ on: step >= 2, done: canSubmit }" />
        </div>

        <p class="mode-hint">
          <template v-if="step < 2">先输入你的账号</template>
          <template v-else-if="mode === 'login'">输入密码后进入对话</template>
          <template v-else>设置密码，完成注册</template>
        </p>

        <!-- 步骤 1：身份 -->
        <label class="field reveal" style="--d: 0ms">
          <span>用户名</span>
          <input
            ref="usernameRef"
            v-model="username"
            autocomplete="username"
            autofocus
            :aria-invalid="Boolean(usernameHint)"
            @blur="markTouched('username')"
            @keydown.enter.prevent="continueToCredentials"
          />
          <small v-if="usernameHint" class="field-msg">{{ usernameHint }}</small>
        </label>

        <!-- 步骤 2：凭证（用户名有效后渐进展开） -->
        <div v-if="step >= 2" class="cred-block">
          <label
            v-if="mode === 'register'"
            class="field reveal"
            style="--d: 40ms"
          >
            <span>显示名称 <em>可选</em></span>
            <input
              ref="displayNameRef"
              v-model="displayName"
              autocomplete="nickname"
              placeholder="默认与用户名相同"
            />
          </label>

          <label class="field reveal" style="--d: 80ms">
            <span>密码</span>
            <input
              ref="passwordRef"
              v-model="password"
              type="password"
              :autocomplete="mode === 'login' ? 'current-password' : 'new-password'"
              :aria-invalid="Boolean(passwordHint)"
              @blur="markTouched('password')"
            />
            <small v-if="passwordHint" class="field-msg">{{ passwordHint }}</small>
          </label>

          <p v-if="mode === 'register'" class="hint reveal" style="--d: 100ms">
            密码至少 8 位，需包含大小写字母和数字。
          </p>

          <div v-if="mode === 'login'" class="advanced reveal" style="--d: 120ms">
            <button
              type="button"
              class="advanced-toggle"
              :aria-expanded="showAdvanced"
              @click="showAdvanced = !showAdvanced"
            >
              {{ showAdvanced ? '收起选项' : '更多选项' }}
            </button>
            <label v-if="showAdvanced" class="field optional">
              <span>租户编码 <em>可选</em></span>
              <input v-model="tenantCode" placeholder="默认与平台一致" autocomplete="organization" />
            </label>
          </div>
        </div>

        <p v-if="localError || auth.error" class="err" role="alert">{{ localError || auth.error }}</p>

        <button
          v-if="step < 2"
          type="button"
          class="btn btn-primary submit"
          :disabled="!canContinueIdentity"
          @click="continueToCredentials"
        >
          继续
        </button>
        <button
          v-else
          type="submit"
          class="btn btn-primary submit"
          :disabled="!canSubmit"
        >
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

.hero {
  text-align: center;
  animation: rise-in 0.7s cubic-bezier(0.22, 1, 0.36, 1) both;
}
.hero-mark {
  margin: 0 auto 14px;
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
.tabs button:hover { color: var(--c-text); }
.tabs button.active {
  background: var(--c-surface);
  color: var(--c-primary);
  box-shadow: var(--shadow-sm);
}
.tabs button:active { transform: scale(0.98); }

.progress {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 6px;
  margin-top: -2px;
}
.progress span {
  height: 3px;
  border-radius: 99px;
  background: rgba(13, 107, 103, 0.12);
  transition: background 0.25s ease, transform 0.25s ease;
}
.progress span.on { background: rgba(13, 107, 103, 0.35); }
.progress span.done { background: var(--c-primary); }

.mode-hint {
  margin: -2px 0 2px;
  font-size: 12px;
  color: var(--c-text-muted);
  text-align: center;
  min-height: 1.2em;
  transition: opacity 0.2s ease;
}

.cred-block {
  display: flex;
  flex-direction: column;
  gap: 14px;
}

.reveal {
  animation: field-in 0.35s cubic-bezier(0.22, 1, 0.36, 1) both;
  animation-delay: var(--d, 0ms);
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
.field-msg {
  font-size: 12px;
  color: var(--c-danger);
  line-height: 1.4;
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
input[aria-invalid='true'] {
  border-color: rgba(196, 74, 74, 0.55);
}
.hint {
  margin: -6px 0 0;
  font-size: 12px;
  color: var(--c-text-muted);
  line-height: 1.5;
}
.advanced-toggle {
  border: 0;
  background: transparent;
  padding: 0;
  font-size: 12px;
  color: var(--c-primary);
  cursor: pointer;
  font-weight: 600;
}
.advanced-toggle:hover { text-decoration: underline; }
.advanced .field { margin-top: 10px; }

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
  transition: transform 0.15s ease, background 0.2s ease, box-shadow 0.2s ease, opacity 0.2s ease;
  box-shadow: 0 8px 20px rgba(13, 107, 103, 0.22);
}
.submit:hover:not(:disabled) {
  transform: translateY(-1px);
  box-shadow: 0 10px 24px rgba(13, 107, 103, 0.28);
}
.submit:active:not(:disabled) { transform: translateY(0); }
.submit:disabled {
  opacity: 0.45;
  box-shadow: none;
  cursor: not-allowed;
}

@keyframes rise-in {
  from { opacity: 0; transform: translateY(18px); }
  to { opacity: 1; transform: translateY(0); }
}
@keyframes field-in {
  from { opacity: 0; transform: translateY(10px); }
  to { opacity: 1; transform: translateY(0); }
}
@keyframes mark-breathe {
  0%, 100% { transform: translateY(0); }
  50% { transform: translateY(-3px); }
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
  .hero-mark,
  .hero,
  .tagline,
  .login-card,
  .reveal,
  .err {
    animation: none !important;
  }
  .hero,
  .tagline,
  .login-card,
  .reveal {
    opacity: 1;
    transform: none;
  }
}

@media (max-width: 480px) {
  .login-card { padding: 20px 16px 18px; }
}
</style>
