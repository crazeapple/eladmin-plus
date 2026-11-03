import { keepPreviousData, useMutation, useQuery, useQueryClient } from '@tanstack/vue-query'
import type { Ref } from 'vue'

import { request } from '@/api/request'
import { DEPT_TREE_KEY } from '@/api/depts'
import type { PageResultUserView, UserSaveRequest } from '@/api/generated'

export interface UserPageQuery {
  page: number
  size: number
  deptId?: number
  name?: string
  email?: string
  enabled?: number
  startTime?: string
  endTime?: string
}

const USERS_KEY = ['system', 'users'] as const

export function useUserPage(query: Ref<UserPageQuery>) {
  return useQuery({
    queryKey: [...USERS_KEY, query],
    placeholderData: keepPreviousData,
    queryFn: () => {
      const qs = new URLSearchParams()
      const q = query.value
      qs.set('page', String(q.page))
      qs.set('size', String(q.size))
      if (q.deptId !== undefined) {
        qs.set('deptId', String(q.deptId))
      }
      if (q.name) {
        qs.set('name', q.name)
      }
      if (q.email) {
        qs.set('email', q.email)
      }
      if (q.enabled !== undefined) {
        qs.set('enabled', String(q.enabled))
      }
      if (q.startTime) {
        qs.set('startTime', q.startTime)
      }
      if (q.endTime) {
        qs.set('endTime', q.endTime)
      }
      return request<PageResultUserView>({ url: `/users?${qs.toString()}` })
    },
  })
}

export function useUserEnabled() {
  const qc = useQueryClient()
  return useMutation({
    mutationFn: ({ id, value }: { id: number; value: 0 | 1 }) =>
      request({ url: `/users/${id}/enabled/${value}`, method: 'PUT' }),
    onSuccess: () => qc.invalidateQueries({ queryKey: USERS_KEY }),
  })
}

export function useUserSave() {
  const qc = useQueryClient()
  return useMutation({
    mutationFn: (data: UserSaveRequest) =>
      data.id
        ? request({ url: `/users/${data.id}`, method: 'PUT', body: data })
        : request({ url: '/users', method: 'POST', body: data }),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: USERS_KEY })
      qc.invalidateQueries({ queryKey: DEPT_TREE_KEY })
    },
  })
}

export function useUserRemove() {
  const qc = useQueryClient()
  return useMutation({
    mutationFn: (ids: number[]) => request({ url: '/users', method: 'DELETE', body: ids }),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: USERS_KEY })
      qc.invalidateQueries({ queryKey: DEPT_TREE_KEY })
    },
  })
}

/** 批量重置为默认密码（凭据变更即后端强退所选用户全部会话） */
export function useUserResetPassword() {
  const qc = useQueryClient()
  return useMutation({
    mutationFn: (ids: number[]) => request({ url: '/users/password', method: 'PUT', body: ids }),
    onSuccess: () => qc.invalidateQueries({ queryKey: USERS_KEY }),
  })
}
