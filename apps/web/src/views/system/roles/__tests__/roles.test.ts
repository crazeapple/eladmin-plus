import { VueQueryPlugin } from '@tanstack/vue-query'
import { cleanup, fireEvent, render, waitFor } from '@testing-library/vue'
import { createPinia, setActivePinia } from 'pinia'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'

import { buildI18n } from '@/i18n'
import { useMenusStore } from '@/stores/menus'
import Roles from '../index.vue'

function pageData() {
  return {
    content: [
      {
        id: 1,
        code: 'ROLE_ROOT',
        name: '内置超级管理员',
        level: 1,
        dataScope: 'ALL',
        enabled: 1,
        builtIn: true,
        createdAt: '2026-11-01T00:00:00',
      },
      {
        id: 2,
        code: 'ROLE_DEV',
        name: '研发角色',
        level: 10,
        dataScope: 'CUSTOM',
        customDeptIds: [2],
        enabled: 1,
        builtIn: false,
        createdAt: '2026-11-02T00:00:00',
      },
    ],
    total: 2,
    page: 0,
    size: 20,
  }
}

const { saveMutateAsync, removeMutateAsync, confirmMock, enFetch } = vi.hoisted(() => ({
  saveMutateAsync: vi.fn().mockResolvedValue(undefined),
  removeMutateAsync: vi.fn().mockResolvedValue(undefined),
  confirmMock: vi.fn().mockResolvedValue(true),
  enFetch: vi.fn().mockResolvedValue([{ id: 2, name: 'Dev Role' }]),
}))

vi.mock('@/api/roles', async (importOriginal) => {
  const actual = await importOriginal<typeof import('@/api/roles')>()
  const { ref } = await import('vue')
  return {
    ...actual,
    useRolePage: () => ({ data: ref(pageData()), isLoading: ref(false) }),
    useRoleSave: () => ({ mutateAsync: saveMutateAsync }),
    useRoleRemove: () => ({ mutateAsync: removeMutateAsync }),
    fetchRolePageEn: enFetch,
  }
})

vi.mock('@/api/depts', async (importOriginal) => {
  const actual = await importOriginal<typeof import('@/api/depts')>()
  const { ref } = await import('vue')
  return {
    ...actual,
    useDeptTree: () => ({
      data: ref([{ id: 2, parentId: 1, name: '研发部', sort: 1, enabled: 1, children: [] }]),
    }),
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
  render(Roles, { global: { plugins: [pinia, buildI18n('zh-CN'), VueQueryPlugin] } })
}

describe('角色管理页', () => {
  it('分页数据渲染，行操作按钮在册', async () => {
    await renderPage()
    expect(document.body.textContent).toContain('内置超级管理员')
    expect(document.body.textContent).toContain('研发角色')
    expect(byTest('roles-edit').length).toBe(2)
    expect(byTest('roles-delete').length).toBe(2)
  })

  it('编辑 CUSTOM 角色：自定义部门树随数据范围出现', async () => {
    await renderPage()
    await fireEvent.click(byTest('roles-edit')[1]!)
    const dialog = document.querySelector('[data-test="roles-dialog"]') as HTMLElement
    await waitFor(() => {
      // 回填 CUSTOM：插槽树选渲染在册
      expect(document.querySelector('[data-test="roles-custom-depts"]')).toBeTruthy()
      expect(dialog.querySelector('[data-field="nameEn"] input')).toBeTruthy()
    })
  })

  it('编辑内置角色：code 与 level 禁用', async () => {
    await renderPage()
    await fireEvent.click(byTest('roles-edit')[0]!)
    await waitFor(() => {
      const dialog = document.querySelector('[data-test="roles-dialog"]') as HTMLElement
      const codeInput = dialog.querySelector('[data-field="code"] input') as HTMLInputElement
      const levelInput = dialog.querySelector('[data-field="level"] input') as HTMLInputElement
      expect(codeInput?.disabled).toBe(true)
      expect(levelInput?.disabled).toBe(true)
    })
  })

  it('必填校验：空编码提交出字段级错误', async () => {
    await renderPage()
    await fireEvent.click(byTest('roles-create')[0]!)
    const submit = Array.from(document.querySelectorAll('button')).find(
      (b) => b.textContent?.trim() === '保存',
    )!
    await fireEvent.click(submit)
    // code 与 name 均必填，空提交出两个字段级错误
    await waitFor(() =>
      expect(document.querySelectorAll('[data-test="auto-form-field-error"]').length).toBe(2),
    )
  })

  it('删除确认：确认后调用删除，取消则不调', async () => {
    await renderPage()
    await fireEvent.click(byTest('roles-delete')[0]!)
    await waitFor(() => expect(removeMutateAsync).toHaveBeenCalled())
    expect(confirmMock).toHaveBeenCalledTimes(1)

    confirmMock.mockRejectedValueOnce(new Error('cancel'))
    await fireEvent.click(byTest('roles-delete')[1]!)
    await waitFor(() => expect(confirmMock).toHaveBeenCalledTimes(2))
    await waitFor(() => expect(removeMutateAsync).toHaveBeenCalledTimes(1))
  })
})
