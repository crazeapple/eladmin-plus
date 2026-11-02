import { beforeEach, afterEach, describe, expect, it, vi } from 'vitest'
import { cleanup, render, waitFor } from '@testing-library/vue'
import { createPinia } from 'pinia'
import { VueQueryPlugin } from '@tanstack/vue-query'
import { createMemoryHistory, createRouter } from 'vue-router'
import App from '@/App.vue'
import { buildI18n } from '@/i18n'
import { installAuthGuard, routes } from '@/router'
import { listViewKeys, loadView } from '../views'
import { setActivePinia } from 'pinia'

/** 平台守卫路由：App 全挂，走真实守卫链 */
async function renderAt(path: string) {
  const pinia = createPinia()
  setActivePinia(pinia)
  const router = createRouter({ history: createMemoryHistory(), routes })
  installAuthGuard(router)
  await router.push(path)
  await router.isReady()
  const view = render(App, {
    global: { plugins: [pinia, router, buildI18n('zh-CN'), VueQueryPlugin] },
  })
  return { router, view, pinia }
}

beforeEach(() => {
  localStorage.setItem('auth:logged-in', '1')
})

afterEach(() => {
  cleanup()
  localStorage.clear()
  vi.restoreAllMocks()
})

describe('动态路由与守卫', () => {
  it('无会话导航带 redirect 回登录', async () => {
    // 先覆盖全局桩再导航：refresh 拒绝（会话不可恢复）
    vi.stubGlobal(
      'fetch',
      vi.fn(
        async () =>
          new Response(JSON.stringify({ code: 401, message: '', data: null }), { status: 401 }),
      ),
    )
    const { router, pinia } = await renderAt('/home')

    await waitFor(() => expect(router.currentRoute.value.path).toBe('/login'))
    expect(router.currentRoute.value.query.redirect).toBe('/home')
    expect(pinia._s.get('session')?.accessToken).toBeNull()
  })

  it('F5 静默恢复后动态路由注册完成，布局首页可达', async () => {
    const { router, pinia } = await renderAt('/home')

    // 守卫经 refresh 恢复（setup-token）→ 拉菜单 → 注册 → 放行
    await waitFor(() => expect(router.currentRoute.value.path).toBe('/home'))
    await waitFor(() => expect(document.querySelector('[data-test="home-dashboard"]')).toBeTruthy())
    // 会话令牌为恢复所得（与守卫同 pinia）
    expect(pinia._s.get('session')?.accessToken).toBe('setup-token')
  })

  it('动态路由 name 与组件 name 一致（keep-alive 前提）', async () => {
    const { router } = await renderAt('/home')
    await waitFor(() => expect(router.currentRoute.value.path).toBe('/home'))

    // 后端菜单三条 → 路由 name 即组件 defineOptions name
    // （demo/cached 等 key 与静态路由同名：静态路由已在则复用，keep-alive 前提成立）
    for (const key of ['home/index', 'demo/cached', 'demo/no-cache']) {
      const view = await loadView(key)
      expect(view).not.toBeNull()
      const matched = router.getRoutes().find((route) => route.name === view!.name)
      expect(matched).toBeTruthy()
    }
  })

  it('映射内全部视图可加载且具备组件 name（配错编译期暴露）', async () => {
    const keys = listViewKeys()
    expect(keys.length).toBeGreaterThanOrEqual(3)
    for (const key of keys) {
      const view = await loadView(key)
      expect(view, `视图 ${key} 缺 name 或不可加载`).not.toBeNull()
    }
    // 登录页与 404 不参与动态注册
    expect(await loadView('login/index')).toBeNull()
    expect(await loadView('error/404')).toBeNull()
    // 目录式与文件式两种 key 形态都能命中
    expect(await loadView('demo/cached')).not.toBeNull()
  })

  it('菜单外的路径由 404 兜底', async () => {
    const { router } = await renderAt('/no-such-menu-page')
    // 守卫恢复会话并注册菜单后，未注册路径仍落 404
    await waitFor(() => expect(router.currentRoute.value.name).toBe('NotFound'))
  })

  it('菜单拉取失败清态回登录', async () => {
    // 先覆盖全局桩再导航：refresh 放行、/auth/me 拒绝
    vi.stubGlobal(
      'fetch',
      vi.fn(async (input: RequestInfo | URL) => {
        const url = input instanceof Request ? input.url : String(input)
        const status = url.includes('/auth/refresh') ? 200 : 401
        const body =
          status === 200
            ? { code: 200, message: '', data: { accessToken: 'route-token', expiresIn: 7200 } }
            : { code: 401, message: '没有访问权限', data: null }
        return new Response(JSON.stringify(body), {
          status,
          headers: { 'Content-Type': 'application/json' },
        })
      }),
    )
    const { router, pinia } = await renderAt('/home')

    await waitFor(() => expect(router.currentRoute.value.path).toBe('/login'))
    expect(router.currentRoute.value.query.redirect).toBe('/home')
    // 会话与意图一起清
    expect(pinia._s.get('session')?.accessToken).toBeNull()
    expect(localStorage.getItem('auth:logged-in')).toBeNull()
  })
})
