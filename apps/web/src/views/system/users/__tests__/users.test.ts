import { VueQueryPlugin } from '@tanstack/vue-query'
import { cleanup, fireEvent, render, waitFor } from '@testing-library/vue'
import { createPinia, setActivePinia } from 'pinia'
import type { Ref } from 'vue'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'

import { buildI18n } from '@/i18n'
import type { UserPageQuery } from '@/api/users'
import { useMenusStore } from '@/stores/menus'
import Users from '../index.vue'

function deptTree() {
  return [
    {
      id: 1,
      parentId: null,
      name: '总部',
      sort: 1,
      enabled: 1,
      children: [{ id: 2, parentId: 1, name: '研发部', sort: 1, enabled: 1, children: [] }],
    },
  ]
}

function pageData() {
  return {
    content: [
      {
        id: 1,
        username: 'admin',
        nickName: '系统管理员',
        deptName: '总部',
        email: 'admin@example.com',
        enabled: 1,
        createdAt: '2026-11-01T00:00:00',
      },
      {
        id: 2,
        username: 'wangwu',
        nickName: '王五',
        deptName: '研发部',
        email: 'wangwu@example.com',
        enabled: 1,
        jobIds: [1],
        roleIds: [2],
        createdAt: '2026-11-02T00:00:00',
      },
    ],
    total: 2,
    page: 0,
    size: 20,
  }
}

const { enabledMutate, confirmMock } = vi.hoisted(() => ({
  enabledMutate: vi.fn().mockResolvedValue(undefined),
  confirmMock: vi.fn().mockResolvedValue(true),
}))

// 捕获查询参数引用，断言树选与筛选的应用效果
let lastQuery: Ref<UserPageQuery> | null = null

vi.mock('@/api/depts', async (importOriginal) => {
  const actual = await importOriginal<typeof import('@/api/depts')>()
  const { ref } = await import('vue')
  return { ...actual, useDeptTree: () => ({ data: ref(deptTree()) }) }
})

vi.mock('@/api/users', async (importOriginal) => {
  const actual = await importOriginal<typeof import('@/api/users')>()
  const { ref } = await import('vue')
  return {
    ...actual,
    useUserPage: (query: Ref<UserPageQuery>) => {
      lastQuery = query
      return { data: ref(pageData()), isLoading: ref(false) }
    },
    useUserEnabled: () => ({ mutateAsync: enabledMutate, isPending: ref(false) }),
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

async function renderPage(userId = 1) {
  const pinia = createPinia()
  setActivePinia(pinia)
  const menus = useMenusStore(pinia)
  menus.user = { id: userId, username: 'admin', nickName: '系统管理员' }
  menus.roles = ['ROLE_ROOT']
  render(Users, { global: { plugins: [pinia, buildI18n('zh-CN'), VueQueryPlugin] } })
}

describe('用户管理页（联动、启停与藏显）', () => {
  it('左树右表渲染：树节点与用户行在册', async () => {
    await renderPage()
    expect(document.body.textContent).toContain('总部')
    expect(document.body.textContent).toContain('admin')
    expect(document.body.textContent).toContain('wangwu')
  })

  it('点树节点即过滤，再点取消', async () => {
    await renderPage()
    const tree = document.querySelector('[data-test="users-dept-tree"]')!
    const deptNode = Array.from(tree.querySelectorAll('.el-tree-node__content')).find((el) =>
      el.textContent?.includes('研发部'),
    )!
    await fireEvent.click(deptNode)
    await waitFor(() => expect(lastQuery?.value.deptId).toBe(2))
    await fireEvent.click(deptNode)
    await waitFor(() => expect(lastQuery?.value.deptId).toBeUndefined())
  })

  it('筛选应用：查询后参数生效且 page 重置', async () => {
    await renderPage()
    const nameInput = document.querySelector('[data-test="users-filter-name"]') as HTMLInputElement
    nameInput.value = 'adm'
    await fireEvent.input(nameInput)
    await fireEvent.click(document.querySelector('[data-test="users-search"]')!)
    await waitFor(() => expect(lastQuery?.value.name).toBe('adm'))
    await waitFor(() => expect(lastQuery?.value.page).toBe(0))
  })

  it('自我保护藏显：当前登录用户与 admin 行无开关无删除', async () => {
    await renderPage(1)
    const rows = document.querySelectorAll('[data-test="users-table"] tbody tr')
    expect(rows.length).toBe(2)
    const adminRow = rows[0]!
    // admin（即当前登录行）：状态为 tag 展示无 switch，行内无删除按钮
    expect(adminRow.querySelector('[data-test="users-enabled"]')).toBeNull()
    expect(adminRow.querySelector('[data-test="users-remove"]')).toBeNull()
    // 非保护行：开关与操作按钮在册
    const normalRow = rows[1]!
    expect(normalRow.querySelector('[data-test="users-enabled"]')).toBeTruthy()
    expect(normalRow.querySelector('[data-test="users-remove"]')).toBeTruthy()
    expect(normalRow.querySelector('[data-test="users-edit"]')).toBeTruthy()
  })

  it('启停：非保护行确认后调用端点；后端拒绝消息直显', async () => {
    await renderPage(1)
    await fireEvent.click(byTest('users-enabled')[0]!)
    await waitFor(() => expect(confirmMock).toHaveBeenCalledTimes(1))
    await waitFor(() => expect(enabledMutate).toHaveBeenCalled())

    enabledMutate.mockRejectedValueOnce(new Error('角色越权，无法操作'))
    await fireEvent.click(byTest('users-enabled')[0]!)
    await waitFor(() => expect(enabledMutate).toHaveBeenCalledTimes(2))
    await waitFor(() => expect(document.body.textContent).toContain('角色越权，无法操作'))
  })
})
