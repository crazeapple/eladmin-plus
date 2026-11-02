<script setup lang="ts">
import { useI18n } from 'vue-i18n'
import { ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { fetchPublicKey, login } from '@/api/auth'
import { encryptPassword } from '@/utils/crypto'
import { useSessionStore } from '@/stores/session'
import SliderCaptcha from '@/components/captcha/SliderCaptcha.vue'

/**
 * 登录页（布局壳之外，偏好主题与语言天然生效）：
 * 公钥页面加载时取 → 滑块通过 → 密码 RSA 加密提交 → 会话入内存 → 跳 redirect；
 * 公钥过期（后端 400 分语义）自动重取公钥重试一次；redirect 只认站内路径。
 */
const { t } = useI18n()
const route = useRoute()
const router = useRouter()
const session = useSessionStore()

const username = ref('')
const password = ref('')
const ticket = ref<string | null>(null)
const submitting = ref(false)

async function onVerified(value: string) {
  ticket.value = value
}

/** redirect 站内校验：仅接受以 / 开头且非 // 的路径（防开放重定向） */
function safeRedirect(): string {
  const target = route.query.redirect
  if (typeof target === 'string' && target.startsWith('/') && !target.startsWith('//')) {
    return target
  }
  return '/home'
}

async function onSubmit() {
  if (!ticket.value) return
  submitting.value = true
  try {
    await submitWithCurrentKey()
  } finally {
    submitting.value = false
  }
}

async function submitWithCurrentKey(retried = false) {
  const { keyId, publicKey } = await fetchPublicKey()
  try {
    const cipher = await encryptPassword(publicKey, password.value)
    const result = await login({
      username: username.value,
      password: cipher,
      keyId,
      ticket: ticket.value!,
    })
    session.save(result.accessToken)
    ElMessage.success(t('login.success'))
    await router.push(safeRedirect())
  } catch (error) {
    const message = error instanceof Error ? error.message : String(error)
    // 公钥过期：后端 400 分语义（消息本地化双语关键词）→ 重取公钥重试一次（换代窗口的在途兜底）
    const expiredMarkers = ['公钥已过期', 'public key expired']
    if (
      !retried &&
      expiredMarkers.some((marker) => message.toLowerCase().includes(marker.toLowerCase()))
    ) {
      return submitWithCurrentKey(true)
    }
    ElMessage.error(message || t('login.failed'))
    ticket.value = null
  }
}
</script>

<template>
  <div class="login" data-test="login-page">
    <el-card class="login__card">
      <h1 class="login__title">{{ t('login.title') }}</h1>
      <el-input
        v-model="username"
        :placeholder="t('login.username')"
        data-test="login-username"
        autocomplete="username"
      />
      <el-input
        v-model="password"
        type="password"
        :placeholder="t('login.password')"
        data-test="login-password"
        autocomplete="current-password"
        show-password
      />
      <SliderCaptcha
        v-if="username && password"
        :hint="t('login.slideHint')"
        :fail-text="t('login.slideFail')"
        @verified="onVerified"
      />
      <el-button
        v-if="ticket"
        type="primary"
        class="login__submit"
        :loading="submitting"
        data-test="login-submit"
        @click="onSubmit"
      >
        {{ t('login.submit') }}
      </el-button>
    </el-card>
  </div>
</template>

<style scoped>
.login {
  display: flex;
  align-items: center;
  justify-content: center;
  min-height: 100vh;
  background: var(--el-bg-color-page);
}

.login__card {
  width: 360px;
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.login__title {
  margin: 0 0 8px;
  font-size: 20px;
  text-align: center;
  color: var(--el-text-color-primary);
}

.login__submit {
  width: 100%;
}
</style>
