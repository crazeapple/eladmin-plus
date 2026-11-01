import { createI18n } from 'vue-i18n'
import enPack from 'element-plus/es/locale/lang/en'
import zhCn from 'element-plus/es/locale/lang/zh-cn'

import en from './locales/en'
import zhCN from './locales/zh-CN'

export type AppLocale = 'zh-CN' | 'en'

export const FALLBACK_LOCALE: AppLocale = 'zh-CN'

export const LOCALE_MESSAGES = { 'zh-CN': zhCN, en } as const

/** 构建应用 i18n 实例（main 与测试共用，locale 传偏好解析值；类型按 legacy: false 推断） */
export function buildI18n(locale: AppLocale) {
  return createI18n({
    legacy: false,
    locale,
    fallbackLocale: FALLBACK_LOCALE,
    messages: LOCALE_MESSAGES,
    // 缺失告警仅开发态，生产静默走 fallback
    missingWarn: import.meta.env.DEV,
    fallbackWarn: import.meta.env.DEV,
  })
}

/** 语言切换即时生效：i18n locale 与根元素 lang 同步（实例 global 与组件 composer 均可传入） */
export function applyLocale(i18n: { locale: { value: string } }, locale: AppLocale): void {
  i18n.locale.value = locale
  document.documentElement.setAttribute('lang', locale)
}

/** EP 组件文案语言包（el-config-provider 消费） */
export function elementPlusLocale(locale: AppLocale) {
  return locale === 'zh-CN' ? zhCn : enPack
}

/** 语言包点分 key 全列表（key 在册断言用） */
export function localeKeys(locale: AppLocale): string[] {
  const flatten = (source: Record<string, unknown>, prefix = ''): string[] =>
    Object.entries(source).flatMap(([key, value]) => {
      const name = prefix ? `${prefix}.${key}` : key
      return typeof value === 'object' && value !== null
        ? flatten(value as Record<string, unknown>, name)
        : [name]
    })
  return flatten(LOCALE_MESSAGES[locale]).sort()
}
