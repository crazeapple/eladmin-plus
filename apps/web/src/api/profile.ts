import { useMutation, useQuery, useQueryClient } from '@tanstack/vue-query'

import type { MeResponse, ProfileUpdateRequest } from './generated'
import { request } from './request'

/** 个人中心契约薄封装：资料改、头像上传与清空（登录即可，仅操作会话用户自己） */

const ME_KEY = ['auth', 'me'] as const

/**
 * 用户区 me 的用户视图：生成物把 me 的用户字段并入 system 域 UserView（两域同名 schema 冲突），
 * 运行时 me 面为 id / username / nickName / avatarId；email / phone / gender 为资料表单预填
 * 预留位，后端契约扩字段后在册即生效。
 */
export interface MeUser {
  id: number
  username: string
  nickName: string | null
  avatarId?: number | null
  email?: string | null
  phone?: string | null
  gender?: number | null
}

export interface MePayload extends Omit<MeResponse, 'user'> {
  user: MeUser
}

/** 用户区信息：资料表单与头像展示的数据源；资料 / 头像变更后失效重取 */
export function useMe() {
  return useQuery({
    queryKey: [...ME_KEY],
    queryFn: () => request<MePayload>({ url: '/auth/me' }),
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
