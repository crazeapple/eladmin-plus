import { VueQueryPlugin } from '@tanstack/vue-query'
import { cleanup, render } from '@testing-library/vue'
import { createPinia, setActivePinia } from 'pinia'
import { defineComponent, h, ref } from 'vue'
import type { Ref } from 'vue'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'

import { client } from '../client'
import { filenameFromDisposition } from '../download'
import {
  exportExceptionLogs,
  exportOperationLogs,
  useExceptionLogClear,
  useExceptionLogPage,
  useExceptionLogRemove,
  useExceptionLogRemoveBatch,
  useOnlineKick,
  useOnlineUsers,
  useOperationLogClear,
  useOperationLogPage,
  useOperationLogRemove,
  useOperationLogRemoveBatch,
  useServerMonitor,
} from '../monitor'
import type { LogPageQuery } from '../monitor'

/** fetch 替身：记录请求（归一为路径 + 查询串）并按用例给出响应（envelope 同形） */
const calls: Array<{ url: string; init: RequestInit }> = []

function stubFetch(respond: () => Response) {
  const fetchStub = vi.fn(async (input: string | URL | Request, init?: RequestInit) => {
    const request = input instanceof Request ? input : undefined
    const raw = request ? request.url : String(input)
    const parsed = new URL(raw, 'http://localhost')
    const bodyRaw = init?.body ?? request?.body
    const body = typeof bodyRaw === 'string' ? bodyRaw : bodyRaw ? await request?.text() : null
    calls.push({
      url: parsed.pathname + parsed.search,
      init: { method: init?.method ?? request?.method ?? 'GET', body },
    })
    return respond()
  })
  client.setConfig({ fetch: fetchStub as unknown as typeof fetch })
}

function jsonEnvelope(data: unknown): Response {
  return new Response(JSON.stringify({ code: 200, message: '', data }), {
    status: 200,
    headers: { 'content-type': 'application/json' },
  })
}

/** 组合式在注语境中取用：harness 组件里跑一遍工厂，收集返回值 */
function useInHarness<T>(factory: () => T): T {
  let result!: T
  const Harness = defineComponent(() => {
    result = factory()
    return () => h('div')
  })
  render(Harness, { global: { plugins: [createPinia(), VueQueryPlugin] } })
  return result
}

beforeEach(() => {
  setActivePinia(createPinia())
  vi.spyOn(window.navigator, 'language', 'get').mockReturnValue('zh-CN')
})

afterEach(() => {
  cleanup()
  client.setConfig({ fetch: undefined })
  calls.length = 0
  vi.restoreAllMocks()
  localStorage.clear()
})

describe('monitor api 层封装（四组契约）', () => {
  it('操作日志分页：筛选与分页进查询串，envelope 解包出页负载', async () => {
    stubFetch(() =>
      jsonEnvelope({
        content: [{ id: 1, username: 'admin', description: '新增用户' }],
        total: 1,
        page: 0,
        size: 20,
      }),
    )
    const query = ref<LogPageQuery>({
      page: 0,
      size: 20,
      username: 'admin',
      ip: '10.0.0.1',
      description: '用户',
      startTime: '2026-11-01T00:00:00',
      endTime: '2026-11-02T23:59:59',
    })
    const page = useInHarness(() => useOperationLogPage(query as Ref<LogPageQuery>))
    await vi.waitFor(() => expect(page.data.value).toBeTruthy())
    expect(calls[0]?.url).toBe(
      '/api/logs/operations?page=0&size=20&username=admin&ip=10.0.0.1&description=' +
        encodeURIComponent('用户') +
        '&startTime=2026-11-01T00%3A00%3A00&endTime=2026-11-02T23%3A59%3A59',
    )
    expect(page.data.value?.content?.[0]?.username).toBe('admin')
  })

  it('空筛选字段不入查询串', async () => {
    stubFetch(() => jsonEnvelope({ content: [], total: 0, page: 0, size: 20 }))
    const page = useInHarness(() =>
      useOperationLogPage(ref({ page: 2, size: 50, username: undefined })),
    )
    await vi.waitFor(() => expect(page.isFetching.value).toBe(false))
    expect(calls[0]?.url).toBe('/api/logs/operations?page=2&size=50')
  })

  it('操作日志单删批删清空走对应端点', async () => {
    stubFetch(() => jsonEnvelope(null))
    const remove = useInHarness(() => useOperationLogRemove())
    await remove.mutateAsync(7)
    expect(calls[0]?.url).toBe('/api/logs/operations/7')
    expect(calls[0]?.init.method).toBe('DELETE')

    const removeBatch = useInHarness(() => useOperationLogRemoveBatch())
    await removeBatch.mutateAsync([1, 2])
    expect(calls[1]?.url).toBe('/api/logs/operations')
    expect(calls[1]?.init.method).toBe('DELETE')
    expect(calls[1]?.init.body).toBe(JSON.stringify([1, 2]))

    const clear = useInHarness(() => useOperationLogClear())
    await clear.mutateAsync()
    expect(calls[2]?.url).toBe('/api/logs/operations/all')
    expect(calls[2]?.init.method).toBe('DELETE')
  })

  it('异常日志分页镜像：message 模糊筛选进查询串', async () => {
    stubFetch(() => jsonEnvelope({ content: [], total: 0, page: 0, size: 20 }))
    const page = useInHarness(() =>
      useExceptionLogPage(ref({ page: 0, size: 20, message: 'NullPointer' })),
    )
    await vi.waitFor(() => expect(page.isFetching.value).toBe(false))
    expect(calls[0]?.url).toBe('/api/logs/exceptions?page=0&size=20&message=NullPointer')
  })

  it('异常日志单删批删清空走对应端点', async () => {
    stubFetch(() => jsonEnvelope(null))
    const remove = useInHarness(() => useExceptionLogRemove())
    await remove.mutateAsync(3)
    expect(calls[0]?.url).toBe('/api/logs/exceptions/3')

    const removeBatch = useInHarness(() => useExceptionLogRemoveBatch())
    await removeBatch.mutateAsync([4, 5])
    expect(calls[1]?.url).toBe('/api/logs/exceptions')
    expect(calls[1]?.init.body).toBe(JSON.stringify([4, 5]))

    const clear = useInHarness(() => useExceptionLogClear())
    await clear.mutateAsync()
    expect(calls[2]?.url).toBe('/api/logs/exceptions/all')
  })

  it('在线用户列表与强退走对应端点', async () => {
    stubFetch(() => jsonEnvelope([{ userId: 9, username: 'admin', browser: 'Chrome' }]))
    const list = useInHarness(() => useOnlineUsers())
    await vi.waitFor(() => expect(list.data.value).toBeTruthy())
    expect(calls[0]?.url).toBe('/api/online')
    expect(list.data.value?.[0]?.username).toBe('admin')

    const kick = useInHarness(() => useOnlineKick())
    await kick.mutateAsync(9)
    expect(calls[1]?.url).toBe('/api/online/9')
    expect(calls[1]?.init.method).toBe('DELETE')
  })

  it('服务监控指标走单端点', async () => {
    stubFetch(() => jsonEnvelope({ cpu: { usedPercent: 12.5 }, jvm: null }))
    const monitor = useInHarness(() => useServerMonitor())
    await vi.waitFor(() => expect(monitor.data.value).toBeTruthy())
    expect(calls[0]?.url).toBe('/api/monitor/server')
    expect(monitor.data.value?.cpu?.usedPercent).toBe(12.5)
    expect(monitor.data.value?.jvm ?? null).toBeNull()
  })
})

describe('文件下载封装（blob + Content-Disposition 文件名）', () => {
  const xlsxHeaders = {
    'content-type': 'application/vnd.openxmlformats-officedocument.spreadsheetml.sheet',
    'content-disposition': 'attachment; filename=operation_log.xlsx',
  }

  beforeEach(() => {
    URL.createObjectURL = vi.fn(() => 'blob:mock-url')
    URL.revokeObjectURL = vi.fn()
    vi.spyOn(HTMLAnchorElement.prototype, 'click').mockImplementation(() => {})
  })

  it('导出请求走 blob 解析，触发保存且文件名取响应头', async () => {
    stubFetch(() => new Response('xlsx-bytes', { status: 200, headers: xlsxHeaders }))
    const filename = await exportOperationLogs({ page: 0, size: 20, username: 'admin' })
    expect(calls[0]?.url).toBe('/api/logs/operations/export?username=admin')
    expect(filename).toBe('operation_log.xlsx')
    expect(URL.createObjectURL).toHaveBeenCalledTimes(1)
    const anchor = HTMLAnchorElement.prototype.click as ReturnType<typeof vi.spyOn>
    expect(anchor).toHaveBeenCalledTimes(1)
    expect(URL.revokeObjectURL).toHaveBeenCalledWith('blob:mock-url')
  })

  it('异常日志导出同构：复用筛选条件', async () => {
    stubFetch(() => new Response('xlsx-bytes', { status: 200, headers: xlsxHeaders }))
    const filename = await exportExceptionLogs({ page: 0, size: 20, message: 'Boom' })
    expect(calls[0]?.url).toBe('/api/logs/exceptions/export?message=Boom')
    expect(filename).toBe('operation_log.xlsx')
  })
})

describe('Content-Disposition 文件名解析', () => {
  it('带引号、无引号与缺失三形态', () => {
    expect(filenameFromDisposition('attachment; filename="op(1).xlsx"')).toBe('op(1).xlsx')
    expect(filenameFromDisposition('attachment; filename=exception_log.xlsx')).toBe(
      'exception_log.xlsx',
    )
    expect(filenameFromDisposition('attachment')).toBeNull()
    expect(filenameFromDisposition(null)).toBeNull()
  })
})
