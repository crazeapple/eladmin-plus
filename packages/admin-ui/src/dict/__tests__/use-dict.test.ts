import { describe, expect, it, vi } from 'vitest'
import { setDictSource, useDict } from '../use-dict'
import type { DictOption, DictSource } from '../types'

function deferred<T>() {
  let resolve!: (value: T) => void
  const promise = new Promise<T>((resolveFn) => {
    resolve = resolveFn
  })
  return { promise, resolve }
}

describe('useDict', () => {
  it('内置 mock 源开箱可用', async () => {
    const { options, loading } = useDict('sys_normal_disable')

    await vi.waitFor(() => expect(loading.value).toBe(false))
    expect(options.value).toEqual([
      { label: '正常', value: '0' },
      { label: '停用', value: '1' },
    ])
  })

  it('Map 缓存命中：同 type 二次调用不再请求数据源', async () => {
    const source = vi.fn(async (): Promise<DictOption[]> => [{ label: '启用', value: '1' }])
    setDictSource(source as DictSource)

    const first = useDict('sys_yes_no')
    await vi.waitFor(() => expect(first.loading.value).toBe(false))

    const second = useDict('sys_yes_no')
    expect(second.options.value).toEqual([{ label: '启用', value: '1' }])
    expect(second.loading.value).toBe(false)
    expect(source).toHaveBeenCalledTimes(1)
  })

  it('同 type 并发去重：共享同一 promise，数据源只请求一次', async () => {
    const gate = deferred<DictOption[]>()
    const source = vi.fn(() => gate.promise)
    setDictSource(source as DictSource)

    const a = useDict('sys_concurrent')
    const b = useDict('sys_concurrent')

    expect(source).toHaveBeenCalledTimes(1)
    gate.resolve([{ label: '并发', value: 'c' }])
    await vi.waitFor(() => {
      expect(a.options.value).toEqual([{ label: '并发', value: 'c' }])
      expect(b.options.value).toEqual([{ label: '并发', value: 'c' }])
      expect(a.loading.value).toBe(false)
      expect(b.loading.value).toBe(false)
    })
  })

  it('invalidate 清缓存后重拉：换源即生效', async () => {
    const sourceA = vi.fn(async (): Promise<DictOption[]> => [{ label: '旧值', value: 'old' }])
    setDictSource(sourceA as DictSource)
    const dict = useDict('sys_refresh')
    await vi.waitFor(() => expect(dict.options.value).toHaveLength(1))

    const sourceB = vi.fn(async (): Promise<DictOption[]> => [{ label: '新值', value: 'new' }])
    setDictSource(sourceB as DictSource)
    await dict.invalidate()

    expect(dict.options.value).toEqual([{ label: '新值', value: 'new' }])

    // 缓存已清：新调用者重新拉取（走新源）
    const fresh = useDict('sys_refresh')
    expect(fresh.options.value).toEqual([{ label: '新值', value: 'new' }])
    expect(sourceB).toHaveBeenCalledTimes(1)
    expect(sourceA).toHaveBeenCalledTimes(1)
  })

  it('数据源失败不阻塞：loading 复位、options 空态、可重试', async () => {
    const failing = vi.fn(async (): Promise<DictOption[]> => {
      throw new Error('源挂了')
    })
    setDictSource(failing as DictSource)

    const dict = useDict('sys_fail')
    await vi.waitFor(() => expect(dict.loading.value).toBe(false))
    expect(dict.options.value).toEqual([])

    const recovered = vi.fn(async (): Promise<DictOption[]> => [{ label: '恢复', value: 'r' }])
    setDictSource(recovered as DictSource)
    await dict.invalidate()
    expect(dict.options.value).toEqual([{ label: '恢复', value: 'r' }])
  })
})
