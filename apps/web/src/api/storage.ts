import { keepPreviousData, useMutation, useQuery, useQueryClient } from '@tanstack/vue-query'
import type { Ref } from 'vue'

import { downloadFile } from './download'
import type { PageResultStorageFileView, StorageFileView } from './generated'
import { queryString } from './query-string'
import { request, send } from './request'

/** tools 存储域契约薄封装：分页列表 / 直传 / 原名下载 / 删除 */

const STORAGE_KEY = ['tools', 'storage'] as const

/** 列表筛选维度：原名模糊、上传人精确、创建时间闭区间 */
export interface StoragePageQuery {
  page: number
  size: number
  name?: string
  uploader?: string
  startTime?: string
  endTime?: string
}

export function useStoragePage(query: Ref<StoragePageQuery>) {
  return useQuery({
    queryKey: [...STORAGE_KEY, 'files', query],
    placeholderData: keepPreviousData,
    queryFn: () =>
      request<PageResultStorageFileView>({
        url: `/storage/files?${queryString({ ...query.value })}`,
      }),
  })
}

/** 直传单文件：multipart 交浏览器自动定界（FormData 直通，不手动设 Content-Type） */
export function useStorageUpload() {
  const qc = useQueryClient()
  return useMutation({
    mutationFn: (file: File) => {
      const form = new FormData()
      form.append('file', file)
      return request<StorageFileView>({ url: '/storage/files', method: 'POST', body: form })
    },
    onSuccess: () => qc.invalidateQueries({ queryKey: [...STORAGE_KEY, 'files'] }),
  })
}

/** 原名下载：文件名以后端 Content-Disposition 为准，响应到达即触发浏览器保存 */
export function downloadStorageFile(id: number): Promise<string> {
  return downloadFile({ url: `/storage/files/${id}/download` })
}

/**
 * 取文件 blob 对象 URL：下载端点须带鉴权头，img 直挂 src 拿不到 ——
 * 先 blob 后 objectURL，供图片缩略 / 大图预览与头像展示消费，用毕由调用方 revoke。
 */
export async function storageFileBlobUrl(id: number): Promise<string> {
  const { data } = await send({ url: `/storage/files/${id}/download`, parseAs: 'blob' })
  return URL.createObjectURL(data as Blob)
}

export function useStorageRemove() {
  const qc = useQueryClient()
  return useMutation({
    mutationFn: (id: number) => request({ url: `/storage/files/${id}`, method: 'DELETE' }),
    onSuccess: () => qc.invalidateQueries({ queryKey: [...STORAGE_KEY, 'files'] }),
  })
}
