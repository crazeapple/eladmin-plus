import { cleanup, fireEvent, render, screen, waitFor, within } from '@testing-library/vue'
import { createPinia, setActivePinia } from 'pinia'
import { createMemoryHistory, createRouter } from 'vue-router'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'

import App from '@/App.vue'
import { buildI18n } from '@/i18n'
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
  document.documentElement.removeAttribute('style')
  vi.restoreAllMocks()
})

/** 渲染整个 App：抽屉改动经偏好 store 驱动主题与语言的全链生效 */
async function renderApp(path = '/home') {
  const pinia = createPinia()
  setActivePinia(pinia)
  const router = createRouter({ history: createMemoryHistory(), routes })
  await router.push(path)
  await router.isReady()
  render(App, { global: { plugins: [pinia, router, buildI18n('zh-CN')] } })
  return usePreferencesStore()
}

/** 设置入口按钮（data-test 惯例与 tags-view 一致） */
function settingsTrigger(): HTMLElement {
  return document.querySelector('[data-test="settings-trigger"]') as HTMLElement
}

describe('SettingsDrawer', () => {
  it('入口开合：点设置图标开抽屉，关闭按钮收起', async () => {
    await renderApp()
    expect(screen.queryByText('主题')).toBeNull()

    await fireEvent.click(settingsTrigger())
    expect(await screen.findByText('主题')).toBeTruthy()

    await fireEvent.click(document.querySelector('.el-drawer__close-btn') as HTMLElement)
    // 抽屉收起：遮罩移除或隐藏（happy-dom 无过渡动画，EP 保留隐藏 DOM）
    await waitFor(() => {
      const overlay = document.querySelector('.el-overlay') as HTMLElement | null
      expect(overlay === null || overlay.style.display === 'none').toBe(true)
    })
  })

  it('切深色即时生效：偏好、html class 同步', async () => {
    const prefs = await renderApp()
    await fireEvent.click(settingsTrigger())
    await screen.findByText('深色')

    await fireEvent.click(screen.getByText('深色'))
    expect(prefs.theme).toBe('dark')
    await waitFor(() => expect(document.documentElement.classList.contains('dark')).toBe(true))
  })

  it('选预设主色即时写入 CSS 变量与偏好', async () => {
    const prefs = await renderApp()
    await fireEvent.click(settingsTrigger())
    await screen.findByText('主色')

    await fireEvent.click(screen.getByRole('button', { name: 'primary color #11a983' }))
    expect(prefs.primaryColor).toBe('#11a983')
    await waitFor(() =>
      expect(document.documentElement.style.getPropertyValue('--el-color-primary')).toBe('#11a983'),
    )
  })

  it('切英文即时生效：偏好、菜单文案、抽屉标题随动', async () => {
    const prefs = await renderApp()
    await fireEvent.click(settingsTrigger())
    await screen.findByText('语言')

    await fireEvent.click(screen.getByText('English'))
    expect(prefs.locale).toBe('en')
    await waitFor(() => expect(screen.getByText('Theme')).toBeTruthy())
    const menu = document.querySelector('.layout__menu') as HTMLElement
    await waitFor(() => expect(within(menu).getByText('Home')).toBeTruthy())
    expect(document.documentElement.getAttribute('lang')).toBe('en')
  })
})
