import { keepPreviousData, useMutation, useQuery, useQueryClient } from '@tanstack/vue-query'
import type { Ref } from 'vue'

import { downloadFile } from './download'
import type {
  OnlineUserView,
  PageResultExceptionLog,
  PageResultOperationLog,
  ServerMonitorView,
} from './generated'
import { request } from './request'

/** monitor 监控域四组契约薄封装：操作日志 / 异常日志 / 在线用户 / 服务监控 */

const MONITOR_KEY = ['monitor'] as const

/** 日志页共用筛选维度：操作人 / IP 精确、描述（或异常消息）模糊、创建时间闭区间 */
export interface LogPageQuery {
  page: number
  size: number
  username?: string
  ip?: string
  description?: string
  startTime?: string
  endTime?: string
}

export interface ExceptionLogQuery extends Omit<LogPageQuery, 'description'> {
  message?: string
}

/** 查询串构造：空值字段不入串（分页字段恒在） */
function queryString(params: Record<string, string | number | undefined>): string {
  const qs = new URLSearchParams()
  for (const [key, value] of Object.entries(params)) {
    if (value !== undefined && value !== '') {
      qs.set(key, String(value))
    }
  }
  return qs.toString()
}

/** —— 操作日志（/logs/operations） —— */

export function useOperationLogPage(query: Ref<LogPageQuery>) {
  return useQuery({
    queryKey: [...MONITOR_KEY, 'operationLogs', query],
    placeholderData: keepPreviousData,
    queryFn: () =>
      request<PageResultOperationLog>({
        url: `/logs/operations?${queryString({ ...query.value })}`,
      }),
  })
}

export function useOperationLogRemove() {
  const qc = useQueryClient()
  return useMutation({
    mutationFn: (id: number) => request({ url: `/logs/operations/${id}`, method: 'DELETE' }),
    onSuccess: () => qc.invalidateQueries({ queryKey: [...MONITOR_KEY, 'operationLogs'] }),
  })
}

export function useOperationLogRemoveBatch() {
  const qc = useQueryClient()
  return useMutation({
    mutationFn: (ids: number[]) =>
      request({ url: '/logs/operations', method: 'DELETE', body: ids }),
    onSuccess: () => qc.invalidateQueries({ queryKey: [...MONITOR_KEY, 'operationLogs'] }),
  })
}

export function useOperationLogClear() {
  const qc = useQueryClient()
  return useMutation({
    mutationFn: () => request({ url: '/logs/operations/all', method: 'DELETE' }),
    onSuccess: () => qc.invalidateQueries({ queryKey: [...MONITOR_KEY, 'operationLogs'] }),
  })
}

/** 导出当前筛选全集（复用筛选条件不分页），浏览器保存触发即落文件名 */
export function exportOperationLogs(query: LogPageQuery): Promise<string> {
  return downloadFile({
    url: `/logs/operations/export?${queryString({
      username: query.username,
      ip: query.ip,
      description: query.description,
      startTime: query.startTime,
      endTime: query.endTime,
    })}`,
  })
}

/** —— 异常日志（/logs/exceptions） —— */

export function useExceptionLogPage(query: Ref<ExceptionLogQuery>) {
  return useQuery({
    queryKey: [...MONITOR_KEY, 'exceptionLogs', query],
    placeholderData: keepPreviousData,
    queryFn: () =>
      request<PageResultExceptionLog>({
        url: `/logs/exceptions?${queryString({ ...query.value })}`,
      }),
  })
}

export function useExceptionLogRemove() {
  const qc = useQueryClient()
  return useMutation({
    mutationFn: (id: number) => request({ url: `/logs/exceptions/${id}`, method: 'DELETE' }),
    onSuccess: () => qc.invalidateQueries({ queryKey: [...MONITOR_KEY, 'exceptionLogs'] }),
  })
}

export function useExceptionLogRemoveBatch() {
  const qc = useQueryClient()
  return useMutation({
    mutationFn: (ids: number[]) =>
      request({ url: '/logs/exceptions', method: 'DELETE', body: ids }),
    onSuccess: () => qc.invalidateQueries({ queryKey: [...MONITOR_KEY, 'exceptionLogs'] }),
  })
}

export function useExceptionLogClear() {
  const qc = useQueryClient()
  return useMutation({
    mutationFn: () => request({ url: '/logs/exceptions/all', method: 'DELETE' }),
    onSuccess: () => qc.invalidateQueries({ queryKey: [...MONITOR_KEY, 'exceptionLogs'] }),
  })
}

/** 导出当前筛选全集（含堆栈列） */
export function exportExceptionLogs(query: ExceptionLogQuery): Promise<string> {
  return downloadFile({
    url: `/logs/exceptions/export?${queryString({
      username: query.username,
      ip: query.ip,
      message: query.message,
      startTime: query.startTime,
      endTime: query.endTime,
    })}`,
  })
}

/** —— 在线用户（/online） —— */

export function useOnlineUsers() {
  return useQuery({
    queryKey: [...MONITOR_KEY, 'online'],
    queryFn: () => request<OnlineUserView[]>({ url: '/online' }),
  })
}

/** 强退目标用户全部会话（含自己在内无豁免，页面确认文案兜底） */
export function useOnlineKick() {
  const qc = useQueryClient()
  return useMutation({
    mutationFn: (userId: number) => request({ url: `/online/${userId}`, method: 'DELETE' }),
    onSuccess: () => qc.invalidateQueries({ queryKey: [...MONITOR_KEY, 'online'] }),
  })
}

/** —— 服务监控（/monitor/server） —— */

/**
 * 服务指标查询：轮询由页面定时器驱动 refetch；单次失败静默——不重试堆叠，
 * 错误态由页面吞下（保旧数据渲染），下一轮继续。
 */
export function useServerMonitor() {
  return useQuery({
    queryKey: [...MONITOR_KEY, 'server'],
    queryFn: () => request<ServerMonitorView>({ url: '/monitor/server' }),
    retry: false,
  })
}
