import { createPinia, setActivePinia } from 'pinia'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'

import { useSessionStore } from '../session'
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
    store.iconSet = 'ep'

    const persisted = JSON.parse(localStorage.getItem(STORAGE_KEY)!) as Record<string, string>
    expect(persisted).toEqual({
      theme: 'dark',
      primaryColor: '#123456',
      locale: 'en',
      iconSet: 'ep',
    })

    const reopened = freshStore()
    expect(reopened.theme).toBe('dark')
    expect(reopened.primaryColor).toBe('#123456')
    expect(reopened.locale).toBe('en')
    expect(reopened.iconSet).toBe('ep')
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

  it('iconSet 旧缓存缺字段按缺省 svg 读，在册值原样读', () => {
    localStorage.setItem(
      STORAGE_KEY,
      JSON.stringify({ theme: 'light', primaryColor: '#123456', locale: 'en' }),
    )
    const store = freshStore()
    expect(store.iconSet).toBe('svg')

    localStorage.setItem(
      STORAGE_KEY,
      JSON.stringify({ theme: 'light', primaryColor: '#123456', locale: 'en', iconSet: 'ep' }),
    )
    expect(freshStore().iconSet).toBe('ep')
  })

  it('缓存先行渲染、me 到达覆盖：状态迁移时序为本地值 → 账户值', () => {
    localStorage.setItem(
      STORAGE_KEY,
      JSON.stringify({ theme: 'dark', primaryColor: '#123456', locale: 'en', iconSet: 'svg' }),
    )
    const store = freshStore()
    // 首帧：本地缓存先行
    expect(store.theme).toBe('dark')
    expect(store.locale).toBe('en')

    // me 到达：账户值权威覆盖，并回写本地缓存
    store.applyAccountPreferences({
      theme: 'light',
      primaryColor: '#abcdef',
      locale: 'zh-CN',
      iconSet: 'ep',
    })
    expect(store.theme).toBe('light')
    expect(store.primaryColor).toBe('#abcdef')
    expect(store.locale).toBe('zh-CN')
    expect(store.iconSet).toBe('ep')
    expect(JSON.parse(localStorage.getItem(STORAGE_KEY)!)).toEqual({
      theme: 'light',
      primaryColor: '#abcdef',
      locale: 'zh-CN',
      iconSet: 'ep',
    })
  })

  it('me preferences 字段整体缺失不动本地（契约降级保缓存先行语义）', () => {
    const store = freshStore()
    store.theme = 'dark'
    store.locale = 'en'

    store.applyAccountPreferences(undefined)
    expect(store.theme).toBe('dark')
    expect(store.locale).toBe('en')
  })

  it('me 账户值字段非法逐字段落缺省，合法字段照收', () => {
    vi.spyOn(window.navigator, 'language', 'get').mockReturnValue('zh-CN')
    const store = freshStore()
    store.theme = 'dark'

    store.applyAccountPreferences({
      theme: 'blue',
      primaryColor: 'not-a-color',
      locale: 'en',
      iconSet: 'ep',
    })
    expect(store.theme).toBe('auto')
    expect(store.primaryColor).toBe(DEFAULT_PRIMARY_COLOR)
    expect(store.locale).toBe('en')
    expect(store.iconSet).toBe('ep')
  })

  it('匿名期切换偏好只落本地，不发任何请求', async () => {
    const fetchSpy = vi.fn()
    vi.stubGlobal('fetch', fetchSpy)
    const store = freshStore()
    // 无会话令牌（匿名期）
    await store.setPreference('theme', 'dark')
    await store.setPreference('locale', 'en')

    expect(fetchSpy).not.toHaveBeenCalled()
    expect(store.theme).toBe('dark')
    expect(store.locale).toBe('en')
    expect(JSON.parse(localStorage.getItem(STORAGE_KEY)!).theme).toBe('dark')
  })

  it('登录态切换偏好异步 PUT 全量四项，成功不回滚', async () => {
    // 钉住系统语言，PUT 载荷的 locale 缺省形态确定
    vi.spyOn(window.navigator, 'language', 'get').mockReturnValue('zh-CN')
    const fetchSpy = vi.fn(async (input: RequestInfo | URL) => {
      const url = input instanceof Request ? input.url : String(input)
      if (url.includes('/profile/preferences')) {
        return new Response(JSON.stringify({ code: 200, message: '', data: null }), {
          status: 200,
          headers: { 'Content-Type': 'application/json' },
        })
      }
      throw new Error(`测试不应真发请求：${url}`)
    })
    vi.stubGlobal('fetch', fetchSpy)
    const store = freshStore()
    useSessionStore().save('token-in-test')

    await store.setPreference('theme', 'dark')
    expect(store.theme).toBe('dark')

    const putCall = fetchSpy.mock.calls
      .map((call) => call[0] as Request)
      .find((request) => request.url.includes('/profile/preferences'))
    expect(putCall).toBeTruthy()
    expect(putCall!.method).toBe('PUT')
    expect(await putCall!.json()).toEqual({
      theme: 'dark',
      primaryColor: DEFAULT_PRIMARY_COLOR,
      locale: 'zh-CN',
      iconSet: 'svg',
    })
  })

  it('登录态 PUT 失败回滚到改前值', async () => {
    vi.stubGlobal(
      'fetch',
      vi.fn(async (input: RequestInfo | URL) => {
        const url = input instanceof Request ? input.url : String(input)
        if (url.includes('/profile/preferences')) {
          return new Response(JSON.stringify({ code: 500, message: '后端不可用' }), {
            status: 500,
            headers: { 'Content-Type': 'application/json' },
          })
        }
        throw new Error(`测试不应真发请求：${url}`)
      }),
    )
    const store = freshStore()
    useSessionStore().save('token-in-test')

    // 乐观生效在先，PUT 失败回滚改前值在后
    const change = store.setPreference('theme', 'dark')
    expect(store.theme).toBe('dark')
    await change
    expect(store.theme).toBe('auto')
  })

  it('PUT 失败期间新切换已接管：失败回滚让位，不倒回更旧值', async () => {
    // 钉住系统语言：初始 locale 为 zh-CN，切换 en 才构成真实状态迁移
    vi.spyOn(window.navigator, 'language', 'get').mockReturnValue('zh-CN')
    let releaseThemePut: (() => void) | null = null
    vi.stubGlobal(
      'fetch',
      vi.fn(async (input: RequestInfo | URL) => {
        const url = input instanceof Request ? input.url : String(input)
        if (url.includes('/profile/preferences')) {
          if (releaseThemePut === null) {
            // 首个 PUT（theme）挂起模拟慢网，放行后以失败收场
            await new Promise<void>((resolve) => {
              releaseThemePut = resolve
            })
            return new Response(JSON.stringify({ code: 500, message: '后端不可用' }), {
              status: 500,
              headers: { 'Content-Type': 'application/json' },
            })
          }
          return new Response(JSON.stringify({ code: 200, message: '', data: null }), {
            status: 200,
            headers: { 'Content-Type': 'application/json' },
          })
        }
        throw new Error(`测试不应真发请求：${url}`)
      }),
    )
    const store = freshStore()
    useSessionStore().save('token-in-test')

    const themeChange = store.setPreference('theme', 'dark')
    await vi.waitFor(() => expect(releaseThemePut).not.toBeNull())

    // 新切换（locale）接管状态后，首个 PUT 才失败返回
    const localeChange = store.setPreference('locale', 'en')
    releaseThemePut!()
    await Promise.all([themeChange, localeChange])

    expect(store.locale).toBe('en')
    expect(store.theme).toBe('dark')
  })
})
