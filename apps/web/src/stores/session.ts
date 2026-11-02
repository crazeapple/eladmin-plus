import { defineStore } from 'pinia'
import { ref } from 'vue'

/**
 * 会话 store：accessToken 只住内存（不持久化——localStorage 扩大 XSS 盗取面），
 * 持久化由 refresh cookie 承担（后端管理，前端不读不写）；F5 静默恢复随刷新链路接线。
 */
export const useSessionStore = defineStore('session', () => {
  const accessToken = ref<string | null>(null)

  function save(token: string) {
    accessToken.value = token
  }

  function clear() {
    accessToken.value = null
  }

  return { accessToken, save, clear }
})
