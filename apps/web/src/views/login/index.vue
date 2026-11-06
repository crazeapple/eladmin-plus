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
 * 公钥页面加载时取；填表不触发验证码，点「登录」卡内展开滑块区块，过验即登录；
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

/** 点「登录」展开滑块区块：表单不全时不展开（校验消息由浏览器表单语义承担，不强弹提示） */
function onSubmit() {
  if (!username.value || !password.value || submitting.value || captchaOpen.value) return
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
    <!-- 中央：标语 + 登录卡 -->
    <main class="login__main">
      <h2 class="login__slogan">{{ t('login.slogan') }}</h2>
      <p class="login__slogan-sub">{{ t('login.sloganSub') }}</p>
      <el-card class="login__card">
        <!-- 品牌标识：卡内顶部，颜色随文字主色亮暗自适应 -->
        <div class="login__mark" data-test="login-brand">eladmin-plus</div>
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
          :disabled="captchaOpen"
          data-test="login-submit"
          @click="onSubmit"
        >
          {{ t('login.submit') }}
        </el-button>
        <!-- 滑块验证内联展开区：过验即登录；收起销毁重挂，重开重新出题（等价原弹层 destroy-on-close） -->
        <Transition name="login-captcha">
          <div v-if="captchaOpen" class="login__captcha" data-test="login-captcha-dialog">
            <div class="login__captcha-head">
              <span class="login__captcha-title">{{ t('login.captchaTitle') }}</span>
              <button
                type="button"
                class="login__captcha-close"
                data-test="login-captcha-close"
                @click="captchaOpen = false"
              >
                {{ t('login.captchaClose') }}
              </button>
            </div>
            <SliderCaptcha
              :hint="t('login.slideHint')"
              :fail-text="t('login.slideFail')"
              :retry-text="t('login.captchaRetry')"
              @verified="onVerified"
            />
          </div>
        </Transition>
      </el-card>
    </main>
  </div>
</template>

<style scoped lang="scss">
.login {
  display: flex;
  min-height: 100vh;
  /* 粒子面料背景图铺满整页：左右区全透明叠加，文字靠图面暗区与文字阴影保可读 */
  background-image: url('../../assets/login-fabric.jpg');
  background-size: cover;
  background-position: center;
}

.login__mark {
  margin-bottom: 4px;
  font-size: 18px;
  font-weight: 700;
  letter-spacing: 1px;
  text-align: center;
  color: var(--el-text-color-primary);
}

.login__main {
  display: flex;
  flex: 1;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 20px;
  padding: 24px;
}

.login__slogan {
  margin: 0;
  font-size: 30px;
  font-weight: 700;
  line-height: 1.35;
  color: #fff;
  text-align: center;
  text-shadow: 0 2px 16px rgb(0 0 0 / 50%);
}

.login__slogan-sub {
  margin: -12px 0 0;
  font-size: 14px;
  line-height: 1.8;
  color: rgb(255 255 255 / 78%);
  text-align: center;
  text-shadow: 0 1px 10px rgb(0 0 0 / 45%);
}

.login__card {
  width: 400px;
  /* 卡浮于装饰层之上 */
  position: relative;
  z-index: 1;
  /* 卡内边距随 EP 卡片 token 提到 28px（加宽加密改善空旷感） */
  --el-card-padding: 28px;
  /* 表单卡：品牌卡片底 + 圆角与重阴影层次（亮暗双态取 token 值） */
  background-color: var(--ea-card-bg, #fff);
  border: 1px solid var(--ea-card-border, var(--el-border-color-lighter, #e4e7ed));
  border-radius: var(--ea-radius-lg, 12px);
  box-shadow: var(--ea-shadow-3, var(--el-box-shadow-light));
}

/* 表单元素垂直间距落在 EP 卡片内容区（title/input/button 都在 body 层，
   卡根设 flex gap 只会作用于唯一子元素、不生效） */
.login__card :deep(.el-card__body) {
  display: flex;
  flex-direction: column;
  gap: 20px;
}

.login__submit {
  width: 100%;
}

/* 滑块内联展开区：随卡内容区同宽（flex 子项拉伸），与输入框天然左右缘对齐 */
.login__captcha-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 10px;
}

.login__captcha-title {
  font-size: 13px;
  font-weight: 600;
  color: var(--el-text-color-secondary, #909399);
}

.login__captcha-close {
  padding: 2px 8px;
  font-size: 12px;
  color: var(--el-text-color-secondary, #909399);
  cursor: pointer;
  background: none;
  border: none;

  &:hover {
    color: var(--el-text-color-primary, #303133);
  }
}

/* 展开 / 收起过渡：淡入淡出带轻微位移，收起即销毁（v-if 重挂语义） */
.login-captcha-enter-active,
.login-captcha-leave-active {
  transition:
    opacity 0.2s ease,
    transform 0.2s ease;
}

.login-captcha-enter-from,
.login-captcha-leave-to {
  opacity: 0;
  transform: translateY(-6px);
}

/* 窄屏：品牌标识缩距，卡满幅 */
@media (max-width: 768px) {
  .login__mark {
    padding: 20px 24px;
  }

  .login__main {
    padding: 16px;
  }

  .login__card {
    width: 100%;
    max-width: 400px;
  }
}
</style>
