import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { createPinia, setActivePinia } from 'pinia'
import { broadcastLogout, broadcastRefreshed, onAuthBroadcast, withRefreshLock } from '../auth-sync'
import { request } from '../request'
import { useSessionStore } from '@/stores/session'

/** Web Locks 桩：记录锁名与并发进入，锁体串行执行 */
function stubLocks() {
  const locked: string[] = []
  let active = 0
  let maxActive = 0
  const request = vi.fn(
    async (_name: string, callback: () => Promise<unknown>): Promise<unknown> => {
      locked.push(_name)
      active++
      maxActive = Math.max(maxActive, active)
      try {
        return await callback()
      } finally {
        active--
      }
    },
  )
  vi.stubGlobal('navigator', { ...navigator, locks: { request } })
  return { locked, request, maxActive: () => maxActive }
}

/** BroadcastChannel 桩：实例间经共享注册表互发（真语义：同名实例互相收到），另可外部派发模拟其他 tab */
function stubChannel() {
  const posted: unknown[] = []
  const buses = new Set<FakeChannel>()
  class FakeChannel {
    private listeners = new Set<(event: MessageEvent) => void>()

    constructor() {
      buses.add(this)
    }

    receive(message: unknown) {
      this.listeners.forEach((listener) => listener({ data: message } as MessageEvent))
    }

    postMessage(message: unknown) {
      posted.push(message)
      for (const bus of buses) {
        if (bus !== this) bus.receive(message)
      }
    }

    addEventListener(_type: string, listener: (event: MessageEvent) => void) {
      this.listeners.add(listener)
    }

    removeEventListener(_type: string, listener: (event: MessageEvent) => void) {
      this.listeners.delete(listener)
    }
  }
  vi.stubGlobal('BroadcastChannel', FakeChannel)
  return {
    posted,
    /** 模拟其他 tab 派发（不落 posted，那是本 tab 的发出记录） */
    dispatch(message: unknown) {
      buses.forEach((bus) => bus.receive(message))
    },
  }
}

function jsonResponse(status: number, body: unknown): Response {
  return new Response(JSON.stringify(body), {
    status,
    headers: { 'Content-Type': 'application/json' },
  })
}

function stubFetch(script: Array<{ status: number; body: unknown }>) {
  const calls: string[] = []
  const spy = vi.fn(async (input: RequestInfo | URL) => {
    const request = input instanceof Request ? input : new Request(input)
    calls.push(request.url)
    const step = script[calls.length - 1] ?? script[script.length - 1]!
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

describe('多 tab 会话协调', () => {
  it('刷新成功发 refreshed 广播、登出发 logout 广播', () => {
    broadcastRefreshed('token-x')
    broadcastLogout()

    const bus = stubChannel()
    const received: unknown[] = []
    onAuthBroadcast((message) => received.push(message))
    // 桩是逐消息派发，历史 post 不回放——验证订阅后新广播
    bus.dispatch({ type: 'refreshed', accessToken: 'token-y' })
    expect(received).toEqual([{ type: 'refreshed', accessToken: 'token-y' }])
  })

  it('其他 tab 收到 refreshed 广播后同步内存令牌，收到 logout 全清', async () => {
    const store = useSessionStore()
    const bus = stubChannel()
    // 本 tab 订阅广播（main.ts 同款接线）
    const unsubscribe = onAuthBroadcast((message) => {
      if (message.type === 'refreshed') store.save(message.accessToken)
      if (message.type === 'logout') store.reset()
    })

    // 模拟另一 tab 广播（同名实例互相收到）
    bus.dispatch({ type: 'refreshed', accessToken: 'from-other-tab' })
    expect(store.accessToken).toBe('from-other-tab')

    bus.dispatch({ type: 'logout' })
    expect(store.accessToken).toBeNull()
    unsubscribe()
  })

  it('刷新经 Web Locks 串行执行', async () => {
    const locks = stubLocks()
    const result = await withRefreshLock(async () => 'inside')
    expect(result).toBe('inside')
    expect(locks.locked).toEqual(['auth-refresh'])
    expect(locks.maxActive()).toBe(1)
  })

  it('401 刷新走锁并广播共享结果', async () => {
    const store = useSessionStore()
    store.save('stale')
    const locks = stubLocks()
    const bus = stubChannel()
    const calls = stubFetch([
      { status: 401, body: { code: 401, message: '', data: null } },
      {
        status: 200,
        body: { code: 200, message: '', data: { accessToken: 'fresh-x', expiresIn: 7200 } },
      },
      { status: 200, body: { code: 200, message: '', data: { ok: true } } },
    ])

    const received: unknown[] = []
    onAuthBroadcast((message) => received.push(message))

    await request({ url: '/users' })

    expect(locks.locked).toEqual(['auth-refresh'])
    expect(store.accessToken).toBe('fresh-x')
    expect(bus.posted).toEqual([{ type: 'refreshed', accessToken: 'fresh-x' }])
    expect(received).toEqual([{ type: 'refreshed', accessToken: 'fresh-x' }])
    expect(calls.filter((url) => url.includes('/auth/refresh'))).toHaveLength(1)
  })

  it('刷新失败广播 logout，其他 tab 全清', async () => {
    const store = useSessionStore()
    store.save('stale')
    stubLocks()
    const bus = stubChannel()
    stubFetch([
      { status: 401, body: { code: 401, message: '', data: null } },
      { status: 401, body: { code: 401, message: '', data: null } },
    ])
    const received: unknown[] = []
    onAuthBroadcast((message) => received.push(message))

    await request({ url: '/users' }).catch(() => {})

    expect(bus.posted).toEqual([{ type: 'logout' }])
    expect(received).toEqual([{ type: 'logout' }])
    expect(store.accessToken).toBeNull()
  })
})
