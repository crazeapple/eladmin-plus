import { defineStore } from 'pinia'
import { ref } from 'vue'
import { refresh } from '@/api/auth'

const INTENT_KEY = 'auth:logged-in'

/**
 * 会话 store：accessToken 只住内存（不持久化——localStorage 扩大 XSS 盗取面），
 * 持久化由 refresh cookie 承担（后端管理，前端不读不写）。
 * localStorage 仅存非敏感的登录意图标志，F5 后守卫据此静默 refresh 恢复。
 */
export const useSessionStore = defineStore('session', () => {
  const accessToken = ref<string | null>(null)

  function save(token: string) {
    accessToken.value = token
    localStorage.setItem(INTENT_KEY, '1')
  }

  function clear() {
    accessToken.value = null
  }

  /** 完全清态：token 与登录意图一起撤（登出 / 刷新失败） */
  function reset() {
    accessToken.value = null
    localStorage.removeItem(INTENT_KEY)
  }

  /** F5 静默恢复：内存无 token 且有登录意图 → 静默 refresh；成功 true，失败 false（并清意图） */
  async function ensureSession(): Promise<boolean> {
    if (accessToken.value) {
      return true
    }
    if (localStorage.getItem(INTENT_KEY) !== '1') {
      return false
    }
    try {
      const result = await refresh()
      accessToken.value = result.accessToken
      return true
    } catch {
      reset()
      return false
    }
  }

  return { accessToken, save, clear, reset, ensureSession }
})
