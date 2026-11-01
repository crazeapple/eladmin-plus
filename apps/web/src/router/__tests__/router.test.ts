import { cleanup, render, screen } from '@testing-library/vue'
import { createMemoryHistory, createRouter, type RouteRecordRaw } from 'vue-router'
import { afterEach, describe, expect, it } from 'vitest'

import App from '@/App.vue'
import { routes } from '@/router'

afterEach(cleanup)

async function renderAt(path: string) {
  const router = createRouter({ history: createMemoryHistory(), routes })
  await router.push(path)
  await router.isReady()
  return render(App, { global: { plugins: [router] } })
}

describe('router', () => {
  it('根路径进入布局与首页占位', async () => {
    await renderAt('/')
    expect(await screen.findByText('仪表盘随后续提案接入，当前为占位视图。')).toBeTruthy()
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
