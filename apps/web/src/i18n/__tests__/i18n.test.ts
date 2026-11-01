import { describe, expect, it } from 'vitest'

import {
  FALLBACK_LOCALE,
  LOCALE_MESSAGES,
  applyLocale,
  buildI18n,
  elementPlusLocale,
} from '../index'

/** 递归拍平语言包，产出点分 key 列表 */
function flattenKeys(source: Record<string, unknown>, prefix = ''): string[] {
  return Object.entries(source).flatMap(([key, value]) => {
    const name = prefix ? `${prefix}.${key}` : key
    return typeof value === 'object' && value !== null
      ? flattenKeys(value as Record<string, unknown>, name)
      : [name]
  })
}

const zhKeys = flattenKeys(LOCALE_MESSAGES['zh-CN']).sort()
const enKeys = flattenKeys(LOCALE_MESSAGES.en).sort()

describe('i18n 框架', () => {
  it('双语言包 key 集同构（缺 key 在源头拦住，不依赖 fallback 兜底）', () => {
    expect(enKeys).toEqual(zhKeys)
    expect(zhKeys.length).toBeGreaterThan(0)
  })

  it('实例以 zh-CN 兜底、composition 模式构建', () => {
    const i18n = buildI18n('en')
    expect(i18n.global.locale.value).toBe('en')
    expect(i18n.global.fallbackLocale.value).toBe(FALLBACK_LOCALE)
    expect(i18n.global.t('routes.home')).toBe('Home')
  })

  it('applyLocale 同步实例语言与根元素 lang', () => {
    const target = { locale: { value: 'zh-CN' as string } }
    applyLocale(target, 'en')
    expect(target.locale.value).toBe('en')
    expect(document.documentElement.getAttribute('lang')).toBe('en')
  })

  it('EP 组件文案语言包随 locale 取用', () => {
    expect(elementPlusLocale('zh-CN').name).toBe('zh-cn')
    expect(elementPlusLocale('en').name).toBe('en')
  })
})
