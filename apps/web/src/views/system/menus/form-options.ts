import { LOCALE_MESSAGES, localeKeys } from '@/i18n'
import type { AppLocale } from '@/i18n'
import { listViewKeys } from '@/router/views'

export interface KeySelectOption {
  value: string
  label: string
}

/** 点分 key 取语言包当前语言值（预览用；缺失返回空串） */
function packValue(locale: AppLocale, key: string): string {
  let node: unknown = LOCALE_MESSAGES[locale]
  for (const part of key.split('.')) {
    if (typeof node !== 'object' || node === null) {
      return ''
    }
    node = (node as Record<string, unknown>)[part]
  }
  return typeof node === 'string' ? node : ''
}

/**
 * i18n key 下拉数据源：语言包点分 key 全枚举 + 当前语言值预览。
 * 与双包同构测试同一事实源（localeKeys），词条进包即进下拉。
 */
export function i18nKeySelectOptions(locale: AppLocale): KeySelectOption[] {
  return localeKeys(locale).map((key) => {
    const preview = packValue(locale, key)
    return { value: key, label: preview ? `${key}（${preview}）` : key }
  })
}

/** 组件路径下拉数据源：views 反向导出（登录页已在 views 层排除） */
export function componentPathSelectOptions(): KeySelectOption[] {
  return listViewKeys().map((key) => ({ value: key, label: key }))
}
