import { defineStore } from 'pinia'
import { ref } from 'vue'
import { request } from '@/api/request'
import { usePreferencesStore } from '@/stores/preferences'

/** 后端 /auth/me 下发的菜单树节点（与后端 MeController 形态对齐） */
export interface MenuNode {
  id: number
  parentId: number | null
  type: number
  permission: string | null
  i18nKey: string | null
  componentPathKey: string | null
  icon?: string | null
  noCache: boolean
  sort: number
  children: MenuNode[]
}

/**
 * 菜单 store：后端菜单是唯一真相。load 拉取一次，reset 随登出清空；
 * 动态注册（addRoute）由路由守卫执行，store 只管数据。
 */
export interface SessionUser {
  id: number
  username: string
  nickName: string | null
}

export const useMenusStore = defineStore('menus', () => {
  const menus = ref<MenuNode[]>([])
  const loaded = ref(false)
  const user = ref<SessionUser | null>(null)
  const roles = ref<string[]>([])
  const permissions = ref<string[]>([])

  /** 拉取菜单树与用户视图（随带账户偏好，到达即覆盖本地缓存）；失败（含 401）返回 false 交守卫跳登录 */
  async function load(): Promise<boolean> {
    try {
      const payload = await request<{
        user?: SessionUser
        roles?: string[]
        permissions?: string[]
        menus?: MenuNode[]
        preferences?: unknown
      }>({ url: '/auth/me' })
      user.value = payload.user ?? null
      roles.value = payload.roles ?? []
      permissions.value = payload.permissions ?? []
      menus.value = payload.menus ?? []
      usePreferencesStore().applyAccountPreferences(payload.preferences)
      loaded.value = true
      return true
    } catch {
      return false
    }
  }

  function reset() {
    menus.value = []
    loaded.value = false
    user.value = null
    roles.value = []
    permissions.value = []
  }

  /** 后端菜单树展平（含各级） */
  function flatten(): MenuNode[] {
    const result: MenuNode[] = []
    const walk = (nodes: MenuNode[]) => {
      for (const node of nodes) {
        result.push(node)
        if (node.children?.length) {
          walk(node.children)
        }
      }
    }
    walk(menus.value)
    return result
  }

  return { menus, loaded, user, roles, permissions, load, reset, flatten }
})
