import { setDictSource } from '@eladmin-plus/admin-ui'
import { useMutation, useQuery, useQueryClient } from '@tanstack/vue-query'
import { computed } from 'vue'
import type { Ref } from 'vue'

import { request } from '@/api/request'
import type {
  Dict,
  DictDetail,
  DictDetailSaveRequest,
  DictItemView,
  DictSaveRequest,
} from '@/api/generated'
import { FALLBACK_LOCALE } from '@/i18n'
import { usePreferencesStore } from '@/stores/preferences'

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

/** 消费端点：按字典名取明细数组（label 多语言 JSON 原样下发，前端按 locale 解析） */
export async function fetchDictByName(name: string): Promise<DictItemView[]> {
  return request<DictItemView[]>({ url: `/dicts/${encodeURIComponent(name)}` })
}

/**
 * label 取值链：label[当前 locale] → label[默认语言 zh-CN] → 对象任一非空值。
 * 组装侧保证合法 JSON；极端非法原文原样透传（后端 422 权威）。
 */
function pickLabel(label: string | undefined): string {
  if (!label) {
    return ''
  }
  let parsed: unknown
  try {
    parsed = JSON.parse(label)
  } catch {
    return label
  }
  if (typeof parsed !== 'object' || parsed === null || Array.isArray(parsed)) {
    return ''
  }
  const rows = parsed as Record<string, string>
  const current = usePreferencesStore().resolvedLocale
  return rows[current] ?? rows[FALLBACK_LOCALE] ?? Object.values(rows).find(Boolean) ?? ''
}

/** 接线真实数据源（应用启动调用一次）：按名取端点适配为 DictOption；缓存与并发去重语义由 useDict 保持 */
export function setupDictSource(): void {
  setDictSource(async (type) => {
    const items = await fetchDictByName(type)
    return items.map((item) => ({ label: pickLabel(item.label), value: item.value ?? '' }))
  })
}
