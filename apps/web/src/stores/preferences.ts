import { defineStore } from 'pinia'
import { computed, ref, watch } from 'vue'

export type ThemePreference = 'light' | 'dark' | 'auto'
export type LocalePreference = 'system' | 'zh-CN' | 'en'

export interface Preferences {
  theme: ThemePreference
  primaryColor: string
  locale: LocalePreference
}

/** Element Plus 默认主色，偏好缺省与色板回退值 */
export const DEFAULT_PRIMARY_COLOR = '#409EFF'

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

/** 读取持久化偏好；键缺失、JSON 损坏或字段非法一律整键弃用，重跑首访检测 */
function load(): Preferences {
  const raw = localStorage.getItem(STORAGE_KEY)
  if (raw) {
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
      // 损坏即弃，走首访检测
    }
  }
  return detectDefaults()
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

  return { theme, primaryColor, locale, resolvedLocale }
})
