import { VueQueryPlugin } from '@tanstack/vue-query'
import { cleanup, fireEvent, render, waitFor } from '@testing-library/vue'
import { createPinia, setActivePinia } from 'pinia'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { ref } from 'vue'

import { buildI18n } from '@/i18n'
import StoragePage from '../index.vue'

const { uploadMutate, removeMutate, confirmMock, pageQueryRef } = vi.hoisted(() => ({
  uploadMutate: vi.fn().mockResolvedValue(undefined),
  removeMutate: vi.fn().mockResolvedValue(undefined),
  confirmMock: vi.fn().mockResolvedValue(true),
  pageQueryRef: vi.fn(),
}))

/** 列表数据源（各用例自行赋值） */
let listData: {
  content?: Array<Record<string, unknown>>
  total?: number
} = {}

vi.mock('@/api/storage', () => ({
  useStoragePage: (query: unknown) => {
    pageQueryRef(query)
    return { data: ref(listData), isLoading: ref(false) }
  },
  useStorageUpload: () => ({ mutateAsync: uploadMutate }),
  useStorageRemove: () => ({ mutateAsync: removeMutate }),
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
  listData = {}
})

afterEach(() => {
  cleanup()
  localStorage.clear()
  vi.restoreAllMocks()
  vi.clearAllMocks()
})

async function renderPage(permissions: string[] | 'root' = 'root') {
  cleanup()
  const pinia = createPinia()
  setActivePinia(pinia)
  const menus = useMenusStoreOf(pinia)
  menus.user = { id: 1, username: 'admin', nickName: '系统管理员' }
  if (permissions === 'root') {
    menus.roles = ['ROLE_ROOT']
  } else {
    menus.permissions = permissions
  }
  render(StoragePage, { global: { plugins: [pinia, buildI18n('zh-CN'), VueQueryPlugin] } })
}

// 独立引入避免与 mock 时序相互牵动
import { useMenusStore } from '@/stores/menus'
function useMenusStoreOf(pinia: ReturnType<typeof createPinia>) {
  return useMenusStore(pinia)
}

describe('存储管理页（列表、三维筛选、上传与删除）', () => {
  it('列表渲染：原名、大小人性化、上传人、时间显示', async () => {
    listData = {
      content: [
        {
          id: 1,
          name: '报表.xlsx',
          ext: 'xlsx',
          size: 1536,
          mime: 'application/vnd.openxmlformats-officedocument.spreadsheetml.sheet',
          engine: 'rustfs',
          uploader: 'admin',
          createdAt: '2026-11-05T09:30:00',
        },
        {
          id: 2,
          name: '小文件.txt',
          ext: 'txt',
          size: 512,
          mime: 'text/plain',
          engine: 'local',
          uploader: 'wangwu',
          createdAt: '2026-11-04T10:00:00',
        },
      ],
      total: 2,
    }
    await renderPage()
    const rows = document.querySelectorAll('[data-test="storage-table"] tbody tr')
    expect(rows.length).toBe(2)
    expect(document.body.textContent).toContain('报表.xlsx')
    expect(document.body.textContent).toContain('1.5 KB')
    expect(document.body.textContent).toContain('512 B')
    expect(document.body.textContent).toContain('wangwu')
    expect(document.body.textContent).toContain('2026-11-05 09:30:00')
  })

  it('三维筛选：文本回车即应用且入查询参数，重置清空', async () => {
    await renderPage()
    await fireEvent.update(byTest('storage-filter-name'), '报表')
    await fireEvent.update(byTest('storage-filter-uploader'), 'admin')
    await fireEvent.keyUp(byTest('storage-filter-name'), { key: 'Enter' })
    await fireEvent.update(byTest('storage-filter-name'), '')
    // change 事件（回车 / 失焦）触发 applyFilters
    await fireEvent.change(byTest('storage-filter-uploader'), { value: '' })
    await waitFor(() => {
      const query = (pageQueryRef.mock.calls.at(-1)?.[0] as { value: Record<string, unknown> })
        .value
      expect(query.uploader).toBe('admin')
    })
    await fireEvent.click(byTest('storage-reset'))
    await waitFor(() => {
      const query = (pageQueryRef.mock.calls.at(-1)?.[0] as { value: Record<string, unknown> })
        .value
      expect(query.uploader).toBeUndefined()
      expect(query.name).toBeUndefined()
    })
  })

  it('上传：选文件即直传并提示成功；失败展示后端消息（413 通路）', async () => {
    await renderPage()
    const file = new File(['file-bytes'], 'e2e-报表.txt', { type: 'text/plain' })
    const input = document.querySelector<HTMLInputElement>(
      '[data-test="storage-upload-picker"] input[type="file"]',
    )
    await fireEvent.change(input!, { target: { files: [file] } })
    await waitFor(() => expect(uploadMutate).toHaveBeenCalledWith(file))
    await waitFor(() => expect(document.body.textContent).toContain('已上传 e2e-报表.txt'))

    uploadMutate.mockRejectedValueOnce(new Error('上传文件大小超出限制'))
    await fireEvent.change(input!, { target: { files: [file] } })
    await waitFor(() => expect(document.body.textContent).toContain('上传文件大小超出限制'))
  })

  it('删除：确认后调端点并提示成功；取消不调', async () => {
    listData = {
      content: [
        {
          id: 1,
          name: '报表.xlsx',
          size: 1536,
          uploader: 'admin',
          createdAt: '2026-11-05T09:30:00',
        },
      ],
      total: 1,
    }
    await renderPage()

    // 取消不调
    confirmMock.mockRejectedValueOnce('cancel')
    await fireEvent.click(byTest('storage-remove'))
    await waitFor(() => expect(confirmMock).toHaveBeenCalledTimes(1))
    expect(removeMutate).not.toHaveBeenCalled()

    // 确认即删
    await fireEvent.click(byTest('storage-remove'))
    await waitFor(() => expect(confirmMock).toHaveBeenCalledTimes(2))
    await waitFor(() => expect(removeMutate).toHaveBeenCalledWith(1))
    await waitFor(() => expect(document.body.textContent).toContain('删除成功'))
  })

  it('hasAuth 藏显：无 storage:upload 无上传按钮、无 storage:del 无删除按钮', async () => {
    listData = {
      content: [
        {
          id: 1,
          name: '报表.xlsx',
          size: 1536,
          uploader: 'admin',
          createdAt: '2026-11-05T09:30:00',
        },
      ],
      total: 1,
    }
    await renderPage(['storage:list'])
    expect(document.querySelectorAll('[data-test="storage-upload"]').length).toBe(0)
    expect(document.querySelectorAll('[data-test="storage-remove"]').length).toBe(0)

    await renderPage(['storage:list', 'storage:upload', 'storage:del'])
    expect(byTest('storage-upload')).toBeTruthy()
    expect(byTest('storage-remove')).toBeTruthy()
  })
})
