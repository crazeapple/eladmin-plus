import { VueQueryPlugin } from '@tanstack/vue-query'
import { cleanup, fireEvent, render, screen, waitFor, within } from '@testing-library/vue'
import { createPinia, setActivePinia } from 'pinia'
import { createMemoryHistory, createRouter } from 'vue-router'
import { defineComponent, h } from 'vue'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'

import App from '@/App.vue'
import { buildI18n } from '@/i18n'
import { installAuthGuard, routes } from '@/router'
import { useMenusStore, type MenuNode } from '@/stores/menus'

beforeEach(() => {
  // 钉住系统语言，偏好首访检测稳定取 zh-CN（文案断言走中文语言包）
  vi.spyOn(window.navigator, 'language', 'get').mockReturnValue('zh-CN')
})

afterEach(() => {
  cleanup()
  localStorage.clear()
  vi.restoreAllMocks()
})

/** 含「系统管理」目录的身份域菜单树（V4 种子形态） */
function menuTree(): MenuNode[] {
  const page = (id: number, i18nKey: string, componentPathKey: string, sort: number): MenuNode => ({
    id,
    parentId: 5,
    type: 1,
    permission: null,
    i18nKey,
    componentPathKey,
    noCache: false,
    sort,
    children: [],
  })
  return [
    {
      id: 1,
      parentId: null,
      type: 1,
      permission: null,
      i18nKey: 'routes.home',
      componentPathKey: 'home/index',
      noCache: false,
      sort: 1,
      children: [],
    },
    {
      id: 5,
      parentId: null,
      type: 0,
      permission: null,
      i18nKey: 'routes.system',
      componentPathKey: null,
      noCache: false,
      sort: 4,
      children: [
        page(6, 'routes.systemUsers', 'system/users', 1),
        page(7, 'routes.systemRoles', 'system/roles', 2),
        page(8, 'routes.systemDepts', 'system/depts', 3),
        page(9, 'routes.systemJobs', 'system/jobs', 4),
      ],
    },
  ]
}

function seedMenus(pinia: ReturnType<typeof createPinia>) {
  const store = useMenusStore(pinia)
  store.menus = menuTree()
  store.loaded = true
}

/** 渲染整个 App（顶层 router-view 语义与生产同构）；身份域视图用占位组件承载路由 */
async function renderApp(path = '/home') {
  document.body.innerHTML = ''
  const pinia = createPinia()
  setActivePinia(pinia)
  seedMenus(pinia)
  const router = createRouter({ history: createMemoryHistory(), routes })
  installAuthGuard(router)
  router.addRoute('Layout', {
    path: '/system/users',
    name: 'SystemUsers',
    component: defineComponent({
      name: 'SystemUsers',
      render: () => h('div', { 'data-test': 'probe-page' }, 'users-page'),
    }),
    meta: { title: 'routes.systemUsers' },
  })
  await router.push(path)
  await router.isReady()
  render(App, { global: { plugins: [pinia, router, buildI18n('zh-CN'), VueQueryPlugin] } })
  return router
}

describe('侧栏目录渲染与面包屑', () => {
  it('目录以子菜单形态渲染，展开后四个子项可见', async () => {
    await renderApp()
    // 目录渲染为 el-sub-menu，四个子项在册；点击展开后全部可见
    const submenuTitle = screen.getByText('系统管理')
    expect(submenuTitle).toBeTruthy()
    for (const title of ['用户管理', '角色管理', '部门管理', '岗位管理']) {
      expect(screen.getByText(title)).toBeTruthy()
    }
    await fireEvent.click(submenuTitle)
    await waitFor(() => {
      const item = screen.getByText('用户管理').closest('li') as HTMLElement
      expect(item.style.display === 'none' || item.offsetParent === null).toBe(false)
    })
  })

  it('面包屑两级：目录纯文本加页面链接', async () => {
    await renderApp('/system/users')
    await waitFor(() => expect(document.querySelector('[data-test="probe-page"]')).toBeTruthy())
    const breadcrumb = document.querySelector('.layout__breadcrumb') as HTMLElement
    // EP 面包屑不渲染真 <a>：可点级带 is-link 类，目录级纯文本无
    const directory = within(breadcrumb).getByText('系统管理')
    expect(directory.classList.contains('is-link')).toBe(false)
    const pageItem = within(breadcrumb).getByText('用户管理')
    expect(pageItem.classList.contains('is-link')).toBe(true)
  })
})
