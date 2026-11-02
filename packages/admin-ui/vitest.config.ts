import vue from '@vitejs/plugin-vue'
import { defineConfig } from 'vitest/config'

// 组件层独立测试环境：happy-dom + SFC 编译，与 apps/web 同栈
export default defineConfig({
  plugins: [vue()],
  test: {
    environment: 'happy-dom',
  },
})
