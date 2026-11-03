import { VueQueryPlugin } from '@tanstack/vue-query'
import { cleanup, fireEvent, render, waitFor } from '@testing-library/vue'
import { createPinia, setActivePinia } from 'pinia'
import type { Ref } from 'vue'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'

import { buildI18n } from '@/i18n'
import type { UserPageQuery } from '@/api/users'
import Users from '../index.vue'

function deptTree() {
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
    ],
    total: 1,
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

async function renderPage() {
  const pinia = createPinia()
  setActivePinia(pinia)
  render(Users, { global: { plugins: [pinia, buildI18n('zh-CN'), VueQueryPlugin] } })
}

describe('用户管理页（联动与启停）', () => {
  it('左树右表渲染：树节点与用户行在册', async () => {
    await renderPage()
    expect(document.body.textContent).toContain('总部')
    expect(document.body.textContent).toContain('研发部')
    expect(document.body.textContent).toContain('admin')
    expect(document.body.textContent).toContain('系统管理员')
  })

  it('点树节点即过滤，再点取消', async () => {
    await renderPage()
    const tree = document.querySelector('[data-test="users-dept-tree"]')!
    const deptNode = Array.from(tree.querySelectorAll('.el-tree-node__content')).find((el) =>
      el.textContent?.includes('研发部'),
    )!
    await fireEvent.click(deptNode)
    await waitFor(() => expect(lastQuery?.value.deptId).toBe(2))
    await waitFor(() => expect(lastQuery?.value.page).toBe(0))
    // 再点取消过滤
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

  it('停用确认与后端拒绝直显：自我保护 toast', async () => {
    enabledMutate.mockRejectedValueOnce(new Error('不能停用自己'))
    await renderPage()
    await fireEvent.click(document.querySelector('[data-test="users-enabled"]')!)
    await waitFor(() => expect(confirmMock).toHaveBeenCalledTimes(1))
    await waitFor(() => expect(enabledMutate).toHaveBeenCalled())
    // 后端保护消息 toast 直显
    await waitFor(() => expect(document.body.textContent).toContain('不能停用自己'))
  })
})
