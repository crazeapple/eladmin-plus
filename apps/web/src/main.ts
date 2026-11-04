import { QueryClient, VueQueryPlugin } from '@tanstack/vue-query'
import { createPinia } from 'pinia'
import { createApp } from 'vue'

import App from './App.vue'
import { bindLocaleHeader } from './api/locale'
import { onAuthBroadcast } from './api/auth-sync'
import { setupDictSource } from './api/dicts'
import { buildI18n } from './i18n'
import router from './router'
import { useMenusStore } from './stores/menus'
import { usePreferencesStore } from './stores/preferences'
import { useSessionStore } from './stores/session'
import { useTagsViewStore } from './stores/tags-view'
// EP 暗色变量集（html.dark 时生效）
import 'element-plus/theme-chalk/dark/css-vars.css'
// API 式调用的 EP 组件（ElMessage 等，不经模板解析）样式需集中显式导入，否则渲染无样式（toast 挤出视口）
import 'element-plus/es/components/message/style/css'
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

// 其他 tab 的会话广播：刷新结果同步内存令牌；登出全清并踢回登录页（A tab 退出 B tab 立即生效）
onAuthBroadcast((message) => {
  const store = useSessionStore()
  if (message.type === 'refreshed') {
    store.save(message.accessToken)
  }
  if (message.type === 'logout') {
    store.reset()
    useMenusStore().reset()
    useTagsViewStore().closeAll()
    if (router.currentRoute.value.path !== '/login') {
      router.push('/login')
    }
  }
})

// i18n 初值取偏好解析语言（首访随系统检测已在偏好 store 完成）
const i18n = buildI18n(usePreferencesStore().resolvedLocale)
app.use(i18n)

// 请求默认头随偏好语言联动（后端消息本地化）
bindLocaleHeader()

// 字典消费走真实数据源：按名取端点 + label 按 locale 解析（useDict 缓存语义不变）
setupDictSource()

app.use(VueQueryPlugin, { queryClient })
app.use(router)
app.mount('#app')
