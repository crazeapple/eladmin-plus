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
 * 左侧品牌视觉区（渐变底 + 几何装饰 + 产品文案），右侧表单卡，窄屏折叠单栏；
 * 公钥页面加载时取；填表不触发验证码，点「登录」弹滑块层，过验即登录；
 * 密码 RSA 加密提交 → 会话入内存 → 跳 redirect；
 * 公钥过期（后端 400 分语义）自动重取公钥重试一次；redirect 只认站内路径。
 */
const { t } = useI18n()
const route = useRoute()
const router = useRouter()
const session = useSessionStore()

const username = ref('')
const password = ref('')
const submitting = ref(false)
const captchaOpen = ref(false)

/** 点「登录」弹滑块层：表单不全时不开层（校验消息由浏览器表单语义承担，不强弹提示） */
function onSubmit() {
  if (!username.value || !password.value || submitting.value) return
  captchaOpen.value = true
}

/** 过验即登录：持一次性票据直接提交，无需二次操作；未过验关层则回表单态（旧题随 TTL 过期） */
async function onVerified(value: string) {
  captchaOpen.value = false
  submitting.value = true
  try {
    await submitWithCurrentKey(value)
  } finally {
    submitting.value = false
  }
}

/** redirect 站内校验：仅接受以 / 开头且非 // 的路径（防开放重定向） */
function safeRedirect(): string {
  const target = route.query.redirect
  if (typeof target === 'string' && target.startsWith('/') && !target.startsWith('//')) {
    return target
  }
  return '/home'
}

async function submitWithCurrentKey(ticket: string, retried = false) {
  const { keyId, publicKey } = await fetchPublicKey()
  try {
    const cipher = await encryptPassword(publicKey, password.value)
    const result = await login({
      username: username.value,
      password: cipher,
      keyId,
      ticket,
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
      return submitWithCurrentKey(ticket, true)
    }
    ElMessage.error(message || t('login.failed'))
    // 票据一次性：失败后保留表单，重试需重新过滑块（点「登录」重开弹层出新题）
  }
}
</script>

<template>
  <div class="login" data-test="login-page">
    <!-- 品牌视觉区：渐变底 + 程序化几何装饰 + 产品文案（窄屏折叠隐藏） -->
    <aside class="login__brand" data-test="login-brand">
      <div class="login__brand-inner">
        <div class="login__wordmark">eladmin-plus</div>
        <h2 class="login__slogan">{{ t('login.slogan') }}</h2>
        <p class="login__slogan-sub">{{ t('login.sloganSub') }}</p>
      </div>
      <div class="login__decor" aria-hidden="true">
        <span class="login__decor-ring"></span>
        <span class="login__decor-dot login__decor-dot--1"></span>
        <span class="login__decor-dot login__decor-dot--2"></span>
        <span class="login__decor-dot login__decor-dot--3"></span>
        <span class="login__decor-square"></span>
      </div>
    </aside>

    <!-- 表单卡 -->
    <main class="login__panel">
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
        <el-button
          type="primary"
          class="login__submit"
          :loading="submitting"
          data-test="login-submit"
          @click="onSubmit"
        >
          {{ t('login.submit') }}
        </el-button>
      </el-card>
    </main>

    <!-- 滑块验证弹层：过验即登录；关层重开随组件重挂载重新出题 -->
    <el-dialog
      v-model="captchaOpen"
      :title="t('login.captchaTitle')"
      width="360px"
      align-center
      destroy-on-close
      data-test="login-captcha-dialog"
    >
      <SliderCaptcha
        :hint="t('login.slideHint')"
        :fail-text="t('login.slideFail')"
        :retry-text="t('login.captchaRetry')"
        @verified="onVerified"
      />
    </el-dialog>
  </div>
</template>

<style scoped lang="scss">
.login {
  display: flex;
  min-height: 100vh;
  background-color: var(--ea-page-bg, var(--el-bg-color-page, #f2f3f5));
}

.login__brand {
  position: relative;
  display: flex;
  /* 分栏对称基数 50:50：与表单区等宽，右区空旷感由背景图案填充而非占比压制 */
  flex: 50;
  align-items: center;
  justify-content: center;
  /* 品牌渐变底：靛蓝系对角渐变 */
  background: var(--ea-gradient-brand, var(--el-color-primary));
  overflow: hidden;
}

.login__brand-inner {
  position: relative;
  z-index: 1;
  max-width: 420px;
  padding: 48px;
  color: #fff;
}

.login__wordmark {
  margin-bottom: 32px;
  font-size: 20px;
  font-weight: 700;
  letter-spacing: 1px;
}

.login__slogan {
  margin: 0 0 12px;
  font-size: 30px;
  font-weight: 700;
  line-height: 1.35;
}

.login__slogan-sub {
  margin: 0;
  font-size: 14px;
  line-height: 1.8;
  color: rgb(255 255 255 / 78%);
}

/* 几何装饰：程序化形状（圆环 / 圆点 / 圆角块），零图片资产 */
.login__decor {
  position: absolute;
  inset: 0;

  span {
    position: absolute;
    display: block;
  }
}

.login__decor-ring {
  top: 8%;
  right: 12%;
  width: 180px;
  height: 180px;
  border: 2px solid rgb(255 255 255 / 22%);
  border-radius: 50%;
}

.login__decor-square {
  bottom: 12%;
  left: 10%;
  width: 120px;
  height: 120px;
  background: rgb(255 255 255 / 10%);
  border-radius: var(--ea-radius-lg, 12px);
  transform: rotate(18deg);
}

.login__decor-dot {
  border-radius: 50%;
  background: rgb(255 255 255 / 28%);

  &--1 {
    top: 30%;
    right: 28%;
    width: 16px;
    height: 16px;
  }

  &--2 {
    bottom: 28%;
    right: 14%;
    width: 10px;
    height: 10px;
  }

  &--3 {
    top: 16%;
    left: 14%;
    width: 12px;
    height: 12px;
  }
}

.login__panel {
  display: flex;
  /* 分栏对称基数 50:50（与品牌区互为等宽） */
  flex: 50;
  align-items: center;
  justify-content: center;
  padding: 24px;
  /* 右区极淡几何图案底：品牌主色点阵 + 细网格程序化平铺（透明度取 4%~7% 档，零图片资产） */
  background-image:
    radial-gradient(
      circle,
      color-mix(in srgb, var(--ea-primary, #5d87ff) 7%, transparent) 1.5px,
      transparent 1.5px
    ),
    linear-gradient(
      color-mix(in srgb, var(--ea-primary, #5d87ff) 4%, transparent) 1px,
      transparent 1px
    ),
    linear-gradient(
      90deg,
      color-mix(in srgb, var(--ea-primary, #5d87ff) 4%, transparent) 1px,
      transparent 1px
    );
  background-size:
    24px 24px,
    24px 24px,
    24px 24px;
}

/* 暗色底上图案略提亮仍守极淡档（4%~8% 上限档），色值随品牌主色 token 双态 */
html.dark .login__panel {
  background-image:
    radial-gradient(
      circle,
      color-mix(in srgb, var(--ea-primary, #5d87ff) 8%, transparent) 1.5px,
      transparent 1.5px
    ),
    linear-gradient(
      color-mix(in srgb, var(--ea-primary, #5d87ff) 5%, transparent) 1px,
      transparent 1px
    ),
    linear-gradient(
      90deg,
      color-mix(in srgb, var(--ea-primary, #5d87ff) 5%, transparent) 1px,
      transparent 1px
    );
  background-size:
    24px 24px,
    24px 24px,
    24px 24px;
}

.login__card {
  width: 400px;
  display: flex;
  flex-direction: column;
  gap: 20px;
  /* 卡内边距随 EP 卡片 token 提到 28px（加宽加密改善空旷感） */
  --el-card-padding: 28px;
  /* 表单卡：品牌卡片底 + 圆角与重阴影层次（亮暗双态取 token 值） */
  background-color: var(--ea-card-bg, #fff);
  border: 1px solid var(--ea-card-border, var(--el-border-color-lighter, #e4e7ed));
  border-radius: var(--ea-radius-lg, 12px);
  box-shadow: var(--ea-shadow-3, var(--el-box-shadow-light));
}

.login__title {
  margin: 0;
  font-size: 20px;
  text-align: center;
  color: var(--el-text-color-primary);
}

.login__submit {
  width: 100%;
}

/* 窄屏折叠单栏：品牌区让位，表单卡满幅 */
@media (max-width: 768px) {
  .login__brand {
    display: none;
  }

  .login__panel {
    padding: 16px;
  }

  .login__card {
    width: 100%;
    max-width: 380px;
  }
}
</style>
