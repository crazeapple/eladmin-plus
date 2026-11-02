import { useMenusStore } from '@/stores/menus'

/** 内置超管角色位：判定不依赖用户名 */
export const ROOT_ROLE = 'ROLE_ROOT'

/**
 * 权限点查询：集合查询 + ROLE_ROOT 直通（可见性藏显只是 UX，能力点清单登录下发）。
 * 模板用法 v-if="hasAuth('system:user:add')"，不做 DOM 移除式指令（定案 4.2.8）。
 */
export function useAuth() {
  const menusStore = useMenusStore()

  function hasAuth(permission: string): boolean {
    if (menusStore.roles.includes(ROOT_ROLE)) {
      return true
    }
    return menusStore.permissions.includes(permission)
  }

  return { hasAuth }
}
