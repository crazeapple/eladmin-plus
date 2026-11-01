import { createPinia, setActivePinia } from 'pinia'
import { beforeEach, describe, expect, it } from 'vitest'

import { useTagsViewStore, type TagRouteLike } from '@/stores/tags-view'

const homeRoute: TagRouteLike = { name: 'Home', path: '/home', meta: { title: '首页' } }
const cachedRoute: TagRouteLike = {
  name: 'DemoCached',
  path: '/demo/cached',
  meta: { title: '演示·缓存' },
}
const noCacheRoute: TagRouteLike = {
  name: 'DemoNoCache',
  path: '/demo/no-cache',
  meta: { title: '演示·不缓存', noCache: true },
}

describe('tagsView store', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
  })

  it('固定首页 tag：store 建立即存在，重复添加去重', () => {
    const store = useTagsViewStore()
    expect(store.visitedViews).toHaveLength(1)
    expect(store.visitedViews[0].path).toBe('/home')

    store.addView(cachedRoute)
    store.addView({ ...cachedRoute })
    store.addView(homeRoute)
    expect(store.visitedViews).toHaveLength(2)
  })

  it('noCache 路由不入缓存集，缓存路由入缓存集', () => {
    const store = useTagsViewStore()
    store.addView(cachedRoute)
    store.addView(noCacheRoute)
    expect(store.cachedViews).toContain('DemoCached')
    expect(store.cachedViews).not.toContain('DemoNoCache')
  })

  it('关闭当前：非首页可关且缓存同步移除，首页不可关', () => {
    const store = useTagsViewStore()
    store.addView(cachedRoute)
    store.closeCurrent('/demo/cached')
    expect(store.visitedViews.map((v) => v.path)).toEqual(['/home'])
    expect(store.cachedViews).not.toContain('DemoCached')

    store.addView(cachedRoute)
    store.closeCurrent('/home')
    expect(store.visitedViews).toHaveLength(2)
  })

  it('关闭其他：保留首页与目标，缓存集随之收缩', () => {
    const store = useTagsViewStore()
    store.addView(cachedRoute)
    store.addView(noCacheRoute)
    store.closeOthers('/demo/no-cache')
    expect(store.visitedViews.map((v) => v.path)).toEqual(['/home', '/demo/no-cache'])
    expect(store.cachedViews).toEqual(['Home'])
  })

  it('关闭全部：仅剩首页', () => {
    const store = useTagsViewStore()
    store.addView(cachedRoute)
    store.addView(noCacheRoute)
    store.closeAll()
    expect(store.visitedViews.map((v) => v.path)).toEqual(['/home'])
    expect(store.cachedViews).toEqual(['Home'])
  })

  it('关闭后重开：页签重新登记', () => {
    const store = useTagsViewStore()
    store.addView(cachedRoute)
    store.closeCurrent('/demo/cached')
    store.addView(cachedRoute)
    expect(store.visitedViews.map((v) => v.path)).toEqual(['/home', '/demo/cached'])
    expect(store.cachedViews).toContain('DemoCached')
  })
})
