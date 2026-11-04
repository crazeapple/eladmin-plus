import { defineConfig } from '@playwright/test'

// E2E 冒烟打 compose 起的本地生产栈（先 docker compose -f deploy/compose.yaml up -d --wait 起栈再跑，
// 门禁语义 = 非零退出即中止发布）。
// project 划分：setup 生产会话态 → anonymous（公开冒烟与登录链）→ system（持会话态走身份域与监控域页面）。
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
  projects: [
    {
      name: 'setup',
      testMatch: 'auth-setup.ts',
    },
    {
      name: 'anonymous',
      testMatch: /(auth|public)\.spec\.ts$/,
    },
    {
      name: 'system',
      testMatch: /(smoke|system[^/]*|monitor[^/]*)\.spec\.ts$/,
      dependencies: ['setup'],
      use: { storageState: '.auth/state.json' },
    },
  ],
})
