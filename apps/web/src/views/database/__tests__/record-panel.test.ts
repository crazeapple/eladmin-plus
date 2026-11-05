import { VueQueryPlugin } from '@tanstack/vue-query'
import { cleanup, fireEvent, render, waitFor } from '@testing-library/vue'
import { createPinia, setActivePinia } from 'pinia'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { ref } from 'vue'

import { buildI18n } from '@/i18n'
import RecordPanel from '../record-panel.vue'

const { removeMutateAsync, exportBackupRecord, confirmMock, listData, pageQueryRef } = vi.hoisted(
  () => ({
    removeMutateAsync: vi.fn().mockResolvedValue(undefined),
    exportBackupRecord: vi.fn().mockResolvedValue('eladmin_plus_20261106000000.sql'),
    confirmMock: vi.fn().mockResolvedValue(true),
    listData: {
      value: {
        content: [] as Array<Record<string, unknown>>,
        total: 0,
      },
    },
    pageQueryRef: vi.fn(),
  }),
)

vi.mock('@/api/database', () => ({
  useBackupRecords: (query: unknown) => {
    pageQueryRef(query)
    return { data: ref(listData.value), isLoading: ref(false) }
  },
  useBackupRecordRemove: () => ({ mutateAsync: removeMutateAsync }),
  exportBackupRecord,
}))

vi.mock('element-plus', async (importOriginal) => {
  const actual = await importOriginal<typeof import('element-plus')>()
  return { ...actual, ElMessageBox: { ...actual.ElMessageBox, confirm: confirmMock } }
})

function byTest(test: string): HTMLElement {
  const element = document.querySelector(`[data-test="${test}"]`)
  if (!element) {
    throw new Error(`element not found: [data-test="${test}"]`)
  }
  return element as HTMLElement
}

beforeEach(() => {
  vi.spyOn(window.navigator, 'language', 'get').mockReturnValue('zh-CN')
  listData.value = { content: [], total: 0 }
})

afterEach(() => {
  cleanup()
  localStorage.clear()
  vi.restoreAllMocks()
  vi.clearAllMocks()
})

async function renderPanel() {
  const pinia = createPinia()
  setActivePinia(pinia)
  useMenusStoreOf(pinia).roles = ['ROLE_ROOT']
  render(RecordPanel, { global: { plugins: [pinia, buildI18n('zh-CN'), VueQueryPlugin] } })
}

// 独立引入避免与 mock 时序相互牵动（storage 页同款）
import { useMenusStore } from '@/stores/menus'
function useMenusStoreOf(pinia: ReturnType<typeof createPinia>) {
  return useMenusStore(pinia)
}

describe('备份记录区块（列表、文件名筛选、行级导出与删除）', () => {
  it('列表渲染：库名、文件名、大小人性化、备份时间', async () => {
    listData.value = {
      content: [
        {
          id: 1,
          fileName: 'eladmin_plus_20261106000000.sql',
          dbName: 'eladmin_plus',
          size: 1536,
          createdAt: '2026-11-06T00:00:00',
        },
      ],
      total: 1,
    }
    await renderPanel()
    const rows = document.querySelectorAll('[data-test="database-record-table"] tbody tr')
    expect(rows.length).toBe(1)
    expect(document.body.textContent).toContain('eladmin_plus_20261106000000.sql')
    expect(document.body.textContent).toContain('1.5 KB')
    expect(document.body.textContent).toContain('2026-11-06 00:00:00')
  })

  it('文件名筛选：change 即应用入查询参数，清空与重置还原', async () => {
    await renderPanel()
    await fireEvent.update(byTest('database-record-filter'), 'eladmin_plus_2026')
    await fireEvent.change(byTest('database-record-filter'))
    await waitFor(() => {
      const query = (pageQueryRef.mock.calls.at(-1)?.[0] as { value: Record<string, unknown> })
        .value
      expect(query.filename).toBe('eladmin_plus_2026')
    })

    // 清空即还原
    await fireEvent.update(byTest('database-record-filter'), '')
    await fireEvent.change(byTest('database-record-filter'))
    await waitFor(() => {
      const query = (pageQueryRef.mock.calls.at(-1)?.[0] as { value: Record<string, unknown> })
        .value
      expect(query.filename).toBeUndefined()
    })

    // 重置按钮同效
    await fireEvent.update(byTest('database-record-filter'), '某文件名')
    await fireEvent.change(byTest('database-record-filter'))
    await waitFor(() => {
      const query = (pageQueryRef.mock.calls.at(-1)?.[0] as { value: Record<string, unknown> })
        .value
      expect(query.filename).toBe('某文件名')
    })
    await fireEvent.click(byTest('database-record-reset'))
    await waitFor(() => {
      const query = (pageQueryRef.mock.calls.at(-1)?.[0] as { value: Record<string, unknown> })
        .value
      expect(query.filename).toBeUndefined()
    })
  })

  it('行级导出：调下载封装并提示已落盘文件名；失败展示消息', async () => {
    listData.value = {
      content: [
        {
          id: 1,
          fileName: 'eladmin_plus_20261106000000.sql',
          dbName: 'eladmin_plus',
          size: 1536,
          createdAt: '2026-11-06T00:00:00',
        },
      ],
      total: 1,
    }
    await renderPanel()
    await fireEvent.click(byTest('database-record-export'))
    await waitFor(() => expect(exportBackupRecord).toHaveBeenCalledWith(1))
    await waitFor(() =>
      expect(document.body.textContent).toContain('已下载 eladmin_plus_20261106000000.sql'),
    )

    // 已删文件 404：后端消息直显
    exportBackupRecord.mockRejectedValueOnce(new Error('记录不存在'))
    await fireEvent.click(byTest('database-record-export'))
    await waitFor(() => {
      const messages = Array.from(document.querySelectorAll('.el-message')).map(
        (m) => m.textContent,
      )
      expect(messages.join()).toContain('记录不存在')
    })
  })

  it('行级删除：确认后调删除并提示成功；取消不调', async () => {
    listData.value = {
      content: [
        {
          id: 1,
          fileName: 'eladmin_plus_20261106000000.sql',
          dbName: 'eladmin_plus',
          size: 1536,
          createdAt: '2026-11-06T00:00:00',
        },
      ],
      total: 1,
    }
    await renderPanel()

    // 取消不调
    confirmMock.mockRejectedValueOnce('cancel')
    await fireEvent.click(byTest('database-record-delete'))
    await waitFor(() => expect(confirmMock).toHaveBeenCalledTimes(1))
    expect(removeMutateAsync).not.toHaveBeenCalled()

    // 确认即删
    await fireEvent.click(byTest('database-record-delete'))
    await waitFor(() => expect(confirmMock).toHaveBeenCalledTimes(2))
    await waitFor(() => expect(removeMutateAsync).toHaveBeenCalledWith(1))
    await waitFor(() => expect(document.body.textContent).toContain('删除成功'))
  })
})
