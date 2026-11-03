import { VueQueryPlugin } from '@tanstack/vue-query'
import { cleanup, fireEvent, render, waitFor } from '@testing-library/vue'
import { createPinia, setActivePinia } from 'pinia'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'

import { buildI18n } from '@/i18n'
import { useMenusStore } from '@/stores/menus'
import Jobs from '../index.vue'

const pageData = {
  content: [
    { id: 1, name: '开发工程师', sort: 1, enabled: 1 },
    { id: 2, name: '市场专员', sort: 2, enabled: 0 },
  ],
  total: 2,
  page: 0,
  size: 20,
}

const { saveMutateAsync, removeMutateAsync, confirmMock, requestMock } = vi.hoisted(() => ({
  saveMutateAsync: vi.fn().mockResolvedValue(undefined),
  removeMutateAsync: vi.fn().mockResolvedValue(undefined),
  confirmMock: vi.fn().mockResolvedValue(true),
  requestMock: vi.fn(),
}))

vi.mock('@/api/jobs', async (importOriginal) => {
  const actual = await importOriginal<typeof import('@/api/jobs')>()
  return {
    ...actual,
    useJobPage: () => ({ data: { value: pageData }, isLoading: { value: false } }),
    useJobSave: () => ({ mutateAsync: saveMutateAsync }),
    useJobRemove: () => ({ mutateAsync: removeMutateAsync }),
  }
})

// 页面直接 import request 做 en 回填查询：headers 带 en 时返回英文名页
vi.mock('@/api/request', async (importOriginal) => {
  const actual = await importOriginal<typeof import('@/api/request')>()
  return {
    ...actual,
    request: (options: { headers?: Record<string, string>; url: string }) => {
      requestMock(options)
      if (options.headers?.['Accept-Language'] === 'en') {
        return Promise.resolve({
          content: [{ id: 1, name: 'Dev Engineer', sort: 1, enabled: 1 }],
          total: 1,
          page: 0,
          size: 1,
        })
      }
      return Promise.resolve(pageData)
    },
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
})

async function renderPage() {
  const pinia = createPinia()
  setActivePinia(pinia)
  useMenusStore(pinia).roles = ['ROLE_ROOT']
  render(Jobs, { global: { plugins: [pinia, buildI18n('zh-CN'), VueQueryPlugin] } })
}

describe('岗位管理页', () => {
  it('分页数据渲染，行操作按钮在册', async () => {
    await renderPage()
    expect(document.body.textContent).toContain('开发工程师')
    expect(document.body.textContent).toContain('市场专员')
    expect(byTest('jobs-edit').length).toBe(2)
    expect(byTest('jobs-delete').length).toBe(2)
    expect(byTest('jobs-create').length).toBe(1)
  })

  it('必填校验：空名称提交出字段级错误', async () => {
    await renderPage()
    await fireEvent.click(byTest('jobs-create')[0]!)
    const dialog = document.querySelector('[data-test="jobs-dialog"]') as HTMLElement
    expect(dialog).toBeTruthy()
    await fireEvent.click(screenButton('保存'))
    await waitFor(() =>
      expect(document.querySelectorAll('[data-test="auto-form-field-error"]').length).toBe(1),
    )
  })

  it('编辑回填英文名：en 展示名与默认名不一致时回填', async () => {
    await renderPage()
    await fireEvent.click(byTest('jobs-edit')[0]!)
    await waitFor(() => {
      const dialog = document.querySelector('[data-test="jobs-dialog"]') as HTMLElement
      const nameEn = dialog.querySelector('[data-field="nameEn"] input') as HTMLInputElement
      expect(nameEn?.value ?? '').toBe('Dev Engineer')
    })
  })

  it('删除确认：确认后调用删除，取消则不调', async () => {
    await renderPage()
    await fireEvent.click(byTest('jobs-delete')[0]!)
    await waitFor(() => expect(removeMutateAsync).toHaveBeenCalled())
    expect(confirmMock).toHaveBeenCalledTimes(1)

    confirmMock.mockRejectedValueOnce(new Error('cancel'))
    await fireEvent.click(byTest('jobs-delete')[1]!)
    await waitFor(() => expect(confirmMock).toHaveBeenCalledTimes(2))
    await waitFor(() => expect(removeMutateAsync).toHaveBeenCalledTimes(1))
  })
})

/** 提交按钮定位（须点 button 本体，span 冒泡不触发 happy-dom 的按钮激活行为） */
function screenButton(name: string): HTMLElement {
  return Array.from(document.querySelectorAll('button')).find(
    (b) => b.textContent?.trim() === name,
  )!
}
