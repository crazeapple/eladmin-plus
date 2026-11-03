import { useMenusStore } from '@/stores/menus'
import { VueQueryPlugin } from '@tanstack/vue-query'
import { cleanup, render, within } from '@testing-library/vue'
import { createPinia, setActivePinia } from 'pinia'
import { nextTick } from 'vue'
import { createMemoryHistory, createRouter, type RouteRecordRaw } from 'vue-router'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'

import App from '@/App.vue'
import { buildI18n, localeKeys } from '@/i18n'
import { routes } from '@/router'
import { usePreferencesStore } from '@/stores/preferences'

beforeEach(() => {
  vi.spyOn(window.navigator, 'language', 'get').mockReturnValue('zh-CN')
  vi.spyOn(window, 'matchMedia').mockImplementation(
    (query: string) =>
      ({
        matches: false,
        media: query,
        onchange: null,
        addListener: vi.fn(),
        removeListener: vi.fn(),
        addEventListener: vi.fn(),
        removeEventListener: vi.fn(),
        dispatchEvent: vi.fn(),
      }) as unknown as MediaQueryList,
  )
})

afterEach(() => {
  cleanup()
  localStorage.clear()
  document.documentElement.className = ''
  vi.restoreAllMocks()
})

describe('壳层多语言', () => {
  it('路由标题 key 全量在两语言包在册（防漏翻译）', () => {
    const titles: string[] = []
    const walk = (list: RouteRecordRaw[]) => {
      for (const record of list) {
        if (record.meta?.title) titles.push(record.meta.title)
        if (record.children) walk(record.children as RouteRecordRaw[])
      }
    }
    walk(routes)
    expect(titles.length).toBeGreaterThan(0)

    const zh = localeKeys('zh-CN')
    const en = localeKeys('en')
    for (const key of titles) {
      expect(zh).toContain(key)
      expect(en).toContain(key)
    }
  })

  it('语言切换后菜单、面包屑、页签文案即时随动', async () => {
    const pinia = createPinia()
    setActivePinia(pinia)
    // 侧栏菜单来自后端菜单树（store），渲染前经全局桩 load 就位
    await useMenusStore().load()
    const router = createRouter({ history: createMemoryHistory(), routes })
    await router.push('/demo/cached')
    await router.isReady()
    // 渲染 App：语言绑定 watch（偏好 locale → i18n 实例）在 App 内接线
    render(App, { global: { plugins: [pinia, router, buildI18n('zh-CN'), VueQueryPlugin] } })

    // 中文态：菜单、面包屑、页签各自容器内为中文
    const menu = document.querySelector('.layout__menu') as HTMLElement
    const breadcrumb = document.querySelector('.layout__breadcrumb') as HTMLElement
    const tags = document.querySelector('[data-test="tags-view"]') as HTMLElement
    expect(within(menu).getByText('演示·缓存')).toBeTruthy()
    expect(within(breadcrumb).getByText('首页')).toBeTruthy()
    expect(within(breadcrumb).getByText('演示·缓存')).toBeTruthy()
    expect(within(tags).getByText('演示·缓存')).toBeTruthy()

    // 切到英文：三处渲染即时换语言（无需刷新）
    const prefs = usePreferencesStore()
    prefs.locale = 'en'
    await nextTick()
    await nextTick()

    expect(within(menu).getByText('Demo · Cached')).toBeTruthy()
    expect(within(breadcrumb).getByText('Home')).toBeTruthy()
    expect(within(tags).getByText('Demo · Cached')).toBeTruthy()
    expect(document.documentElement.getAttribute('lang')).toBe('en')
  })
})
