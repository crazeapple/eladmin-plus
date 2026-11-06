import { cleanup, render } from '@testing-library/vue'
import { createPinia, setActivePinia } from 'pinia'
import { defineComponent, h, nextTick } from 'vue'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'

import { usePreferencesStore } from '@/stores/preferences'
import { applyPrimaryColor, useTheme } from '../index'

/** 可控的 prefers-color-scheme 桩：支持改值并派发 change 事件（模拟系统切换深浅色） */
function mockPrefersColorScheme(initial: boolean) {
  let matches = initial
  const listeners = new Set<(event: { matches: boolean }) => void>()
  vi.spyOn(window, 'matchMedia').mockImplementation(
    (query: string) =>
      ({
        get matches() {
          return matches
        },
        media: query,
        onchange: null,
        addListener: (listener: () => void) => listeners.add(listener),
        removeListener: (listener: () => void) => listeners.delete(listener),
        addEventListener: (_type: string, listener: () => void) => listeners.add(listener),
        removeEventListener: (_type: string, listener: () => void) => listeners.delete(listener),
        dispatchEvent: vi.fn(),
      }) as unknown as MediaQueryList,
  )
  return {
    set(next: boolean) {
      matches = next
      for (const listener of listeners) {
        listener({ matches } as MediaQueryListEvent)
      }
    },
  }
}

const Probe = defineComponent({
  setup() {
    useTheme()
    return () => h('div')
  },
})

function renderProbe() {
  const pinia = createPinia()
  setActivePinia(pinia)
  render(Probe, { global: { plugins: [pinia] } })
  return usePreferencesStore()
}

beforeEach(() => {
  mockPrefersColorScheme(false)
})

afterEach(() => {
  cleanup()
  document.documentElement.className = ''
  document.documentElement.removeAttribute('style')
  localStorage.clear()
  vi.restoreAllMocks()
})

describe('主题三态', () => {
  it('light / dark 偏好直接决定 html class', async () => {
    const store = renderProbe()
    store.theme = 'light'
    await nextTick()
    expect(document.documentElement.classList.contains('dark')).toBe(false)
    store.theme = 'dark'
    await nextTick()
    expect(document.documentElement.classList.contains('dark')).toBe(true)
  })

  it('auto 按系统深浅色解析', () => {
    mockPrefersColorScheme(true)
    const store = renderProbe()
    store.theme = 'auto'
    expect(document.documentElement.classList.contains('dark')).toBe(true)

    mockPrefersColorScheme(false)
    const store2 = renderProbe()
    store2.theme = 'auto'
    expect(document.documentElement.classList.contains('dark')).toBe(false)
  })

  it('auto 态下系统切换深浅色实时跟随，无需刷新', async () => {
    const scheme = mockPrefersColorScheme(false)
    const store = renderProbe()
    store.theme = 'auto'
    await nextTick()
    expect(document.documentElement.classList.contains('dark')).toBe(false)

    scheme.set(true)
    await nextTick()
    expect(document.documentElement.classList.contains('dark')).toBe(true)
  })
})

describe('主色覆盖', () => {
  it('浅色分支变体与 EP 官方默认值一致（公式对齐）', () => {
    applyPrimaryColor('#409eff', false)
    const style = document.documentElement.style
    expect(style.getPropertyValue('--el-color-primary')).toBe('#409eff')
    expect(style.getPropertyValue('--el-color-primary-light-3')).toBe('#79bbff')
    expect(style.getPropertyValue('--el-color-primary-light-5')).toBe('#a0cfff')
    expect(style.getPropertyValue('--el-color-primary-light-7')).toBe('#c6e2ff')
    expect(style.getPropertyValue('--el-color-primary-light-9')).toBe('#ecf5ff')
    expect(style.getPropertyValue('--el-color-primary-dark-2')).toBe('#337ecc')
  })

  it('暗色分支变体与 EP 官方暗色值一致（混合基准反转）', () => {
    applyPrimaryColor('#409eff', true)
    const style = document.documentElement.style
    expect(style.getPropertyValue('--el-color-primary-light-3')).toBe('#3375b9')
    expect(style.getPropertyValue('--el-color-primary-light-5')).toBe('#2a598a')
    expect(style.getPropertyValue('--el-color-primary-dark-2')).toBe('#66b1ff')
  })

  it('自定义主色即时写入浅色变体', () => {
    applyPrimaryColor('#11a983', false)
    const style = document.documentElement.style
    expect(style.getPropertyValue('--el-color-primary')).toBe('#11a983')
    expect(style.getPropertyValue('--el-color-primary-light-3')).toBe('#58c3a8')
    expect(style.getPropertyValue('--el-color-primary-dark-2')).toBe('#0e8769')
  })

  it('品牌缺省色摘除行内接管（回归样式层品牌 token），大小写不敏感', () => {
    const style = document.documentElement.style
    applyPrimaryColor('#11a983', false)
    expect(style.getPropertyValue('--el-color-primary')).toBe('#11a983')

    // 自选 → 缺省：行内变体摘除，主色交还品牌 token 映射
    applyPrimaryColor('#5d87ff', false)
    expect(style.getPropertyValue('--el-color-primary')).toBe('')
    // 大写同值同语义
    applyPrimaryColor('#5D87FF', false)
    expect(style.getPropertyValue('--el-color-primary')).toBe('')
  })
})
