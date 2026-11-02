import { beforeEach, describe, expect, it, vi } from 'vitest'
import { createPinia, setActivePinia } from 'pinia'
import { useAuth, ROOT_ROLE } from '../use-auth'
import { useMenusStore } from '@/stores/menus'

beforeEach(() => {
  setActivePinia(createPinia())
})

describe('hasAuth 权限点查询', () => {
  it('ROLE_ROOT 直通：任意权限点恒真', () => {
    const store = useMenusStore()
    store.$patch({ roles: [ROOT_ROLE], permissions: [] })
    const { hasAuth } = useAuth()

    expect(hasAuth('system:user:add')).toBe(true)
    expect(hasAuth('anything:at:all')).toBe(true)
  })

  it('普通角色按权限点集合判定', () => {
    const store = useMenusStore()
    store.$patch({ roles: ['ROLE_COMMON'], permissions: ['system:user:list', 'system:user:add'] })
    const { hasAuth } = useAuth()

    expect(hasAuth('system:user:add')).toBe(true)
    expect(hasAuth('system:user:delete')).toBe(false)
  })

  it('无角色时一律拒绝', () => {
    const store = useMenusStore()
    store.$patch({ roles: [], permissions: [] })
    const { hasAuth } = useAuth()

    expect(hasAuth('system:user:list')).toBe(false)
  })

  it('菜单加载后权限点与角色码进入 store（登录下发语义）', async () => {
    localStorage.setItem('auth:logged-in', '1')
    vi.stubGlobal(
      'fetch',
      vi.fn(
        async () =>
          new Response(
            JSON.stringify({
              code: 200,
              message: '',
              data: {
                user: { id: 1, username: 'op', nickName: '操作员' },
                roles: ['ROLE_COMMON'],
                permissions: ['monitor:online:force-logout'],
                menus: [],
              },
            }),
            { status: 200, headers: { 'Content-Type': 'application/json' } },
          ),
      ),
    )
    const store = useMenusStore()
    await store.load()
    const { hasAuth } = useAuth()

    expect(store.roles).toEqual(['ROLE_COMMON'])
    expect(hasAuth('monitor:online:force-logout')).toBe(true)
    expect(hasAuth('system:user:add')).toBe(false)
  })
})
