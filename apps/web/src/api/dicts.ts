import { useMutation, useQuery, useQueryClient } from '@tanstack/vue-query'
import { computed } from 'vue'
import type { Ref } from 'vue'

import { request } from '@/api/request'
import type { Dict, DictDetail, DictDetailSaveRequest, DictSaveRequest } from '@/api/generated'

const DICTS_KEY = ['system', 'dicts'] as const

export function useDictList() {
  return useQuery({
    queryKey: DICTS_KEY,
    queryFn: () => request<Dict[]>({ url: '/dicts' }),
  })
}

export function useDictSave() {
  const qc = useQueryClient()
  return useMutation({
    mutationFn: (data: DictSaveRequest) =>
      data.id
        ? request({ url: `/dicts/${data.id}`, method: 'PUT', body: data })
        : request({ url: '/dicts', method: 'POST', body: data }),
    onSuccess: () => qc.invalidateQueries({ queryKey: DICTS_KEY }),
  })
}

export function useDictRemove() {
  const dictsKey = DICTS_KEY
  const qc = useQueryClient()
  return useMutation({
    mutationFn: (id: number) => request({ url: `/dicts/${id}`, method: 'DELETE' }),
    onSuccess: () => qc.invalidateQueries({ queryKey: dictsKey }),
  })
}

/** 明细清单（明细管理弹窗打开时启用查询） */
export function useDictDetails(dictId: Ref<number | null>) {
  return useQuery({
    queryKey: [...DICTS_KEY, dictId, 'details'],
    enabled: computed(() => dictId.value !== null),
    queryFn: () => request<DictDetail[]>({ url: `/dicts/${dictId.value}/details` }),
  })
}

export function useDictDetailSave(dictId: Ref<number | null>) {
  const qc = useQueryClient()
  return useMutation({
    mutationFn: (data: DictDetailSaveRequest) => {
      if (data.id) {
        return request({
          url: `/dicts/${dictId.value}/details/${data.id}`,
          method: 'PUT',
          body: data,
        })
      }
      return request({ url: `/dicts/${dictId.value}/details`, method: 'POST', body: data })
    },
    onSuccess: () => {
      void qc.invalidateQueries({ queryKey: [...DICTS_KEY, dictId, 'details'] })
    },
  })
}

export function useDictDetailRemove(dictId: Ref<number | null>) {
  const qc = useQueryClient()
  return useMutation({
    mutationFn: (detailId: number) =>
      request({ url: `/dicts/${dictId.value}/details/${detailId}`, method: 'DELETE' }),
    onSuccess: () => {
      void qc.invalidateQueries({ queryKey: [...DICTS_KEY, dictId, 'details'] })
    },
  })
}
