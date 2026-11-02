import { createPinia, setActivePinia } from 'pinia'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'

import { DEFAULT_PRIMARY_COLOR, resolveLocale, usePreferencesStore } from '../preferences'

const STORAGE_KEY = 'eladmin-plus:preferences'

/** 新开一次「会话」：新 pinia + 新 store 实例（模拟应用重开） */
function freshStore() {
  setActivePinia(createPinia())
  return usePreferencesStore()
}

beforeEach(() => {
  setActivePinia(createPinia())
})

afterEach(() => {
  localStorage.clear()
  vi.restoreAllMocks()
})

describe('preferences store', () => {
  it('首访无存储值：主题随系统、locale 随系统语言（中文系）', () => {
    vi.spyOn(window.navigator, 'language', 'get').mockReturnValue('zh-CN')
    const store = usePreferencesStore()
    expect(store.theme).toBe('auto')
    expect(store.primaryColor).toBe(DEFAULT_PRIMARY_COLOR)
    expect(store.locale).toBe('zh-CN')
  })

  it('首访无存储值：系统语言非中文时 locale 为 en', () => {
    vi.spyOn(window.navigator, 'language', 'get').mockReturnValue('en-US')
    const store = usePreferencesStore()
    expect(store.locale).toBe('en')
  })

  it('偏好变化即整组持久化，重开读回', () => {
    const store = usePreferencesStore()
    store.theme = 'dark'
    store.primaryColor = '#123456'
    store.locale = 'en'

    const persisted = JSON.parse(localStorage.getItem(STORAGE_KEY)!) as Record<string, string>
    expect(persisted).toEqual({ theme: 'dark', primaryColor: '#123456', locale: 'en' })

    const reopened = freshStore()
    expect(reopened.theme).toBe('dark')
    expect(reopened.primaryColor).toBe('#123456')
    expect(reopened.locale).toBe('en')
  })

  it('JSON 损坏整键弃用，重跑首访检测', () => {
    localStorage.setItem(STORAGE_KEY, '{oops')
    const store = usePreferencesStore()
    expect(store.theme).toBe('auto')
    expect(store.primaryColor).toBe(DEFAULT_PRIMARY_COLOR)
  })

  it('字段非法同样整键弃用', () => {
    vi.spyOn(window.navigator, 'language', 'get').mockReturnValue('zh-CN')
    localStorage.setItem(
      STORAGE_KEY,
      JSON.stringify({ theme: 'blue', primaryColor: '#123', locale: 'en' }),
    )
    const store = usePreferencesStore()
    expect(store.theme).toBe('auto')
    expect(store.locale).toBe('zh-CN')
  })

  it('locale 偏好为 system 时 resolvedLocale 解析为实际语言', () => {
    vi.spyOn(window.navigator, 'language', 'get').mockReturnValue('zh-TW')
    const store = usePreferencesStore()
    store.locale = 'system'
    expect(store.resolvedLocale).toBe('zh-CN')

    // 系统语言只在首访检测时读取（非实时跟随），函数级断言解析规则
    vi.spyOn(window.navigator, 'language', 'get').mockReturnValue('fr-FR')
    expect(resolveLocale('system')).toBe('en')
    expect(resolveLocale('zh-CN')).toBe('zh-CN')
  })

  it('其他 tab 的偏好写入经 storage 事件实时同步本 tab', () => {
    const store = freshStore()
    expect(store.theme).toBe('auto')

    // 模拟另一 tab 写 localStorage 后浏览器派发的 storage 事件
    window.dispatchEvent(
      new StorageEvent('storage', {
        key: STORAGE_KEY,
        newValue: JSON.stringify({
          theme: 'dark',
          primaryColor: '#123456',
          locale: 'en',
        }),
      }),
    )
    expect(store.theme).toBe('dark')
    expect(store.primaryColor).toBe('#123456')
    expect(store.locale).toBe('en')
  })

  it('storage 事件的非法载荷不污染本 tab 偏好', () => {
    // 钉住系统语言:断言依赖缺省锚定 zh-CN
    vi.spyOn(window.navigator, 'language', 'get').mockReturnValue('zh-CN')
    const store = freshStore()
    window.dispatchEvent(
      new StorageEvent('storage', {
        key: STORAGE_KEY,
        newValue: JSON.stringify({ theme: 'blue', primaryColor: '', locale: 'xx' }),
      }),
    )
    window.dispatchEvent(new StorageEvent('storage', { key: 'other-key', newValue: '{}' }))
    expect(store.theme).toBe('auto')
    expect(store.locale).toBe('zh-CN')
  })
})
