import { useMutation, useQuery, useQueryClient } from '@tanstack/vue-query'

import { request } from '@/api/request'
import type { MenuNode, MenuSaveRequest } from '@/api/generated'

const MENUS_KEY = ['system', 'menus'] as const

/** 全量菜单树（目录 / 页面 / 按钮三类节点；管理页专用查询，角色授权页走既有 fetchMenuTree） */
export function useMenuTree() {
  return useQuery({
    queryKey: MENUS_KEY,
    queryFn: () => request<MenuNode[]>({ url: '/menus/tree' }),
  })
}

export function useMenuSave() {
  const qc = useQueryClient()
  return useMutation({
    mutationFn: (data: MenuSaveRequest) =>
      data.id
        ? request({ url: `/menus/${data.id}`, method: 'PUT', body: data })
        : request({ url: '/menus', method: 'POST', body: data }),
    onSuccess: () => qc.invalidateQueries({ queryKey: MENUS_KEY }),
  })
}

export function useMenuRemove() {
  const qc = useQueryClient()
  return useMutation({
    mutationFn: (id: number) => request({ url: `/menus/${id}`, method: 'DELETE' }),
    onSuccess: () => qc.invalidateQueries({ queryKey: MENUS_KEY }),
  })
}
