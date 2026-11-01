import { defineStore } from 'pinia'
import { ref } from 'vue'

export interface TagView {
  /** 路由 name，keep-alive include 依此匹配 */
  name: string
  path: string
  title: string
  noCache: boolean
}

/** addView 的最小路由形状（避免耦合 vue-router 实例类型） */
export interface TagRouteLike {
  name?: unknown
  path: string
  meta?: { title?: string; noCache?: boolean }
}

export const HOME_TAG: TagView = { name: 'Home', path: '/home', title: '首页', noCache: false }

export const useTagsViewStore = defineStore('tags-view', () => {
  // 首页固定 tag：store 建立即存在，不可关闭
  const visitedViews = ref<TagView[]>([{ ...HOME_TAG }])
  const cachedViews = ref<string[]>([HOME_TAG.name])

  function addView(route: TagRouteLike): void {
    const name = route.name
    if (typeof name !== 'string' || !name) return
    if (visitedViews.value.some((view) => view.path === route.path)) return
    const view: TagView = {
      name,
      path: route.path,
      title: route.meta?.title ?? route.path,
      noCache: Boolean(route.meta?.noCache),
    }
    visitedViews.value.push(view)
    if (!view.noCache && !cachedViews.value.includes(view.name)) {
      cachedViews.value.push(view.name)
    }
  }

  function closeCurrent(path: string): void {
    if (path === HOME_TAG.path) return
    const view = visitedViews.value.find((item) => item.path === path)
    if (!view) return
    visitedViews.value = visitedViews.value.filter((item) => item.path !== path)
    // 关闭即销毁：缓存集同步移除
    cachedViews.value = cachedViews.value.filter((name) => name !== view.name)
  }

  function closeOthers(path: string): void {
    visitedViews.value = visitedViews.value.filter(
      (view) => view.path === HOME_TAG.path || view.path === path,
    )
    const kept = new Set(
      visitedViews.value.filter((view) => !view.noCache).map((view) => view.name),
    )
    cachedViews.value = cachedViews.value.filter((name) => kept.has(name))
  }

  function closeAll(): void {
    visitedViews.value = visitedViews.value.filter((view) => view.path === HOME_TAG.path)
    cachedViews.value = [HOME_TAG.name]
  }

  return { visitedViews, cachedViews, addView, closeCurrent, closeOthers, closeAll }
})
