import { useMutation, useQuery, useQueryClient } from '@tanstack/vue-query'

import { request } from '@/api/request'
import type { DeptSaveRequest, DeptTreeNode } from '@/api/generated'

/**
 * 部门域查询与变更封装。
 * 树查询单例 key：部门管理页与用户管理页共享同一缓存，变更后一处失效两处同步。
 */
export const DEPT_TREE_KEY = ['system', 'depts', 'tree'] as const

export function useDeptTree() {
  return useQuery({
    queryKey: DEPT_TREE_KEY,
    queryFn: () => request<DeptTreeNode[]>({ url: '/depts/tree' }),
  })
}

/** 编辑回填用英文名：en 树的展示名即英文名（无覆盖时与默认名一致） */
export async function fetchDeptTreeEn(): Promise<DeptTreeNode[]> {
  return request<DeptTreeNode[]>({
    url: '/depts/tree',
    headers: { 'Accept-Language': 'en' },
  })
}

export function useDeptSave() {
  const qc = useQueryClient()
  return useMutation({
    mutationFn: (data: DeptSaveRequest) =>
      data.id
        ? request({ url: `/depts/${data.id}`, method: 'PUT', body: data })
        : request({ url: '/depts', method: 'POST', body: data }),
    onSuccess: () => qc.invalidateQueries({ queryKey: DEPT_TREE_KEY }),
  })
}

export function useDeptRemove() {
  const qc = useQueryClient()
  return useMutation({
    mutationFn: (id: number) => request({ url: `/depts/${id}`, method: 'DELETE' }),
    onSuccess: () => qc.invalidateQueries({ queryKey: DEPT_TREE_KEY }),
  })
}

/** 按 id 在树中找节点 */
export function findDeptNode(tree: DeptTreeNode[], id: number): DeptTreeNode | null {
  for (const node of tree) {
    if (node.id === id) {
      return node
    }
    const found = node.children?.length ? findDeptNode(node.children, id) : null
    if (found) {
      return found
    }
  }
  return null
}
