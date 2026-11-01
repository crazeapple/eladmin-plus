import { createRouter, createWebHistory, type RouteRecordRaw } from 'vue-router'

import Layout from '@/layouts/Layout.vue'

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

export default router
