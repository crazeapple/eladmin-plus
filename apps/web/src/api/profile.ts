import { useMutation, useQuery, useQueryClient } from '@tanstack/vue-query'

import type { MeResponse, PasswordChangeRequest, ProfileUpdateRequest } from './generated'
import { request } from './request'

/** 个人中心契约薄封装：资料改、头像上传与清空、改密（登录即可，仅操作会话用户自己） */

const ME_KEY = ['auth', 'me'] as const

/** 用户区信息：资料表单与头像展示的数据源；资料 / 头像变更后失效重取 */
export function useMe() {
  return useQuery({
    queryKey: [...ME_KEY],
    queryFn: () => request<MeResponse>({ url: '/auth/me' }),
  })
}

/** 资料改：四字段全量替换（后端语义），成功后 me 失效重取 */
export function useUpdateProfile() {
  const qc = useQueryClient()
  return useMutation({
    mutationFn: (payload: ProfileUpdateRequest) =>
      request({ url: '/auth/me/profile', method: 'PUT', body: payload }),
    onSuccess: () => qc.invalidateQueries({ queryKey: [...ME_KEY] }),
  })
}

/** 头像上传：multipart 直传，文件落存储域且上传人为自己，avatar 写文件 id；成功后 me 失效重取 */
export function useUploadAvatar() {
  const qc = useQueryClient()
  return useMutation({
    mutationFn: (file: File) => {
      const form = new FormData()
      form.append('file', file)
      return request<number>({ url: '/auth/me/avatar', method: 'POST', body: form })
    },
    onSuccess: () => qc.invalidateQueries({ queryKey: [...ME_KEY] }),
  })
}

/** 头像清空：置 null 恢复无头像态（文件行不删，仅解除引用）；成功后 me 失效重取 */
export function useClearAvatar() {
  const qc = useQueryClient()
  return useMutation({
    mutationFn: () => request({ url: '/auth/me/avatar', method: 'DELETE' }),
    onSuccess: () => qc.invalidateQueries({ queryKey: [...ME_KEY] }),
  })
}

/**
 * 改密：成功即该用户全部会话失效（含当前，再访问 401）。前端按登出语义清态跳登录，
 * 故此处不失效 me 缓存（随后的重取必 401），清态由调用方收口。
 */
export function useChangePassword() {
  return useMutation({
    mutationFn: (payload: PasswordChangeRequest) =>
      request({ url: '/auth/me/password', method: 'PUT', body: payload }),
  })
}
