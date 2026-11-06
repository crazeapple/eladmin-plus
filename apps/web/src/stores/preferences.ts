import { defineStore } from 'pinia'
import { computed, ref, watch } from 'vue'
import { ElMessage } from 'element-plus'

import { ApiError } from '@/api/client'
import { putPreferences } from '@/api/profile'
import { useSessionStore } from '@/stores/session'

export type ThemePreference = 'light' | 'dark' | 'auto'
export type LocalePreference = 'system' | 'zh-CN' | 'en'
export type IconSetPreference = 'svg' | 'ep'

export interface Preferences {
  theme: ThemePreference
  primaryColor: string
  locale: LocalePreference
  iconSet: IconSetPreference
}

/** 品牌靛蓝主色（与样式层 --ea-primary 同形），偏好缺省与色板回退值 */
export const DEFAULT_PRIMARY_COLOR = '#5d87ff'

const STORAGE_KEY = 'eladmin-plus:preferences'

const THEME_VALUES: readonly ThemePreference[] = ['light', 'dark', 'auto']
const LOCALE_VALUES: readonly LocalePreference[] = ['system', 'zh-CN', 'en']
const ICON_SET_VALUES: readonly IconSetPreference[] = ['svg', 'ep']

/** 缺省偏好（与后端缺省同值）：账户未设置过偏好时 me 即下发此组 */
const DEFAULTS: Preferences = {
  theme: 'auto',
  primaryColor: DEFAULT_PRIMARY_COLOR,
  locale: 'system',
  iconSet: 'svg',
}

/** 主色合法形态：# 前缀 hex，3 / 6 / 8 位（与后端写口校验同则） */
const COLOR_PATTERN = /^#([0-9a-f]{3}|[0-9a-f]{6}|[0-9a-f]{8})$/i

/** 首访检测：无有效存储值时的默认偏好（locale 随系统语言、主题随系统） */
function detectDefaults(): Preferences {
  return { ...DEFAULTS, locale: systemLocale() }
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

/** 字段合法判定（本地缓存、storage 载荷与 me 账户值共用同一值域） */
function validTheme(value: unknown): value is ThemePreference {
  return typeof value === 'string' && THEME_VALUES.includes(value as ThemePreference)
}

function validLocale(value: unknown): value is LocalePreference {
  return typeof value === 'string' && LOCALE_VALUES.includes(value as LocalePreference)
}

function validIconSet(value: unknown): value is IconSetPreference {
  return typeof value === 'string' && ICON_SET_VALUES.includes(value as IconSetPreference)
}

function validPrimaryColor(value: unknown): value is string {
  return typeof value === 'string' && COLOR_PATTERN.test(value)
}

/** 解析持久化偏好原文；JSON 损坏或必填字段非法返回 null（调用方整键弃用，重跑首访检测）。
 * iconSet 后补落库，旧缓存缺该字段按缺省 svg 读（不弃整键）。 */
function parse(raw: string): Preferences | null {
  try {
    const parsed = JSON.parse(raw) as Partial<Preferences>
    if (
      validTheme(parsed.theme) &&
      validPrimaryColor(parsed.primaryColor) &&
      validLocale(parsed.locale)
    ) {
      return {
        theme: parsed.theme,
        primaryColor: parsed.primaryColor,
        locale: parsed.locale,
        iconSet: validIconSet(parsed.iconSet) ? parsed.iconSet : 'svg',
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
  const iconSet = ref<IconSetPreference>(initial.iconSet)

  function applyAll(next: Preferences) {
    theme.value = next.theme
    primaryColor.value = next.primaryColor
    locale.value = next.locale
    iconSet.value = next.iconSet
  }

  function holds(next: Preferences): boolean {
    return (
      theme.value === next.theme &&
      primaryColor.value === next.primaryColor &&
      locale.value === next.locale &&
      iconSet.value === next.iconSet
    )
  }

  // 偏好变化即时整组写回（storage 事件保多 Tab 同步），无显式保存动作
  watch(
    [theme, primaryColor, locale, iconSet],
    () => {
      localStorage.setItem(
        STORAGE_KEY,
        JSON.stringify({
          theme: theme.value,
          primaryColor: primaryColor.value,
          locale: locale.value,
          iconSet: iconSet.value,
        } satisfies Preferences),
      )
    },
    { flush: 'sync' },
  )

  const resolvedLocale = computed(() => resolveLocale(locale.value))

  /**
   * 用户切换偏好：本地即时生效（UI 立即应用 + storage 事件同步其他 Tab），
   * 登录态再异步 PUT 后端（全量四项幂等）；匿名期仅本地临时，登录后账户值权威覆盖。
   * PUT 失败回滚到改前值并提示（失败期间新切换已接管则让位不回滚），下次切换重写。
   */
  async function setPreference<K extends keyof Preferences>(key: K, value: Preferences[K]) {
    const before: Preferences = {
      theme: theme.value,
      primaryColor: primaryColor.value,
      locale: locale.value,
      iconSet: iconSet.value,
    }
    const written: Preferences = { ...before, [key]: value }
    applyAll(written)
    if (!useSessionStore().accessToken) {
      return
    }
    try {
      await putPreferences(written)
    } catch (error) {
      if (holds(written)) {
        applyAll(before)
      }
      ElMessage.error(failureText(error))
    }
  }

  /** 失败文案：后端可本地化消息直出，网络层失败按当前语言兜底 */
  function failureText(error: unknown): string {
    if (error instanceof ApiError && error.message) {
      return error.message
    }
    return resolvedLocale.value === 'en' ? 'Failed to save preferences' : '偏好保存失败'
  }

  /**
   * me 到达的账户值覆盖（登录后账户偏好权威）：先本地缓存渲染、me 到达后逐字段覆盖——
   * 字段合法才收，非法或缺省字段落缺省偏好；preferences 字段整体缺失（如契约降级）不动本地。
   */
  function applyAccountPreferences(payload: unknown) {
    if (typeof payload !== 'object' || payload === null) {
      return
    }
    const account = payload as Record<string, unknown>
    applyAll({
      theme: validTheme(account.theme) ? account.theme : DEFAULTS.theme,
      primaryColor: validPrimaryColor(account.primaryColor)
        ? account.primaryColor
        : DEFAULTS.primaryColor,
      locale: validLocale(account.locale) ? account.locale : DEFAULTS.locale,
      iconSet: validIconSet(account.iconSet) ? account.iconSet : DEFAULTS.iconSet,
    })
  }

  // 其他 tab 改偏好时实时同步本 tab（refs 一变，主题/语言的消费方 watch 即自动应用；远端写入不再回写后端）
  window.addEventListener('storage', (event) => {
    if (event.key !== STORAGE_KEY || event.newValue === null) return
    const next = parse(event.newValue)
    if (!next) return
    applyAll(next)
  })

  return {
    theme,
    primaryColor,
    locale,
    iconSet,
    resolvedLocale,
    setPreference,
    applyAccountPreferences,
  }
})
