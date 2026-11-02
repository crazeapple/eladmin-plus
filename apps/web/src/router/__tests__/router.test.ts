import { cleanup, render, screen, waitFor } from '@testing-library/vue'
import { createPinia } from 'pinia'
import { createMemoryHistory, createRouter, type RouteRecordRaw } from 'vue-router'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'

import App from '@/App.vue'
import { buildI18n } from '@/i18n'
import { routes } from '@/router'

beforeEach(() => {
  // 钉住系统语言，偏好首访检测稳定取 zh-CN（文案断言走中文语言包）
  vi.spyOn(window.navigator, 'language', 'get').mockReturnValue('zh-CN')
})

afterEach(() => {
  cleanup()
  localStorage.clear()
  vi.restoreAllMocks()
})

async function renderAt(path: string) {
  const router = createRouter({ history: createMemoryHistory(), routes })
  await router.push(path)
  await router.isReady()
  return render(App, {
    global: { plugins: [createPinia(), router, buildI18n('zh-CN')] },
  })
}

describe('router', () => {
  it('根路径进入布局与首页仪表盘', async () => {
    await renderAt('/')
    await waitFor(() => expect(document.querySelector('[data-test="home-dashboard"]')).toBeTruthy())
  })

  it('未知路由进入 404', async () => {
    await renderAt('/no-such-page')
    expect(await screen.findByText('404')).toBeTruthy()
  })

  it('静态路由 name 与页面组件 name 一致（keep-alive 前提）', async () => {
    const layoutChildren = (routes[0].children ?? []) as RouteRecordRaw[]
    for (const route of layoutChildren) {
      const mod = (await (route.component as () => Promise<unknown>)()) as {
        default: { name?: string }
      }
      expect(mod.default.name).toBe(route.name)
    }
    const notFound = routes[routes.length - 1]
    const mod = (await (notFound.component as () => Promise<unknown>)()) as {
      default: { name?: string }
    }
    expect(mod.default.name).toBe(notFound.name)
  })
})
