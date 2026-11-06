import { defineStore } from 'pinia'
import { computed, ref, watch } from 'vue'

export type ThemePreference = 'light' | 'dark' | 'auto'
export type LocalePreference = 'system' | 'zh-CN' | 'en'

export interface Preferences {
  theme: ThemePreference
  primaryColor: string
  locale: LocalePreference
}

/** 品牌靛蓝主色（与样式层 --ea-primary 同源），偏好缺省与色板回退值 */
export const DEFAULT_PRIMARY_COLOR = '#5D87FF'

const STORAGE_KEY = 'eladmin-plus:preferences'

const THEME_VALUES: readonly ThemePreference[] = ['light', 'dark', 'auto']
const LOCALE_VALUES: readonly LocalePreference[] = ['system', 'zh-CN', 'en']

/** 首访检测：无有效存储值时的默认偏好（locale 随系统语言、主题随系统） */
function detectDefaults(): Preferences {
  return {
    theme: 'auto',
    primaryColor: DEFAULT_PRIMARY_COLOR,
    locale: systemLocale(),
  }
}

/** 系统语言映射到受支持语言（中文系 → zh-CN，其余 → en） */
function systemLocale(): 'zh-CN' | 'en' {
  const language = typeof navigator === 'undefined' ? 'zh-CN' : navigator.language
  return language.toLowerCase().startsWith('zh') ? 'zh-CN' : 'en'
}

/** system 偏好不是语言标签，对外（i18n、请求头）一律用解析值 */
export function resolveLocale(locale: LocalePreference): 'zh-CN' | 'en' {
  return locale === 'system' ? systemLocale() : locale
}

/** 解析持久化偏好原文；JSON 损坏或字段非法返回 null（调用方整键弃用，重跑首访检测） */
function parse(raw: string): Preferences | null {
  try {
    const parsed = JSON.parse(raw) as Partial<Preferences>
    if (
      typeof parsed.theme === 'string' &&
      THEME_VALUES.includes(parsed.theme as ThemePreference) &&
      typeof parsed.primaryColor === 'string' &&
      parsed.primaryColor.length > 0 &&
      typeof parsed.locale === 'string' &&
      LOCALE_VALUES.includes(parsed.locale as LocalePreference)
    ) {
      return {
        theme: parsed.theme as ThemePreference,
        primaryColor: parsed.primaryColor,
        locale: parsed.locale as LocalePreference,
      }
    }
  } catch {
    // 损坏即弃
  }
  return null
}

/** 读取持久化偏好；键缺失或非法一律整键弃用，重跑首访检测 */
function load(): Preferences {
  const raw = localStorage.getItem(STORAGE_KEY)
  if (!raw) {
    return detectDefaults()
  }
  return parse(raw) ?? detectDefaults()
}

export const usePreferencesStore = defineStore('preferences', () => {
  const initial = load()
  const theme = ref<ThemePreference>(initial.theme)
  const primaryColor = ref(initial.primaryColor)
  const locale = ref<LocalePreference>(initial.locale)

  // 偏好变化即时整组写回，无显式保存动作
  watch(
    [theme, primaryColor, locale],
    () => {
      localStorage.setItem(
        STORAGE_KEY,
        JSON.stringify({
          theme: theme.value,
          primaryColor: primaryColor.value,
          locale: locale.value,
        } satisfies Preferences),
      )
    },
    { flush: 'sync' },
  )

  const resolvedLocale = computed(() => resolveLocale(locale.value))

  // 其他 tab 改偏好时实时同步本 tab（refs 一变，主题/语言的消费方 watch 即自动应用）
  window.addEventListener('storage', (event) => {
    if (event.key !== STORAGE_KEY || event.newValue === null) return
    const next = parse(event.newValue)
    if (!next) return
    theme.value = next.theme
    primaryColor.value = next.primaryColor
    locale.value = next.locale
  })

  return { theme, primaryColor, locale, resolvedLocale }
})
