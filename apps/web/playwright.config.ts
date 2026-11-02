import { defineConfig } from '@playwright/test'

// E2E 冒烟打 compose 起的本地生产栈（web 容器宿主端口，默认 80）
// 先 `docker compose -f deploy/compose.yaml up -d --wait` 起栈再跑；门禁语义 = 非零退出即中止发布
export default defineConfig({
  testDir: 'e2e',
  // 冒烟是发布门禁：重试会掩盖问题，一次都不重试
  retries: 0,
  reporter: 'list',
  use: {
    baseURL: process.env.E2E_BASE_URL ?? 'http://localhost:80',
    locale: 'zh-CN',
    timezoneId: 'Asia/Shanghai',
  },
})
