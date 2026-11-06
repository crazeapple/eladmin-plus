import { VueQueryPlugin } from '@tanstack/vue-query'
import { cleanup, fireEvent, render, screen, waitFor, within } from '@testing-library/vue'
import { createPinia } from 'pinia'
import { createMemoryHistory, createRouter, type Router } from 'vue-router'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'

import App from '@/App.vue'
import { buildI18n } from '@/i18n'
import { installAuthGuard, routes } from '@/router'

beforeEach(() => {
  // 钉住系统语言，偏好首访检测稳定取 zh-CN（文案断言走中文语言包）
  vi.spyOn(window.navigator, 'language', 'get').mockReturnValue('zh-CN')
})

afterEach(() => {
  cleanup()
  localStorage.clear()
  vi.restoreAllMocks()
})

async function renderApp(path: string): Promise<Router> {
  const router = createRouter({ history: createMemoryHistory(), routes })
  await router.push(path)
  await router.isReady()
  render(App, { global: { plugins: [createPinia(), router, buildI18n('zh-CN'), VueQueryPlugin] } })
  return router
}

function tagsArea() {
  const area = document.querySelector('[data-test="tags-view"]')!
  return within(area as HTMLElement)
}

/** 等一拍，让右键菜单动作里的异步 router.push 完成 */
const tick = () => new Promise((resolve) => setTimeout(resolve, 0))

function tagByName(title: string) {
  const tag = tagsArea().getByText(title).closest('.el-tag')
  return tag as HTMLElement
}

function menu() {
  const el = document.querySelector('[data-test="tags-menu"]')
  return within(el as HTMLElement)
}

describe('TagsView', () => {
  it('页签渲染：首页固定不可关闭，其他页签可关闭', async () => {
    await renderApp('/demo/cached')
    expect(tagByName('首页').querySelector('.el-tag__close')).toBeNull()
    expect(tagByName('缓存').querySelector('.el-tag__close')).not.toBeNull()
  })

  it('右键菜单：关闭其他保留首页与目标，关闭全部仅剩首页', async () => {
    const router = await renderApp('/home')
    await router.push('/demo/cached')
    await router.push('/demo/no-cache')
    expect(tagByName('不缓存')).toBeTruthy()

    await fireEvent.contextMenu(tagByName('缓存'))
    await fireEvent.click(menu().getByText('关闭其他'))
    await tick()
    expect(tagsArea().queryByText('不缓存')).toBeNull()
    expect(tagsArea().getByText('缓存')).toBeTruthy()

    await fireEvent.contextMenu(tagByName('缓存'))
    await fireEvent.click(menu().getByText('关闭全部'))
    await tick()
    expect(tagsArea().queryByText('缓存')).toBeNull()
    expect(tagsArea().getByText('首页')).toBeTruthy()
    expect(router.currentRoute.value.path).toBe('/home')
  })

  it('关闭当前：非首页可关，活跃被关跳最后存活页签', async () => {
    const router = await renderApp('/demo/cached')
    await router.push('/demo/no-cache')
    expect(tagByName('不缓存')).toBeTruthy()

    await fireEvent.contextMenu(tagByName('不缓存'))
    await fireEvent.click(menu().getByText('关闭当前'))
    await tick()
    expect(tagsArea().queryByText('不缓存')).toBeNull()
    expect(router.currentRoute.value.path).toBe('/demo/cached')
  })

  it('keep-alive：缓存页往返保留输入状态', async () => {
    const router = await renderApp('/demo/cached')
    const input = (await screen.findByPlaceholderText(
      '输入点什么，然后切走再切回',
    )) as HTMLInputElement
    await fireEvent.input(input, { target: { value: 'hello' } })
    expect(input.value).toBe('hello')

    await router.push('/home')
    await waitFor(() => expect(document.querySelector('[data-test="home-dashboard"]')).toBeTruthy())
    await router.push('/demo/cached')
    const again = (await screen.findByPlaceholderText(
      '输入点什么，然后切走再切回',
    )) as HTMLInputElement
    expect(again.value).toBe('hello')
  })

  it('noCache：不入缓存，往返即清空', async () => {
    const router = await renderApp('/demo/no-cache')
    const input = (await screen.findByPlaceholderText(
      '输入点什么，切走再切回会清空',
    )) as HTMLInputElement
    await fireEvent.input(input, { target: { value: 'temp' } })

    await router.push('/home')
    await waitFor(() => expect(document.querySelector('[data-test="home-dashboard"]')).toBeTruthy())
    await router.push('/demo/no-cache')
    const again = (await screen.findByPlaceholderText(
      '输入点什么，切走再切回会清空',
    )) as HTMLInputElement
    expect(again.value).toBe('')
  })
})

describe('页签缓存动态接线（守卫注册路由的 noCache 语义）', () => {
  beforeEach(() => {
    localStorage.setItem('auth:logged-in', '1')
  })

  /** 守卫版挂载：走真实守卫链（恢复会话 + 拉菜单 + 注册动态路由） */
  async function renderAppWithGuard(
    path: string,
  ): Promise<{ router: Router; pinia: ReturnType<typeof createPinia> }> {
    const pinia = createPinia()
    const router = createRouter({ history: createMemoryHistory(), routes })
    installAuthGuard(router)
    await router.push(path)
    await router.isReady()
    render(App, { global: { plugins: [pinia, router, buildI18n('zh-CN'), VueQueryPlugin] } })
    await waitFor(() => expect(document.querySelector('[data-test="home-dashboard"]')).toBeTruthy())
    return { router, pinia }
  }

  it('缓存页（demo/cached）进缓存集，noCache 页（demo/no-cache）不入', async () => {
    const { router, pinia } = await renderAppWithGuard('/home')

    await router.push('/demo/cached')
    await waitFor(() => expect(router.currentRoute.value.path).toBe('/demo/cached'))
    await router.push('/demo/no-cache')
    await waitFor(() => expect(router.currentRoute.value.path).toBe('/demo/no-cache'))

    const tags = pinia._s.get('tags-view')!
    const cached = tags.cachedViews
    expect(cached).toContain('Home')
    expect(cached).toContain('DemoCached')
    expect(cached).not.toContain('DemoNoCache')
    // 页签三条都在（含固定首页）
    expect(tags.visitedViews.map((view: { path: string }) => view.path)).toEqual([
      '/home',
      '/demo/cached',
      '/demo/no-cache',
    ])
  })

  it('登录后导航的动态路由页签标题取 i18n key', async () => {
    const { router, pinia } = await renderAppWithGuard('/home')
    await router.push('/demo/cached')
    await waitFor(() => expect(router.currentRoute.value.path).toBe('/demo/cached'))

    const tags = pinia._s.get('tags-view')!
    const cachedTag = tags.visitedViews.find(
      (view: { path: string }) => view.path === '/demo/cached',
    )
    expect(cachedTag?.title).toBe('routes.demoCached')
  })
})
