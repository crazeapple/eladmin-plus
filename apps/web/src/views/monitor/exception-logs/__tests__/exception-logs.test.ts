import { VueQueryPlugin } from '@tanstack/vue-query'
import { cleanup, fireEvent, render, waitFor } from '@testing-library/vue'
import { createPinia, setActivePinia } from 'pinia'
import type { Ref } from 'vue'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'

import { buildI18n } from '@/i18n'
import { useMenusStore } from '@/stores/menus'
import ExceptionLogs from '../index.vue'
import type { ExceptionLogQuery } from '@/api/monitor'

const STACK_TEXT = [
  'java.lang.NullPointerException: boom',
  '\tat com.eladminplus.monitor.BoomController.hit(BoomController.java:42)',
  '\tat java.base/jdk.internal.reflect.Method.invoke(Method.java:568)',
].join('\n')

function pageData() {
  return {
    content: [
      {
        id: 1,
        username: 'admin',
        ip: '10.0.0.2',
        name: 'NullPointerException',
        message: 'boom',
        stackTrace: STACK_TEXT,
        createdAt: '2026-11-01T08:30:00',
      },
      {
        id: 2,
        username: null,
        ip: '192.168.1.8',
        name: 'IllegalArgumentException',
        message: 'bad arg',
        stackTrace: 'java.lang.IllegalArgumentException: bad arg',
        createdAt: '2026-11-02T09:00:00',
      },
    ],
    total: 2,
    page: 0,
    size: 20,
  }
}

const { removeMutate, batchMutate, clearMutate, exportMock, confirmMock } = vi.hoisted(() => ({
  removeMutate: vi.fn().mockResolvedValue(undefined),
  batchMutate: vi.fn().mockResolvedValue(undefined),
  clearMutate: vi.fn().mockResolvedValue(undefined),
  exportMock: vi.fn().mockResolvedValue('exception_log.xlsx'),
  confirmMock: vi.fn().mockResolvedValue(true),
}))

const writeTextMock = vi.fn().mockResolvedValue(undefined)

// 捕获查询参数引用，断言筛选与重置的应用效果
let lastQuery: Ref<ExceptionLogQuery> | null = null

vi.mock('@/api/monitor', async () => {
  const { ref } = await import('vue')
  return {
    useExceptionLogPage: (query: Ref<ExceptionLogQuery>) => {
      lastQuery = query
      return { data: ref(pageData()), isLoading: ref(false) }
    },
    useExceptionLogRemove: () => ({ mutateAsync: removeMutate, isPending: ref(false) }),
    useExceptionLogRemoveBatch: () => ({ mutateAsync: batchMutate, isPending: ref(false) }),
    useExceptionLogClear: () => ({ mutateAsync: clearMutate, isPending: ref(false) }),
    exportExceptionLogs: exportMock,
  }
})

vi.mock('element-plus', async (importOriginal) => {
  const actual = await importOriginal<typeof import('element-plus')>()
  return { ...actual, ElMessageBox: { ...actual.ElMessageBox, confirm: confirmMock } }
})

function byTest(test: string): HTMLElement[] {
  return Array.from(document.querySelectorAll(`[data-test="${test}"]`)) as HTMLElement[]
}

beforeEach(() => {
  vi.spyOn(window.navigator, 'language', 'get').mockReturnValue('zh-CN')
  Object.defineProperty(window.navigator, 'clipboard', {
    value: { writeText: writeTextMock },
    configurable: true,
  })
})

afterEach(() => {
  cleanup()
  localStorage.clear()
  vi.restoreAllMocks()
  vi.clearAllMocks()
  lastQuery = null
})

async function renderPage(permissions: string[] | 'root' = 'root') {
  cleanup()
  const pinia = createPinia()
  setActivePinia(pinia)
  const menus = useMenusStore(pinia)
  menus.user = { id: 1, username: 'admin', nickName: '系统管理员' }
  if (permissions === 'root') {
    menus.roles = ['ROLE_ROOT']
  } else {
    menus.permissions = permissions
  }
  render(ExceptionLogs, { global: { plugins: [pinia, buildI18n('zh-CN'), VueQueryPlugin] } })
}

describe('异常日志页（筛选、堆栈抽屉与删除清空导出）', () => {
  it('列表渲染：异常类名、消息与时间在册', async () => {
    await renderPage()
    expect(document.body.textContent).toContain('NullPointerException')
    expect(document.body.textContent).toContain('boom')
    expect(document.body.textContent).toContain('2026-11-01 08:30:00')
  })

  it('message 模糊筛选变更即重查并重置分页', async () => {
    await renderPage()
    const messageInput = document.querySelector(
      '[data-test="ex-logs-filter-message"]',
    ) as HTMLInputElement
    messageInput.value = 'bad'
    await fireEvent.input(messageInput)
    await fireEvent.change(messageInput)
    await waitFor(() => expect(lastQuery?.value.message).toBe('bad'))
    expect(lastQuery?.value.page).toBe(0)

    // 重置回全量
    await fireEvent.click(document.querySelector('[data-test="ex-logs-reset"]')!)
    await waitFor(() => expect(lastQuery?.value.message).toBeUndefined())
  })

  it('堆栈抽屉：全文滚动展示，复制按钮写入剪贴板并反馈', async () => {
    await renderPage()
    await fireEvent.click(byTest('ex-logs-stack')[0]!)
    const drawer = document.querySelector('[data-test="ex-logs-stack-drawer"]')!
    expect(drawer.textContent).toContain(STACK_TEXT)
    expect(drawer.textContent).toContain('boom')

    await fireEvent.click(document.querySelector('[data-test="ex-logs-stack-copy"]')!)
    await waitFor(() => expect(writeTextMock).toHaveBeenCalledWith(STACK_TEXT))
    await waitFor(() => expect(document.body.textContent).toContain('已复制'))
  })

  it('单删批删清空：确认后调用端点，取消不调', async () => {
    await renderPage()

    // 取消不调
    confirmMock.mockRejectedValueOnce('cancel')
    await fireEvent.click(byTest('ex-logs-remove')[0]!)
    await waitFor(() => expect(confirmMock).toHaveBeenCalledTimes(1))
    expect(removeMutate).not.toHaveBeenCalled()

    // 确认即删
    await fireEvent.click(byTest('ex-logs-remove')[0]!)
    await waitFor(() => expect(confirmMock).toHaveBeenCalledTimes(2))
    await waitFor(() => expect(removeMutate).toHaveBeenCalledWith(1))

    // 批删
    const checkboxes = document.querySelectorAll('[data-test="admin-table-row-select"] input')
    await fireEvent.click(checkboxes[0]!)
    await fireEvent.click(checkboxes[1]!)
    await waitFor(() => expect(byTest('ex-logs-batch-remove').length).toBe(1))
    await fireEvent.click(byTest('ex-logs-batch-remove')[0]!)
    await waitFor(() => expect(batchMutate).toHaveBeenCalledWith([1, 2]))

    // 清空
    await fireEvent.click(byTest('ex-logs-clear')[0]!)
    await waitFor(() => expect(confirmMock.mock.calls.at(-1)?.[0]).toContain('全部异常日志记录'))
    await waitFor(() => expect(clearMutate).toHaveBeenCalled())
  })

  it('导出：复用当前筛选并反馈落盘文件名', async () => {
    await renderPage()
    await fireEvent.click(byTest('ex-logs-export')[0]!)
    await waitFor(() => expect(exportMock).toHaveBeenCalled())
    await waitFor(() => expect(document.body.textContent).toContain('exception_log.xlsx'))
  })

  it('hasAuth 藏显：共用 log 组权限按点藏显', async () => {
    await renderPage(['monitor:log:del'])
    expect(byTest('ex-logs-stack').length).toBe(0)
    expect(byTest('ex-logs-export').length).toBe(0)
    expect(byTest('ex-logs-remove').length).toBe(2)
    expect(byTest('ex-logs-clear').length).toBe(1)

    await renderPage(['monitor:log:list'])
    expect(byTest('ex-logs-remove').length).toBe(0)
    expect(byTest('ex-logs-clear').length).toBe(0)
    expect(byTest('ex-logs-stack').length).toBe(2)
    expect(byTest('ex-logs-export').length).toBe(1)
  })
})
