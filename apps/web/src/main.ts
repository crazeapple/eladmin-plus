import { QueryClient, VueQueryPlugin } from '@tanstack/vue-query'
import { createPinia } from 'pinia'
import { createApp } from 'vue'

import App from './App.vue'
import { bindLocaleHeader } from './api/locale'
import { onAuthBroadcast } from './api/auth-sync'
import { buildI18n } from './i18n'
import router from './router'
import { usePreferencesStore } from './stores/preferences'
import { useSessionStore } from './stores/session'
// EP 暗色变量集（html.dark 时生效）
import 'element-plus/theme-chalk/dark/css-vars.css'
import '@/styles/index.scss'

// 后台管理数据实时性要求低：默认 30 秒内直接用缓存，失败只重试一次
const queryClient = new QueryClient({
  defaultOptions: {
    queries: { staleTime: 30_000, retry: 1 },
  },
})

const app = createApp(App)
const pinia = createPinia()
app.use(pinia)

// 其他 tab 的会话广播：刷新结果同步内存令牌，登出全清
onAuthBroadcast((message) => {
  const store = useSessionStore()
  if (message.type === 'refreshed') store.save(message.accessToken)
  if (message.type === 'logout') store.reset()
})

// i18n 初值取偏好解析语言（首访随系统检测已在偏好 store 完成）
const i18n = buildI18n(usePreferencesStore().resolvedLocale)
app.use(i18n)

// 请求默认头随偏好语言联动（后端消息本地化）
bindLocaleHeader()

app.use(VueQueryPlugin, { queryClient })
app.use(router)
app.mount('#app')
