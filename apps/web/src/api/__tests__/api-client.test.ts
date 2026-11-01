import { QueryClient } from '@tanstack/vue-query'
import { afterEach, describe, expect, it, vi } from 'vitest'

import { overview } from '../generated/sdk.gen'
import { ApiError, client } from '../client'
import { overviewStatsQueryOptions } from '../stats'

const okData = { userCount: 128, onlineCount: 3, todayVisitCount: 1024, logCount: 2048 }

function stubFetch(body: unknown, status: number) {
  return vi.fn(
    async () =>
      new Response(JSON.stringify(body), {
        status,
        headers: { 'content-type': 'application/json' },
      }),
  )
}

afterEach(() => {
  client.setConfig({ fetch: undefined })
})

describe('client envelope 解包', () => {
  it('2xx 取 data，调用方拿到纯负载', async () => {
    client.setConfig({ fetch: stubFetch({ code: 200, message: '', data: okData }, 200) })

    const { data } = await overview({ throwOnError: true })

    expect(data).toEqual(okData)
  })

  it('非 2xx 抛 ApiError(message) 挂 code', async () => {
    client.setConfig({
      fetch: stubFetch({ code: 400, message: '请求参数或操作不被接受', data: null }, 400),
    })

    const error = await overview({ throwOnError: true }).then(
      () => undefined,
      (e: unknown) => e,
    )

    expect(error).toBeInstanceOf(ApiError)
    expect((error as ApiError).message).toBe('请求参数或操作不被接受')
    expect((error as ApiError).code).toBe(400)
  })
})

describe('统计查询', () => {
  it('queryFn 解包后经 zod 校验', async () => {
    client.setConfig({ fetch: stubFetch({ code: 200, message: '', data: okData }, 200) })
    const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false } } })

    const data = await queryClient.fetchQuery(overviewStatsQueryOptions)

    expect(data.userCount).toBe(128n)
    expect(data.todayVisitCount).toBe(1024n)
  })

  it('失败沿 query 冒泡 ApiError', async () => {
    client.setConfig({
      fetch: stubFetch({ code: 500, message: '服务内部错误，请稍后再试', data: null }, 500),
    })
    const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false } } })

    const error = await queryClient.fetchQuery(overviewStatsQueryOptions).then(
      () => undefined,
      (e: unknown) => e,
    )

    expect(error).toBeInstanceOf(ApiError)
    expect((error as ApiError).code).toBe(500)
  })
})
