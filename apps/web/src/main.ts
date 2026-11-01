import { QueryClient, VueQueryPlugin } from '@tanstack/vue-query'
import { createPinia } from 'pinia'
import { createApp } from 'vue'

import App from './App.vue'
import router from './router'
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

app.use(createPinia())
app.use(VueQueryPlugin, { queryClient })
app.use(router)
app.mount('#app')
