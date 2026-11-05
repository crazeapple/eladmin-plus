import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { cleanup, render, waitFor } from '@testing-library/vue'
import { fireEvent } from '@testing-library/dom'
import { createPinia, setActivePinia } from 'pinia'
import { VueQueryPlugin } from '@tanstack/vue-query'
import { createMemoryHistory, createRouter } from 'vue-router'
import App from '@/App.vue'
import { buildI18n } from '@/i18n'
import { installAuthGuard, routes } from '@/router'

// 跨 tab 协调桩：登出广播 spy 供退出用例断言；其余签名保形
const authSync = vi.hoisted(() => ({
  broadcastLogout: vi.fn(),
  broadcastRefreshed: vi.fn(),
  onAuthBroadcast: vi.fn(() => () => {}),
  withRefreshLock: vi.fn((fn: () => Promise<unknown>) => fn()),
}))
vi.mock('@/api/auth-sync', () => authSync)

beforeEach(() => {
  // 钉住系统语言，文案断言走中文语言包
  vi.spyOn(window.navigator, 'language', 'get').mockReturnValue('zh-CN')
  localStorage.setItem('auth:logged-in', '1')
})

afterEach(() => {
  cleanup()
  localStorage.clear()
  vi.restoreAllMocks()
})

function byTest(id: string): HTMLElement {
  const element = document.querySelector(`[data-test="${id}"]`)
  if (!element) {
    throw new Error(`element not found: [data-test="${id}"]`)
  }
  return element as HTMLElement
}

/** App 全挂（守卫恢复会话并拉菜单），返回各 store */
async function renderApp() {
  const pinia = createPinia()
  setActivePinia(pinia)
  const router = createRouter({ history: createMemoryHistory(), routes })
  installAuthGuard(router)
  await router.push('/home')
  await router.isReady()
  render(App, { global: { plugins: [pinia, router, buildI18n('zh-CN'), VueQueryPlugin] } })
  await waitFor(() => expect(document.querySelector('[data-test="home-dashboard"]')).toBeTruthy())
  return {
    router,
    session: pinia._s.get('session')!,
    menus: pinia._s.get('menus')!,
    tags: pinia._s.get('tags-view')!,
  }
}

describe('顶栏用户区', () => {
  it('头像显示用户图标与登录用户名，下拉含个人中心与退出（退出前有分隔线）', async () => {
    const { router } = await renderApp()
    // 菜单拉取后用户视图在 store（svg 图标无文本，textContent 即用户名）
    await waitFor(() => expect(byTest('user-avatar').textContent?.trim()).toBe('admin'))
    expect(byTest('user-avatar').querySelector('svg')).toBeTruthy()

    await fireEvent.click(byTest('user-avatar'))
    const dropdown = await waitFor(() => {
      const menu = document.querySelector('.el-dropdown-menu')
      expect(menu).toBeTruthy()
      return menu as HTMLElement
    })
    const profileItem = dropdown.querySelector('[data-test="user-profile"]') as HTMLElement
    expect(profileItem.textContent).toContain('个人中心')
    expect(profileItem.className).not.toContain('is-disabled')
    expect(dropdown.querySelector('[data-test="user-logout"]')?.textContent).toContain('退出登录')
    expect(dropdown.querySelector('.el-dropdown-menu__item--divided')).toBeTruthy()

    // 个人中心项可点：跳 /account 固定路由页渲染
    await fireEvent.click(profileItem)
    await waitFor(() => expect(router.currentRoute.value.path).toBe('/account'))
    await waitFor(() => expect(document.querySelector('[data-test="account-page"]')).toBeTruthy())
  })

  it('退出登录调后端、本地全清并回登录页', async () => {
    const logoutCalls: string[] = []
    vi.stubGlobal(
      'fetch',
      vi.fn(async (input: RequestInfo | URL) => {
        const url = input instanceof Request ? input.url : String(input)
        if (url.includes('/auth/logout')) {
          logoutCalls.push(url)
          return new Response(JSON.stringify({ code: 200, message: '', data: null }), {
            status: 200,
          })
        }
        // 其余端点走 setup 同款应答（refresh / auth/me）
        const json = (body: unknown) =>
          new Response(JSON.stringify(body), {
            status: 200,
            headers: { 'Content-Type': 'application/json' },
          })
        if (url.includes('/auth/refresh')) {
          return json({
            code: 200,
            message: '',
            data: { accessToken: 'setup-token', expiresIn: 7200 },
          })
        }
        return json({
          code: 200,
          message: '',
          data: {
            user: { id: 1, username: 'admin', nickName: '系统管理员' },
            roles: ['ROLE_ROOT'],
            permissions: [],
            menus: [],
          },
        })
      }),
    )

    const { router, session, menus, tags } = await renderApp()
    // 进入一个动态路由制造页签
    await router.push('/demo/cached')
    await waitFor(() => expect(tags.visitedViews.length).toBeGreaterThan(1))
    expect(session.accessToken).toBeTruthy()

    await waitFor(() => expect(byTest('user-avatar').textContent?.trim()).toBe('admin'))
    await fireEvent.click(byTest('user-avatar'))
    const dropdown = await waitFor(() => {
      const menu = document.querySelector('.el-dropdown-menu')
      expect(menu).toBeTruthy()
      return menu as HTMLElement
    })
    await fireEvent.click(dropdown.querySelector('[data-test="user-logout"]') as HTMLElement)

    await waitFor(() => expect(router.currentRoute.value.path).toBe('/login'))
    // 本地全清
    expect(session.accessToken).toBeNull()
    expect(menus.user).toBeNull()
    expect(menus.menus).toEqual([])
    expect(tags.visitedViews).toHaveLength(1)
    expect(tags.visitedViews[0]!.path).toBe('/home')
    // 后端登出端点被调；其他 tab 收到登出广播
    expect(logoutCalls).toHaveLength(1)
    expect(authSync.broadcastLogout).toHaveBeenCalled()
  })

  it('登出后端异常也继续清本地回登录', async () => {
    vi.stubGlobal(
      'fetch',
      vi.fn(async (input: RequestInfo | URL) => {
        const url = input instanceof Request ? input.url : String(input)
        if (url.includes('/auth/logout')) {
          return new Response('server down', { status: 500 })
        }
        const json = (body: unknown) =>
          new Response(JSON.stringify(body), {
            status: 200,
            headers: { 'Content-Type': 'application/json' },
          })
        if (url.includes('/auth/refresh')) {
          return json({
            code: 200,
            message: '',
            data: { accessToken: 'setup-token', expiresIn: 7200 },
          })
        }
        return json({
          code: 200,
          message: '',
          data: {
            user: { id: 1, username: 'admin', nickName: '系统管理员' },
            roles: ['ROLE_ROOT'],
            permissions: [],
            menus: [],
          },
        })
      }),
    )

    const { router, session } = await renderApp()
    await waitFor(() => expect(byTest('user-avatar').textContent?.trim()).toBe('admin'))
    await fireEvent.click(byTest('user-avatar'))
    const dropdown = await waitFor(() => {
      const menu = document.querySelector('.el-dropdown-menu')
      expect(menu).toBeTruthy()
      return menu as HTMLElement
    })
    await fireEvent.click(dropdown.querySelector('[data-test="user-logout"]') as HTMLElement)

    await waitFor(() => expect(router.currentRoute.value.path).toBe('/login'))
    expect(session.accessToken).toBeNull()
  })
})
