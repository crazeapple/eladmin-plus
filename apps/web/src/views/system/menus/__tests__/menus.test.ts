import { VueQueryPlugin } from '@tanstack/vue-query'
import { cleanup, fireEvent, render, waitFor } from '@testing-library/vue'
import { createPinia, setActivePinia } from 'pinia'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'

import { buildI18n } from '@/i18n'
import { useMenusStore } from '@/stores/menus'
import Menus from '../index.vue'
import { componentPathSelectOptions, i18nKeySelectOptions } from '../form-options'
import { typeFields } from '../type-fields'
import type { MenuNode } from '@/api/generated'

const treeData: MenuNode[] = [
  {
    id: 1,
    type: 0,
    i18nKey: 'routes.system',
    noCache: false,
    sort: 4,
    children: [
      {
        id: 2,
        parentId: 1,
        type: 1,
        i18nKey: 'routes.systemUsers',
        componentPathKey: 'system/users',
        noCache: false,
        sort: 1,
        children: [],
      },
      {
        id: 3,
        parentId: 1,
        type: 1,
        i18nKey: 'routes.systemRoles',
        componentPathKey: 'system/roles',
        noCache: false,
        sort: 2,
        children: [
          {
            id: 4,
            parentId: 3,
            type: 2,
            permission: 'system:role:list',
            i18nKey: 'perms.systemRoleList',
            noCache: false,
            sort: 1,
            children: [],
          },
        ],
      },
    ],
  },
]

const { saveMutateAsync, removeMutateAsync, confirmMock } = vi.hoisted(() => ({
  saveMutateAsync: vi.fn().mockResolvedValue(undefined),
  removeMutateAsync: vi.fn().mockResolvedValue(undefined),
  confirmMock: vi.fn().mockResolvedValue(true),
}))

vi.mock('@/api/menus', async (importOriginal) => {
  const actual = await importOriginal<typeof import('@/api/menus')>()
  const { ref } = await import('vue')
  return {
    ...actual,
    useMenuTree: () => ({ data: ref(treeData), isLoading: ref(false) }),
    useMenuSave: () => ({ mutateAsync: saveMutateAsync }),
    useMenuRemove: () => ({ mutateAsync: removeMutateAsync }),
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
  render(Menus, { global: { plugins: [pinia, buildI18n('zh-CN'), VueQueryPlugin] } })
}

/** en 实例渲染：配置域词条随实例语言即时跟随 */
async function renderPageEn() {
  const pinia = createPinia()
  setActivePinia(pinia)
  useMenusStore(pinia).roles = ['ROLE_ROOT']
  render(Menus, { global: { plugins: [pinia, buildI18n('en'), VueQueryPlugin] } })
}

describe('菜单管理页', () => {
  it('en 实例下页面文案取 en 包（语言跟随）', async () => {
    await renderPageEn()
    await waitFor(() => {
      // 树节点标题（System / Users）与工具栏按钮（Add Menu）均取 en 包
      expect(document.body.textContent).toContain('System')
      expect(document.body.textContent).toContain('Users')
      expect(document.body.textContent).toContain('Add Menu')
    })
  })

  it('三型节点区分渲染：目录 / 页面 / 按钮各具标识与元信息', async () => {
    await renderPage()
    // 目录型带目录标识
    expect(document.body.textContent).toContain('目录')
    // 页面节点标题取语言包值
    expect(document.body.textContent).toContain('用户管理')
    // 按钮节点带权限标识元信息与标题文案
    expect(byTest('menus-perm-node').length).toBe(1)
    expect(document.body.textContent).toContain('system:role:list')
    expect(document.body.textContent).toContain('角色查询')
    // 行级编辑 / 删除按钮逐节点在册（4 节点）
    expect(byTest('menus-edit').length).toBe(4)
    expect(byTest('menus-delete').length).toBe(4)
  })

  it('新增表单 type 联动：菜单型 7 字段，切按钮型字段集收缩', async () => {
    await renderPage()
    await fireEvent.click(byTest('menus-create')[0]!)
    const dialog = document.querySelector('[data-test="menus-dialog"]') as HTMLElement
    await waitFor(() => expect(dialog).toBeTruthy())
    // 默认菜单型：页面 7 字段在册（含组件路径与推导展示）
    expect(dialog.querySelector('[data-field="componentPathKey"]')).toBeTruthy()
    expect(dialog.querySelector('[data-field="derivedPath"]')).toBeTruthy()
    expect(dialog.querySelector('[data-field="icon"]')).toBeTruthy()
    // 切按钮型：字段集收缩为上级 + 权限标识 + 排序
    const radios = dialog.querySelectorAll('[data-field="type"] input[type="radio"]')
    await fireEvent.click(radios[2]!)
    await waitFor(() => {
      expect(dialog.querySelector('[data-field="permission"]')).toBeTruthy()
      expect(dialog.querySelector('[data-field="componentPathKey"]')).toBeFalsy()
      expect(dialog.querySelector('[data-field="icon"]')).toBeFalsy()
      expect(dialog.querySelector('[data-field="derivedPath"]')).toBeFalsy()
    })
  })

  it('编辑时 type 锁定，推导 path 与 name 只读展示', async () => {
    await renderPage()
    // 第二个编辑按钮 = 用户管理页节点（componentPathKey system/users）
    await fireEvent.click(byTest('menus-edit')[1]!)
    const dialog = document.querySelector('[data-test="menus-dialog"]') as HTMLElement
    await waitFor(() => {
      const radios = dialog.querySelectorAll('[data-field="type"] input[type="radio"]')
      expect(radios.length).toBe(3)
      expect(Array.from(radios).every((radio) => (radio as HTMLInputElement).disabled)).toBe(true)
      const pathInput = dialog.querySelector('[data-test="menus-derived-path"]') as HTMLInputElement
      expect(pathInput?.value).toBe('/system/users')
      expect(pathInput?.disabled).toBe(true)
    })
    // 路由 name 由视图模块异步解析回填，同样只读
    const nameInput = dialog.querySelector('[data-test="menus-derived-name"]') as HTMLInputElement
    await waitFor(() => expect(nameInput?.value).toBe('SystemUsers'))
    expect(nameInput.disabled).toBe(true)
  })

  it('图标选择器：名称过滤与选中回填 svg: 前缀值', async () => {
    await renderPage()
    await fireEvent.click(byTest('menus-create')[0]!)
    const dialog = document.querySelector('[data-test="menus-dialog"]') as HTMLElement
    await fireEvent.click(dialog.querySelector('[data-test="menus-icon-pick"]')!)
    const pickerDialog = document.querySelector('[data-test="menus-icon-dialog"]') as HTMLElement
    await waitFor(() => expect(pickerDialog).toBeTruthy())
    // 名称过滤：输入 dict 只剩 dictionary 一格
    const filterInput = pickerDialog.querySelector(
      '[data-test="menus-icon-filter"]',
    ) as HTMLInputElement
    await fireEvent.update(filterInput, 'dict')
    await waitFor(() => {
      const cells = pickerDialog.querySelectorAll('[data-test^="icon-option-"]')
      expect(cells.length).toBe(1)
    })
    // 选中回填 svg: 前缀值并关闭弹窗
    await fireEvent.click(pickerDialog.querySelector('[data-test="icon-option-dictionary"]')!)
    const iconInput = dialog.querySelector('[data-test="menus-icon-input"]') as HTMLInputElement
    expect(iconInput?.value).toBe('svg:dictionary')
  })

  it('删除确认：确认后调删除接口，错误消息直显', async () => {
    await renderPage()
    await fireEvent.click(byTest('menus-delete')[0]!)
    await waitFor(() => expect(removeMutateAsync).toHaveBeenCalledWith(1))
    expect(confirmMock).toHaveBeenCalledTimes(1)

    removeMutateAsync.mockRejectedValueOnce(new Error('有子级节点，禁止删除'))
    await fireEvent.click(byTest('menus-delete')[1]!)
    await waitFor(() => {
      const error = document.querySelector('.el-message--error')
      expect(error).toBeTruthy()
      expect(error?.textContent).toContain('有子级节点，禁止删除')
    })
    expect(confirmMock).toHaveBeenCalledTimes(2)
  })
})

describe('表单联动纯函数与双下拉数据源', () => {
  it('typeFields 三型字段集', () => {
    expect(typeFields(0)).toEqual(['parentId', 'i18nKey', 'icon', 'sort'])
    expect(typeFields(1)).toEqual([
      'parentId',
      'i18nKey',
      'componentPathKey',
      'icon',
      'hidden',
      'noCache',
      'sort',
    ])
    expect(typeFields(2)).toEqual(['parentId', 'permission', 'sort'])
    expect(typeFields(9)).toEqual([])
  })

  it('i18n key 下拉可选值在册且带当前语言值预览', () => {
    const options = i18nKeySelectOptions('zh-CN')
    const values = options.map((option) => option.value)
    // 现有词条在册：路由标题与按钮权限点文案 key
    expect(values).toContain('routes.systemUsers')
    expect(values).toContain('perms.systemRoleList')
    // 当前语言值预览
    const home = options.find((option) => option.value === 'routes.home')
    expect(home?.label).toContain('首页')
  })

  it('组件路径下拉含现有视图且排除登录页', () => {
    const values = componentPathSelectOptions().map((option) => option.value)
    expect(values).toContain('system/users')
    expect(values).toContain('system/menus')
    expect(values.some((value) => value.startsWith('login'))).toBe(false)
  })
})
