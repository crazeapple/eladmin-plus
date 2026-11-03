import { VueQueryPlugin } from '@tanstack/vue-query'
import { cleanup, fireEvent, render, screen, waitFor } from '@testing-library/vue'
import { createPinia, setActivePinia } from 'pinia'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'

import { buildI18n } from '@/i18n'
import { useMenusStore } from '@/stores/menus'
import Depts from '../index.vue'

/** 部门树（V3 种子形态：总部 → 研发部 / 市场部） */
function seedTree() {
  return [
    {
      id: 1,
      parentId: null,
      name: '总部',
      sort: 1,
      enabled: 1,
      children: [
        { id: 2, parentId: 1, name: '研发部', sort: 1, enabled: 1, children: [] },
        { id: 3, parentId: 1, name: '市场部', sort: 2, enabled: 1, children: [] },
      ],
    },
  ]
}

const { saveMutateAsync, removeMutateAsync, confirmMock, enFetch } = vi.hoisted(() => ({
  saveMutateAsync: vi.fn().mockResolvedValue(undefined),
  removeMutateAsync: vi.fn().mockResolvedValue(undefined),
  confirmMock: vi.fn().mockResolvedValue(true),
  enFetch: vi.fn().mockResolvedValue([{ id: 1, name: 'EN总部', children: [] }]),
}))

vi.mock('@/api/depts', async (importOriginal) => {
  const actual = await importOriginal<typeof import('@/api/depts')>()
  return {
    ...actual,
    useDeptTree: () => ({ data: { value: seedTree() }, isLoading: { value: false } }),
    useDeptSave: () => ({ mutateAsync: saveMutateAsync }),
    useDeptRemove: () => ({ mutateAsync: removeMutateAsync }),
    fetchDeptTreeEn: enFetch,
  }
})

vi.mock('element-plus', async (importOriginal) => {
  const actual = await importOriginal<typeof import('element-plus')>()
  return { ...actual, ElMessageBox: { ...actual.ElMessageBox, confirm: confirmMock } }
})

beforeEach(() => {
  vi.spyOn(window.navigator, 'language', 'get').mockReturnValue('zh-CN')
  // 诊断：Vue 警告与错误透出（vitest 拦 console）
})

afterEach(() => {
  cleanup()
  localStorage.clear()
  vi.restoreAllMocks()
  vi.clearAllMocks()
})

/** data-test 惯例的 DOM 查询辅助（RTL 的 testIdAttribute 配置在本仓不生效） */
function byTest(test: string): HTMLElement[] {
  return Array.from(document.querySelectorAll(`[data-test="${test}"]`)) as HTMLElement[]
}

async function renderPage() {
  const pinia = createPinia()
  setActivePinia(pinia)
  useMenusStore(pinia).roles = ['ROLE_ROOT']
  render(Depts, { global: { plugins: [pinia, buildI18n('zh-CN'), VueQueryPlugin] } })
}

describe('部门管理页', () => {
  it('树按种子数据渲染，状态与操作按钮在册', async () => {
    await renderPage()
    expect(screen.getByText('总部')).toBeTruthy()
    expect(screen.getByText('研发部')).toBeTruthy()
    expect(screen.getByText('市场部')).toBeTruthy()
    // 每行操作三按钮（ROLE_ROOT 全量可见）
    expect(byTest('depts-edit').length).toBe(3)
    expect(byTest('depts-delete').length).toBe(3)
    expect(byTest('depts-create').length).toBe(1)
  })

  it('必填校验：空名称提交出字段级错误', async () => {
    await renderPage()
    await fireEvent.click(byTest('depts-create')[0]!)
    const dialog = document.querySelector('[data-test="depts-dialog"]') as HTMLElement
    expect(dialog).toBeTruthy()
    // 点 button 本体（span 冒泡不触发 happy-dom 的按钮激活行为，form submit 不会发起）
    await fireEvent.click(screen.getByRole('button', { name: '保存' }))
    await waitFor(() =>
      expect(document.querySelectorAll('[data-test="auto-form-field-error"]').length).toBe(1),
    )
    // 提交全链（AutoForm emit → 保存 mutation → 关框）由 E2E 真浏览器覆盖
  })

  it('编辑回填英文名：en 展示名与默认名不一致时回填', async () => {
    await renderPage()
    await fireEvent.click(byTest('depts-edit')[0]!)
    await waitFor(() => {
      const dialog = document.querySelector('[data-test="depts-dialog"]') as HTMLElement
      const nameEn = dialog.querySelector('[data-field="nameEn"] input') as HTMLInputElement
      expect(nameEn?.value ?? '').toBe('EN总部')
    })
  })

  it('删除保护：确认框后调用删除，取消则不调', async () => {
    await renderPage()
    await fireEvent.click(byTest('depts-delete')[0]!)
    await waitFor(() => expect(removeMutateAsync).toHaveBeenCalled())
    expect(confirmMock).toHaveBeenCalled()

    confirmMock.mockRejectedValueOnce(new Error('cancel'))
    removeMutateAsync.mockClear()
    await fireEvent.click(byTest('depts-delete')[1]!)
    await waitFor(() => expect(confirmMock).toHaveBeenCalledTimes(2))
    await waitFor(() => expect(removeMutateAsync).not.toHaveBeenCalledTimes(2))
  })
})
