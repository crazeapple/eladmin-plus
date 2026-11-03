import { keepPreviousData, useMutation, useQuery, useQueryClient } from '@tanstack/vue-query'
import type { Ref } from 'vue'

import { request } from '@/api/request'
import type { JobSaveRequest, PageResultJobView } from '@/api/generated'

export interface JobPageQuery {
  page: number
  size: number
  name?: string
  enabled?: number
}

const JOBS_KEY = ['system', 'jobs'] as const

export function useJobPage(query: Ref<JobPageQuery>) {
  return useQuery({
    queryKey: [...JOBS_KEY, query],
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
      return request<PageResultJobView>({ url: `/jobs?${qs.toString()}` })
    },
  })
}

export function useJobSave() {
  const qc = useQueryClient()
  return useMutation({
    mutationFn: (data: JobSaveRequest) =>
      data.id
        ? request({ url: `/jobs/${data.id}`, method: 'PUT', body: data })
        : request({ url: '/jobs', method: 'POST', body: data }),
    onSuccess: () => qc.invalidateQueries({ queryKey: JOBS_KEY }),
  })
}

export function useJobRemove() {
  const qc = useQueryClient()
  return useMutation({
    mutationFn: (id: number) => request({ url: `/jobs/${id}`, method: 'DELETE' }),
    onSuccess: () => qc.invalidateQueries({ queryKey: JOBS_KEY }),
  })
}
