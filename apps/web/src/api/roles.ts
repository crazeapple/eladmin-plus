import { keepPreviousData, useMutation, useQuery, useQueryClient } from '@tanstack/vue-query'
import type { Ref } from 'vue'

import { request } from '@/api/request'
import type { RoleSaveRequest, RoleView } from '@/api/generated'

export interface RolePageQuery {
  page: number
  size: number
  name?: string
  enabled?: number
}

const ROLES_KEY = ['system', 'roles'] as const

export function useRolePage(query: Ref<RolePageQuery>) {
  return useQuery({
    queryKey: [...ROLES_KEY, query],
    placeholderData: keepPreviousData,
    queryFn: () => {
      const qs = new URLSearchParams()
      qs.set('page', String(query.value.page))
      qs.set('size', String(query.value.size))
      if (query.value.name) {
        qs.set('name', query.value.name)
      }
      if (query.value.enabled !== undefined) {
        qs.set('enabled', String(query.value.enabled))
      }
      return request<{ content: RoleView[]; total: number; page: number; size: number }>({
        url: `/roles?${qs.toString()}`,
      })
    },
  })
}

/** 编辑回填用英文名：en 树的展示名即英文名（无覆盖时与默认名一致） */
export async function fetchRolePageEn(): Promise<RoleView[]> {
  const page = await request<{ content?: RoleView[] }>({
    url: '/roles?page=0&size=100',
    headers: { 'Accept-Language': 'en' },
  })
  return page.content ?? []
}

export function useRoleSave() {
  const qc = useQueryClient()
  return useMutation({
    mutationFn: (data: RoleSaveRequest) =>
      data.id
        ? request({ url: `/roles/${data.id}`, method: 'PUT', body: data })
        : request({ url: '/roles', method: 'POST', body: data }),
    onSuccess: () => qc.invalidateQueries({ queryKey: ROLES_KEY }),
  })
}

export function useRoleRemove() {
  const qc = useQueryClient()
  return useMutation({
    mutationFn: (id: number) => request({ url: `/roles/${id}`, method: 'DELETE' }),
    onSuccess: () => qc.invalidateQueries({ queryKey: ROLES_KEY }),
  })
}
