/** 跨 tab 会话协调：Web Locks 串行化刷新 + BroadcastChannel 广播共享结果（环境缺失即降级为无协调）。 */

const LOCK_NAME = 'auth-refresh'
const CHANNEL_NAME = 'auth'

export type AuthBroadcast = { type: 'refreshed'; accessToken: string } | { type: 'logout' }

// 每次操作即取即用（低频调用，不做实例缓存——避免环境替换时的实例残留）
function channelOf(): BroadcastChannel | null {
  if (typeof BroadcastChannel === 'undefined') return null
  try {
    return new BroadcastChannel(CHANNEL_NAME)
  } catch {
    return null
  }
}

/** 发刷新结果广播（其他 tab 同步内存令牌） */
export function broadcastRefreshed(accessToken: string): void {
  channelOf()?.postMessage({ type: 'refreshed', accessToken } satisfies AuthBroadcast)
}

/** 发登出广播（其他 tab 全清） */
export function broadcastLogout(): void {
  channelOf()?.postMessage({ type: 'logout' } satisfies AuthBroadcast)
}

/** 订阅会话广播；返回退订函数 */
export function onAuthBroadcast(handler: (message: AuthBroadcast) => void): () => void {
  const bus = channelOf()
  if (!bus) return () => {}
  const listener = (event: MessageEvent) => {
    handler(event.data as AuthBroadcast)
  }
  bus.addEventListener('message', listener)
  return () => bus.removeEventListener('message', listener)
}

/** Web Locks 跨 tab 串行化；locks 不可用时直接执行 */
export async function withRefreshLock<T>(fn: () => Promise<T>): Promise<T> {
  const locks = (navigator as Navigator & { locks?: LockManagerLike }).locks
  if (!locks?.request) {
    return fn()
  }
  return locks.request(LOCK_NAME, fn)
}

interface LockManagerLike {
  request(name: string, callback: () => Promise<unknown>): Promise<unknown>
}
