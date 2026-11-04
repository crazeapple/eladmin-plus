import { VueQueryPlugin } from '@tanstack/vue-query'
import { cleanup, fireEvent, render, waitFor } from '@testing-library/vue'
import { createPinia, setActivePinia } from 'pinia'
import type { Ref } from 'vue'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'

import { buildI18n } from '@/i18n'
import { useMenusStore } from '@/stores/menus'
import OperationLogs from '../index.vue'
import type { LogPageQuery } from '@/api/monitor'

function pageData() {
  return {
    content: [
      {
        id: 1,
        username: 'admin',
        ip: '10.0.0.2',
        description: '新增用户',
        elapsedMs: 123,
        requestParam: '{"username":"wangwu","password":"***"}',
        createdAt: '2026-11-01T08:30:00',
      },
      {
        id: 2,
        username: 'wangwu',
        ip: '192.168.1.8',
        description: '删除部门',
        elapsedMs: 45,
        requestParam: '[3]',
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
  exportMock: vi.fn().mockResolvedValue('operation_log.xlsx'),
  confirmMock: vi.fn().mockResolvedValue(true),
}))

// 捕获查询参数引用，断言筛选与重置的应用效果
let lastQuery: Ref<LogPageQuery> | null = null

vi.mock('@/api/monitor', async () => {
  const { ref } = await import('vue')
  return {
    useOperationLogPage: (query: Ref<LogPageQuery>) => {
      lastQuery = query
      return { data: ref(pageData()), isLoading: ref(false) }
    },
    useOperationLogRemove: () => ({ mutateAsync: removeMutate, isPending: ref(false) }),
    useOperationLogRemoveBatch: () => ({ mutateAsync: batchMutate, isPending: ref(false) }),
    useOperationLogClear: () => ({ mutateAsync: clearMutate, isPending: ref(false) }),
    exportOperationLogs: exportMock,
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
  render(OperationLogs, { global: { plugins: [pinia, buildI18n('zh-CN'), VueQueryPlugin] } })
}

describe('操作日志页（筛选、详情、删除清空与导出）', () => {
  it('列表渲染：操作人、描述与时间（ISO 的 T 换空格）在册', async () => {
    await renderPage()
    expect(document.body.textContent).toContain('admin')
    expect(document.body.textContent).toContain('新增用户')
    expect(document.body.textContent).toContain('2026-11-01 08:30:00')
  })

  it('筛选变更即重查：change 应用且分页重置 0', async () => {
    await renderPage()
    const usernameInput = document.querySelector(
      '[data-test="op-logs-filter-username"]',
    ) as HTMLInputElement
    usernameInput.value = 'adm'
    await fireEvent.input(usernameInput)
    await fireEvent.change(usernameInput)
    await waitFor(() => expect(lastQuery?.value.username).toBe('adm'))
    expect(lastQuery?.value.page).toBe(0)

    // 重置回全量
    await fireEvent.click(document.querySelector('[data-test="op-logs-reset"]')!)
    await waitFor(() => expect(lastQuery?.value.username).toBeUndefined())
    expect(lastQuery?.value.page).toBe(0)
  })

  it('行详情弹窗：请求参数与耗时等字段全文展示', async () => {
    await renderPage()
    await fireEvent.click(byTest('op-logs-detail')[0]!)
    const dialog = document.querySelector('[data-test="op-logs-detail-dialog"]')!
    expect(dialog.textContent).toContain('{"username":"wangwu","password":"***"}')
    expect(dialog.textContent).toContain('123 ms')
    expect(dialog.textContent).toContain('10.0.0.2')
    expect(dialog.textContent).toContain('2026-11-01 08:30:00')
  })

  it('单删：确认后调用端点，取消不调', async () => {
    await renderPage()
    await fireEvent.click(byTest('op-logs-remove')[0]!)
    await waitFor(() => expect(confirmMock).toHaveBeenCalledTimes(1))
    await waitFor(() => expect(removeMutate).toHaveBeenCalledWith(1))

    // 取消：确认弹窗拒绝即不调接口
    removeMutate.mockClear()
    confirmMock.mockRejectedValueOnce('cancel')
    await fireEvent.click(byTest('op-logs-remove')[0]!)
    await waitFor(() => expect(confirmMock).toHaveBeenCalledTimes(2))
    expect(removeMutate).not.toHaveBeenCalled()
  })

  it('批删：勾选行后批量按钮出现，确认后按所选 id 调用', async () => {
    await renderPage()
    const checkboxes = document.querySelectorAll('[data-test="admin-table-row-select"] input')
    await fireEvent.click(checkboxes[0]!)
    await fireEvent.click(checkboxes[1]!)
    await waitFor(() => expect(byTest('op-logs-batch-remove').length).toBe(1))
    await fireEvent.click(byTest('op-logs-batch-remove')[0]!)
    await waitFor(() => expect(confirmMock).toHaveBeenCalledTimes(1))
    expect(confirmMock.mock.calls[0]?.[0]).toContain('2')
    await waitFor(() => expect(batchMutate).toHaveBeenCalledWith([1, 2]))
  })

  it('清空：确认文案明示全表影响，取消不调接口', async () => {
    await renderPage()

    // 取消不调
    confirmMock.mockRejectedValueOnce('cancel')
    await fireEvent.click(byTest('op-logs-clear')[0]!)
    await waitFor(() => expect(confirmMock).toHaveBeenCalledTimes(1))
    expect(confirmMock.mock.calls[0]?.[0]).toContain('全部操作日志记录')
    expect(clearMutate).not.toHaveBeenCalled()

    // 确认即清
    await fireEvent.click(byTest('op-logs-clear')[0]!)
    await waitFor(() => expect(confirmMock).toHaveBeenCalledTimes(2))
    await waitFor(() => expect(clearMutate).toHaveBeenCalled())
  })

  it('导出：复用当前筛选并反馈落盘文件名', async () => {
    await renderPage()
    const usernameInput = document.querySelector(
      '[data-test="op-logs-filter-username"]',
    ) as HTMLInputElement
    usernameInput.value = 'admin'
    await fireEvent.input(usernameInput)
    await fireEvent.change(usernameInput)
    await waitFor(() => expect(lastQuery?.value.username).toBe('admin'))

    await fireEvent.click(byTest('op-logs-export')[0]!)
    await waitFor(() =>
      expect(exportMock).toHaveBeenCalledWith(expect.objectContaining({ username: 'admin' })),
    )
    await waitFor(() => expect(document.body.textContent).toContain('operation_log.xlsx'))
  })

  it('hasAuth 藏显：无 del 权限无删除清空批删，无 list 权限无详情导出', async () => {
    // 仅持 monitor:log:del：详情与导出隐藏，删除与清空在册
    await renderPage(['monitor:log:del'])
    expect(byTest('op-logs-detail').length).toBe(0)
    expect(byTest('op-logs-export').length).toBe(0)
    expect(byTest('op-logs-remove').length).toBe(2)
    expect(byTest('op-logs-clear').length).toBe(1)

    // 仅持 monitor:log:list：删除与清空隐藏，详情与导出在册
    await renderPage(['monitor:log:list'])
    expect(byTest('op-logs-remove').length).toBe(0)
    expect(byTest('op-logs-clear').length).toBe(0)
    expect(byTest('op-logs-detail').length).toBe(2)
    expect(byTest('op-logs-export').length).toBe(1)
  })
})
