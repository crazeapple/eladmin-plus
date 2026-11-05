import { keepPreviousData, useMutation, useQuery, useQueryClient } from '@tanstack/vue-query'
import type { Ref } from 'vue'

import { downloadFile } from './download'
import type {
  BackupRecordView,
  DatabaseConfigSaveRequest,
  DatabaseConfigView,
  PageResultBackupRecordView,
} from './generated'
import { queryString } from './query-string'
import { request } from './request'

/** ops 数据库管理域契约薄封装：连接配置 CRUD / 手动备份 / 备份记录分页与行级删除 / 原名导出 */

const DATABASE_KEY = ['ops', 'database'] as const

/** 连接配置全量列表（无分页端点，密码不回显由后端保证） */
export function useDatabaseConfigs() {
  return useQuery({
    queryKey: [...DATABASE_KEY, 'configs'],
    queryFn: () => request<DatabaseConfigView[]>({ url: '/database/configs' }),
  })
}

/** 新增与编辑同一入口：带 id 走 PUT（密码留空即保持原值语义由后端承载） */
export function useDatabaseConfigSave() {
  const qc = useQueryClient()
  return useMutation({
    mutationFn: (data: DatabaseConfigSaveRequest & { id?: number | null }) =>
      data.id
        ? request({ url: `/database/configs/${data.id}`, method: 'PUT', body: data })
        : request({ url: '/database/configs', method: 'POST', body: data }),
    onSuccess: () => qc.invalidateQueries({ queryKey: [...DATABASE_KEY, 'configs'] }),
  })
}

export function useDatabaseConfigRemove() {
  const qc = useQueryClient()
  return useMutation({
    mutationFn: (id: number) => request({ url: `/database/configs/${id}`, method: 'DELETE' }),
    onSuccess: () => qc.invalidateQueries({ queryKey: [...DATABASE_KEY, 'configs'] }),
  })
}

/**
 * 手动备份：同步长请求（mysqldump 执行期挂起），返回本次备份记录视图；
 * 成功失效记录查询（记录区块随之出新行），失败无行由页面提示。
 */
export function useDatabaseBackup() {
  const qc = useQueryClient()
  return useMutation({
    mutationFn: (id: number) =>
      request<BackupRecordView>({ url: `/database/configs/${id}/backup`, method: 'POST' }),
    onSuccess: () => qc.invalidateQueries({ queryKey: [...DATABASE_KEY, 'records'] }),
  })
}

/** 备份记录筛选维度：文件名模糊（端点 filename 参数即文件名维度） */
export interface BackupRecordQuery {
  page: number
  size: number
  filename?: string
}

export function useBackupRecords(query: Ref<BackupRecordQuery>) {
  return useQuery({
    queryKey: [...DATABASE_KEY, 'records', query],
    placeholderData: keepPreviousData,
    queryFn: () =>
      request<PageResultBackupRecordView>({
        url: `/database/records?${queryString({ ...query.value })}`,
      }),
  })
}

/** 备份记录行级删除：删除代理删存储对象与记录行（后端语义），行随之消失 */
export function useBackupRecordRemove() {
  const qc = useQueryClient()
  return useMutation({
    mutationFn: (id: number) => request({ url: `/database/records/${id}`, method: 'DELETE' }),
    onSuccess: () => qc.invalidateQueries({ queryKey: [...DATABASE_KEY, 'records'] }),
  })
}

/** 备份记录行级导出：流式回原名 sql 文件（已删文件 404 由页面提示） */
export function exportBackupRecord(id: number): Promise<string> {
  return downloadFile({ url: `/database/records/${id}/export` })
}
