import { storeToRefs } from 'pinia'
import { watch } from 'vue'

import { usePreferencesStore } from '@/stores/preferences'

import { client } from './client'

/** 偏好语言联动默认请求头：后端 MessageSource 按 Accept-Language 本地化错误消息 */
export function bindLocaleHeader(): void {
  const { resolvedLocale } = storeToRefs(usePreferencesStore())

  const apply = (locale: string) => {
    client.setConfig({ headers: { 'Accept-Language': locale } })
  }

  apply(resolvedLocale.value)
  watch(resolvedLocale, apply)
}
