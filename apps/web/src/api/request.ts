import { ApiError, client } from './client'
import { refresh } from './auth'
import { useSessionStore } from '@/stores/session'

/**
 * 认证业务请求统一包装：
 * - 注入 Authorization（内存 token）与自定义头（CSRF 纵深）
 * - 401 单飞刷新：并发请求共享同一次 refresh，成功重放原请求（一次性，防循环）；失败清态
 * 跳登录的收口在路由守卫（全局仅一处），此层只清状态并抛 401 语义错误。
 */
export interface RequestOptions {
  url: string
  method?: 'GET' | 'POST' | 'PUT' | 'DELETE'
  body?: unknown
  /** 401 重放标记：重放请求不再触发刷新（防循环），外部勿传 */
  authReplayed?: boolean
}

let refreshing: Promise<string | null> | null = null

/** 单飞刷新：并发共享同一次；成功返回新 token 并入 store，失败清态返回 null */
function ensureFreshToken(): Promise<string | null> {
  refreshing ??= refresh()
    .then((result) => {
      useSessionStore().save(result.accessToken)
      return result.accessToken
    })
    .catch(() => {
      // 刷新失败：清空会话与登录意图（F5 恢复不再尝试）
      useSessionStore().reset()
      return null
    })
    .finally(() => {
      refreshing = null
    })
  return refreshing
}

export async function request<T>(options: RequestOptions): Promise<T> {
  const session = useSessionStore()
  const token = session.accessToken
  const headers: Record<string, string> = { 'X-Requested-With': 'XMLHttpRequest' }
  if (token) {
    headers.Authorization = `Bearer ${token}`
  }
  try {
    const { data } = await client.request({
      url: options.url,
      method: options.method ?? 'GET',
      body: options.body,
      headers,
      throwOnError: true,
    })
    return data as T
  } catch (error) {
    // 401：刷新后重放一次（防循环由 authReplayed 标记保证）
    if (!(error instanceof ApiError) || error.code !== 401 || options.authReplayed) {
      throw error
    }
    const newToken = await ensureFreshToken()
    if (!newToken) {
      throw error
    }
    const { data } = await client.request({
      url: options.url,
      method: options.method ?? 'GET',
      body: options.body,
      headers: { ...headers, Authorization: `Bearer ${newToken}` },
      throwOnError: true,
    })
    return data as T
  }
}

/** 供测试与手动重放使用的重放版包装 */
export async function replayedRequest<T>(options: RequestOptions): Promise<T> {
  return request<T>({ ...options, authReplayed: true })
}
