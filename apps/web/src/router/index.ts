import { createRouter, createWebHistory, type Router, type RouteRecordRaw } from 'vue-router'

import Layout from '@/layouts/Layout.vue'
import { loadView } from './views'
import { useMenusStore } from '@/stores/menus'
import { useSessionStore } from '@/stores/session'

declare module 'vue-router' {
  interface RouteMeta {
    /** 标题存 i18n key（两语言包在册，测试断言防漏翻译） */
    title: string
    icon?: string
    noCache?: boolean
  }
}

export const routes: RouteRecordRaw[] = [
  {
    path: '/',
    component: Layout,
    redirect: '/home',
    children: [
      {
        path: 'home',
        name: 'Home',
        component: () => import('@/views/home/index.vue'),
        meta: { title: 'routes.home', icon: 'index' },
      },
      {
        path: 'demo/cached',
        name: 'DemoCached',
        component: () => import('@/views/demo/cached/index.vue'),
        meta: { title: 'routes.demoCached', icon: 'tab' },
      },
      {
        path: 'demo/no-cache',
        name: 'DemoNoCache',
        component: () => import('@/views/demo/no-cache/index.vue'),
        meta: { title: 'routes.demoNoCache', icon: 'edit', noCache: true },
      },
    ],
  },
  {
    // 登录页在布局壳之外：独立居中卡片，偏好主题与语言天然生效
    path: '/login',
    name: 'Login',
    component: () => import('@/views/login/index.vue'),
    meta: { title: 'login.title' },
  },
  {
    path: '/:pathMatch(.*)*',
    name: 'NotFound',
    component: () => import('@/views/error/404.vue'),
    meta: { title: 'routes.notFound' },
  },
]

const router = createRouter({
  history: createWebHistory(import.meta.env.BASE_URL),
  routes,
})

/** 布局壳内动态路由注册：后端菜单是唯一真相，组件 name 即路由 name（keep-alive 前提） */
async function registerDynamicRoutes(router: Router) {
  const menusStore = useMenusStore()
  const alreadyRegistered = new Set(router.getRoutes().map((route) => route.name))
  for (const node of menusStore.flatten()) {
    if (node.type !== 1 || !node.componentPathKey) {
      continue
    }
    const view = await loadView(node.componentPathKey)
    if (!view || alreadyRegistered.has(view.name)) {
      continue
    }
    router.addRoute('Layout', {
      path: `/${node.componentPathKey}`,
      name: view.name,
      component: view.component,
      meta: { title: node.i18nKey ?? view.name, noCache: node.noCache },
    })
  }
}

/**
 * 平台守卫：无会话回登录（带 redirect）；有会话先恢复（F5）再注册动态路由；未注册路径由 404 兜底。
 * store 在回调内取（延迟到 pinia 装配后的运行时）；抽成安装函数供测试对新实例复用。
 */
export function installAuthGuard(router: Router): void {
  router.beforeEach(async (to) => {
    if (to.name === 'Login') {
      return true
    }
    const sessionStore = useSessionStore()
    const restored = await sessionStore.ensureSession()
    if (!restored) {
      return { path: '/login', query: { redirect: to.fullPath }, replace: true }
    }
    const menusStore = useMenusStore()
    if (!menusStore.loaded) {
      const loaded = await menusStore.load()
      if (!loaded) {
        // 会话被后端拒绝：清态回登录
        sessionStore.reset()
        menusStore.reset()
        return { path: '/login', query: { redirect: to.fullPath }, replace: true }
      }
      await registerDynamicRoutes(router)
      // 新注册路由后的本次导航需重走匹配
      return { path: to.fullPath, replace: true }
    }
    return true
  })
}

installAuthGuard(router)

export default router
