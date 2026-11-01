import { createPinia, setActivePinia } from 'pinia'
import { nextTick } from 'vue'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'

import { usePreferencesStore } from '@/stores/preferences'

import { client } from '../client'
import { bindLocaleHeader } from '../locale'

function headerValue(): string {
  // client 配置的 headers 是 Headers 实例
  return (client.getConfig().headers as Headers).get('Accept-Language') ?? ''
}

beforeEach(() => {
  setActivePinia(createPinia())
  vi.spyOn(window.navigator, 'language', 'get').mockReturnValue('zh-CN')
})

afterEach(() => {
  localStorage.clear()
  vi.restoreAllMocks()
})

describe('语言联动请求头', () => {
  it('绑定即写初值，切换语言即随动', async () => {
    bindLocaleHeader()
    expect(headerValue()).toBe('zh-CN')

    const prefs = usePreferencesStore()
    prefs.locale = 'en'
    await nextTick()
    expect(headerValue()).toBe('en')
  })

  it('system 偏好取解析后的真实语言标签', () => {
    vi.spyOn(window.navigator, 'language', 'get').mockReturnValue('fr-FR')
    bindLocaleHeader()
    expect(headerValue()).toBe('en')
  })
})
