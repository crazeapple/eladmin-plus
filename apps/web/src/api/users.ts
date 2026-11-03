import { keepPreviousData, useMutation, useQuery, useQueryClient } from '@tanstack/vue-query'
import type { Ref } from 'vue'

import { request } from '@/api/request'
import type { PageResultUserView } from '@/api/generated'

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
