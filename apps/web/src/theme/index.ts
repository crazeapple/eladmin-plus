import { useColorMode } from '@vueuse/core'
import { storeToRefs } from 'pinia'
import { type Ref, computed, watchEffect } from 'vue'

import { usePreferencesStore, type ThemePreference } from '@/stores/preferences'
import { primaryVariants } from './colors'

/** 三态偏好解析为明暗（auto 随系统）；系统信号由调用方传入，监听归各自持有 */
export function resolveDarkness(theme: ThemePreference, systemDark: boolean): boolean {
  return theme === 'auto' ? systemDark : theme === 'dark'
}

/** 把主色变体写到根元素行内样式（覆盖 EP 样式表变量） */
export function applyPrimaryColor(color: string, dark: boolean): void {
  const root = document.documentElement
  for (const [name, value] of Object.entries(primaryVariants(color, dark))) {
    root.style.setProperty(name, value)
  }
}

/**
 * 三态主题接线：偏好 store 即唯一存储（useColorMode 经 storageRef 读写偏好），
 * html 挂 dark class 启用 EP 暗色变量；auto 态经 matchMedia 实时跟随系统。
 */
export function useTheme() {
  const prefs = usePreferencesStore()
  const { theme, primaryColor } = storeToRefs(prefs)

  const colorMode = useColorMode({
    selector: 'html',
    attribute: 'class',
    modes: { light: '', dark: 'dark' },
    // 持久化归偏好 store：单一真相，不另设 localStorage 键
    storageRef: theme as Ref<string>,
  })

  const resolvedTheme = computed<'light' | 'dark'>(() =>
    resolveDarkness(theme.value, colorMode.system.value === 'dark') ? 'dark' : 'light',
  )

  // 主色随偏好与明暗分支即时重算（浅深两套混合基准）
  watchEffect(() => {
    applyPrimaryColor(primaryColor.value, resolvedTheme.value === 'dark')
  })

  return { resolvedTheme }
}
