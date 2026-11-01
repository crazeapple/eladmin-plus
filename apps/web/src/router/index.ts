import { createRouter, createWebHistory, type RouteRecordRaw } from 'vue-router'

import Layout from '@/layouts/Layout.vue'

declare module 'vue-router' {
  interface RouteMeta {
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
        meta: { title: '首页', icon: 'index' },
      },
      {
        path: 'demo/cached',
        name: 'DemoCached',
        component: () => import('@/views/demo/cached/index.vue'),
        meta: { title: '演示·缓存', icon: 'tab' },
      },
      {
        path: 'demo/no-cache',
        name: 'DemoNoCache',
        component: () => import('@/views/demo/no-cache/index.vue'),
        meta: { title: '演示·不缓存', icon: 'edit', noCache: true },
      },
    ],
  },
  {
    path: '/:pathMatch(.*)*',
    name: 'NotFound',
    component: () => import('@/views/error/404.vue'),
    meta: { title: '404' },
  },
]

const router = createRouter({
  history: createWebHistory(import.meta.env.BASE_URL),
  routes,
})

export default router
