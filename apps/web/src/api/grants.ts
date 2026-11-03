import { useMutation, useQueryClient } from '@tanstack/vue-query'

import { request } from '@/api/request'
import type { MenuNode } from '@/api/generated'

const ROLES_KEY = ['system', 'roles'] as const

/** 全量菜单树（含目录 / 页面 / 按钮权限点三类节点，角色授权树消费） */
export async function fetchMenuTree(): Promise<MenuNode[]> {
  return request<MenuNode[]>({ url: '/menus/tree' })
}

export async function fetchRoleMenuIds(roleId: number): Promise<number[]> {
  return request<number[]>({ url: `/roles/${roleId}/menus` })
}

export function useSaveRoleMenus() {
  const qc = useQueryClient()
  return useMutation({
    mutationFn: ({ roleId, menuIds }: { roleId: number; menuIds: number[] }) =>
      request({ url: `/roles/${roleId}/menus`, method: 'PUT', body: menuIds }),
    // 授权变更影响 /auth/me 下发（菜单与权限点），invalidate 角色（授权时间列等若有）
    onSuccess: () => qc.invalidateQueries({ queryKey: ROLES_KEY }),
  })
}
