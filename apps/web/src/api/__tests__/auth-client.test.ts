import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { createPinia, setActivePinia } from 'pinia'
import { ApiError, client } from '../client'
import { request } from '../request'
import { useSessionStore } from '@/stores/session'

/** envelope 响应构造 */
function jsonResponse(status: number, body: unknown): Response {
  return new Response(JSON.stringify(body), {
    status,
    headers: { 'Content-Type': 'application/json' },
  })
}

/** fetch 桩：按脚本序列出响应，记录请求头 */
function stubFetch(script: Array<{ status: number; body: unknown }>) {
  const calls: Array<{ url: string; headers: Record<string, string> }> = []
  const spy = vi.fn(async (input: RequestInfo | URL, init?: RequestInit) => {
    const step = script[calls.length] ?? script[script.length - 1]!
    // hey-api 传 Request 对象（headers 在其上），也兼容 (url, init) 形态
    const request = input instanceof Request ? input : new Request(input, init)
    calls.push({ url: request.url, headers: Object.fromEntries(request.headers.entries()) })
    return jsonResponse(step.status, step.body)
  })
  vi.stubGlobal('fetch', spy)
  return calls
}

beforeEach(() => {
  setActivePinia(createPinia())
  localStorage.clear()
})

afterEach(() => {
  vi.unstubAllGlobals()
  vi.restoreAllMocks()
  localStorage.clear()
})

describe('认证请求包装', () => {
  it('带令牌请求注入 Authorization 与自定义头', async () => {
    const store = useSessionStore()
    store.save('token-a')
    const calls = stubFetch([{ status: 200, body: { code: 200, message: '', data: { ok: 1 } } }])

    const data = await request<{ ok: number }>({ url: '/users' })

    expect(data).toEqual({ ok: 1 })
    expect(calls[0]!.headers.Authorization).toBe('Bearer token-a')
    expect(calls[0]!.headers['X-Requested-With']).toBe('XMLHttpRequest')
  })

  it('401 单飞刷新并重放原请求一次', async () => {
    const store = useSessionStore()
    store.save('stale-token')
    const calls = stubFetch([
      { status: 401, body: { code: 401, message: '未登录或登录已过期', data: null } },
      {
        status: 200,
        body: { code: 200, message: '', data: { accessToken: 'fresh-token', expiresIn: 7200 } },
      },
      { status: 200, body: { code: 200, message: '', data: { ok: 2 } } },
    ])

    const data = await request<{ ok: number }>({ url: '/users' })

    expect(data).toEqual({ ok: 2 })
    expect(calls).toHaveLength(3)
    // 首发带旧令牌、刷新不带 Authorization（cookie 承载）、重放带新令牌
    expect(calls[0]!.headers.Authorization).toBe('Bearer stale-token')
    expect(calls[1]!.url).toContain('/auth/refresh')
    expect(calls[1]!.headers['X-Requested-With']).toBe('XMLHttpRequest')
    expect(calls[2]!.headers.Authorization).toBe('Bearer fresh-token')
    // 新令牌入内存
    expect(store.accessToken).toBe('fresh-token')
  })

  it('并发 401 共享一次刷新', async () => {
    const store = useSessionStore()
    store.save('stale-token')
    const calls = stubFetch([
      { status: 401, body: { code: 401, message: '', data: null } },
      { status: 401, body: { code: 401, message: '', data: null } },
      {
        status: 200,
        body: { code: 200, message: '', data: { accessToken: 'fresh', expiresIn: 7200 } },
      },
      { status: 200, body: { code: 200, message: '', data: { ok: 'a' } } },
      { status: 200, body: { code: 200, message: '', data: { ok: 'b' } } },
    ])

    const [a, b] = await Promise.all([
      request<{ ok: string }>({ url: '/users' }),
      request<{ ok: string }>({ url: '/roles' }),
    ])

    expect(a).toEqual({ ok: 'a' })
    expect(b).toEqual({ ok: 'b' })
    // 两次首发 + 一次刷新 + 两次重放 = 5
    expect(calls).toHaveLength(5)
    expect(calls.filter((call) => call.url.includes('/auth/refresh'))).toHaveLength(1)
    expect(store.accessToken).toBe('fresh')
  })

  it('刷新失败清态并抛 401 语义错误', async () => {
    const store = useSessionStore()
    store.save('stale-token')
    localStorage.setItem('auth:logged-in', '1')
    const calls = stubFetch([
      { status: 401, body: { code: 401, message: '未登录或登录已过期', data: null } },
      { status: 401, body: { code: 401, message: '登录状态已失效，请重新登录', data: null } },
    ])

    const error = await request({ url: '/users' }).catch((e: unknown) => e)

    expect(error).toBeInstanceOf(ApiError)
    expect((error as ApiError).code).toBe(401)
    // 清态：token 与登录意图一起撤，重放未发生
    expect(store.accessToken).toBeNull()
    expect(localStorage.getItem('auth:logged-in')).toBeNull()
    expect(calls).toHaveLength(2)
  })

  it('重放请求再 401 不再刷新（防循环）', async () => {
    const store = useSessionStore()
    store.save('stale-token')
    const calls = stubFetch([
      { status: 401, body: { code: 401, message: '', data: null } },
      {
        status: 200,
        body: { code: 200, message: '', data: { accessToken: 'fresh', expiresIn: 7200 } },
      },
      { status: 401, body: { code: 401, message: '', data: null } },
    ])

    const error = await request({ url: '/users' }).catch((e: unknown) => e)

    expect(error).toBeInstanceOf(ApiError)
    expect(calls).toHaveLength(3)
    expect(calls.filter((call) => call.url.includes('/auth/refresh'))).toHaveLength(1)
  })

  it('F5 静默恢复：有意图无令牌时 refresh 成功回 true', async () => {
    const store = useSessionStore()
    localStorage.setItem('auth:logged-in', '1')
    stubFetch([
      {
        status: 200,
        body: { code: 200, message: '', data: { accessToken: 'restored', expiresIn: 7200 } },
      },
    ])

    expect(await store.ensureSession()).toBe(true)
    expect(store.accessToken).toBe('restored')
  })

  it('F5 恢复：无意图直接 false，refresh 失败清意图回 false', async () => {
    const store = useSessionStore()
    stubFetch([])
    expect(await store.ensureSession()).toBe(false)
    expect(useSessionStore().accessToken).toBeNull()

    // 有意图但 refresh 失败：清意图，false
    localStorage.setItem('auth:logged-in', '1')
    stubFetch([{ status: 401, body: { code: 401, message: '', data: null } }])
    expect(await store.ensureSession()).toBe(false)
    expect(localStorage.getItem('auth:logged-in')).toBeNull()
  })

  it('client 单例沿用生成配置（baseUrl /api）', () => {
    expect(client.getConfig().baseUrl).toBe('/api')
  })
})
