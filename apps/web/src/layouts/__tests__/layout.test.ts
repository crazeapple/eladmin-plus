import { cleanup, fireEvent, render, screen, within } from '@testing-library/vue'
import { createPinia } from 'pinia'
import { createMemoryHistory, createRouter } from 'vue-router'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'

import { buildI18n } from '@/i18n'
import Layout from '@/layouts/Layout.vue'
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

function mockMediaQuery(matches: boolean) {
  vi.spyOn(window, 'matchMedia').mockImplementation(
    (query: string) =>
      ({
        matches,
        media: query,
        onchange: null,
        addListener: vi.fn(),
        removeListener: vi.fn(),
        addEventListener: vi.fn(),
        removeEventListener: vi.fn(),
        dispatchEvent: vi.fn(),
      }) as unknown as MediaQueryList,
  )
}

async function renderLayout(path = '/home') {
  const router = createRouter({ history: createMemoryHistory(), routes })
  await router.push(path)
  await router.isReady()
  render(Layout, { global: { plugins: [createPinia(), router, buildI18n('zh-CN')] } })
}

describe('Layout', () => {
  it('桌面形态：点击触发器切换侧栏折叠', async () => {
    mockMediaQuery(false)
    await renderLayout()
    const aside = document.querySelector('.layout__aside')!
    expect(aside.classList.contains('is-collapsed')).toBe(false)
    await fireEvent.click(screen.getByLabelText('折叠或展开侧栏'))
    expect(aside.classList.contains('is-collapsed')).toBe(true)
  })

  it('移动端形态：侧栏抽屉化，遮罩点击关闭', async () => {
    mockMediaQuery(true)
    await renderLayout()
    const aside = document.querySelector('.layout__aside')!
    expect(aside.classList.contains('is-mobile')).toBe(true)
    expect(aside.classList.contains('is-open')).toBe(false)

    await fireEvent.click(screen.getByLabelText('折叠或展开侧栏'))
    expect(aside.classList.contains('is-open')).toBe(true)
    expect(document.querySelector('.layout__overlay')).toBeTruthy()

    await fireEvent.click(document.querySelector('.layout__overlay')!)
    expect(aside.classList.contains('is-open')).toBe(false)
  })

  it('面包屑按静态路由生成', async () => {
    mockMediaQuery(false)
    await renderLayout('/demo/cached')
    const breadcrumb = document.querySelector('.layout__breadcrumb') as HTMLElement
    expect(within(breadcrumb).getByText('首页')).toBeTruthy()
    expect(within(breadcrumb).getByText('演示·缓存')).toBeTruthy()
  })

  it('全屏接线：点击触发 requestFullscreen', async () => {
    mockMediaQuery(false)
    const requestFullscreen = vi.fn().mockResolvedValue(undefined)
    document.documentElement.requestFullscreen =
      requestFullscreen as unknown as typeof document.documentElement.requestFullscreen
    document.exitFullscreen = vi
      .fn()
      .mockResolvedValue(undefined) as unknown as typeof document.exitFullscreen
    // vueuse 的支持探测只认旧前缀键（webkitIsFullScreen 等），happy-dom 均无，补桩
    Object.defineProperty(document, 'webkitIsFullScreen', { configurable: true, get: () => false })
    await renderLayout()
    await fireEvent.click(screen.getByLabelText('切换全屏'))
    expect(requestFullscreen).toHaveBeenCalled()
  })
})
