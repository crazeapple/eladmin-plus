import { useMutation, useQuery, useQueryClient } from '@tanstack/vue-query'
import { computed } from 'vue'
import type { Ref } from 'vue'

import { request } from '@/api/request'
import type {
  PageResultTaskLog,
  TaskLog,
  TaskSaveRequest,
  TaskTargetView,
  TaskView,
} from '@/api/generated'

const TASKS_KEY = ['system', 'tasks'] as const

/** 任务清单（整表返回，页面做客户端分页） */
export function useTaskList() {
  return useQuery({
    queryKey: TASKS_KEY,
    queryFn: () => request<TaskView[]>({ url: '/tasks' }),
  })
}

/** 目标白名单清单（任务表单下拉数据源） */
export function useTaskTargets() {
  return useQuery({
    queryKey: [...TASKS_KEY, 'targets'],
    queryFn: () => request<TaskTargetView[]>({ url: '/tasks/targets' }),
  })
}

export function useTaskSave() {
  const qc = useQueryClient()
  return useMutation({
    mutationFn: (data: TaskSaveRequest) =>
      data.id
        ? request({ url: `/tasks/${data.id}`, method: 'PUT', body: data })
        : request({ url: '/tasks', method: 'POST', body: data }),
    onSuccess: () => qc.invalidateQueries({ queryKey: TASKS_KEY }),
  })
}

export function useTaskRemove() {
  const qc = useQueryClient()
  return useMutation({
    mutationFn: (id: number) => request({ url: `/tasks/${id}`, method: 'DELETE' }),
    onSuccess: () => qc.invalidateQueries({ queryKey: TASKS_KEY }),
  })
}

export function useTaskPause() {
  const qc = useQueryClient()
  return useMutation({
    mutationFn: (id: number) => request({ url: `/tasks/${id}/pause`, method: 'POST' }),
    onSuccess: () => qc.invalidateQueries({ queryKey: TASKS_KEY }),
  })
}

export function useTaskResume() {
  const qc = useQueryClient()
  return useMutation({
    mutationFn: (id: number) => request({ url: `/tasks/${id}/resume`, method: 'POST' }),
    onSuccess: () => qc.invalidateQueries({ queryKey: TASKS_KEY }),
  })
}

export function useTaskExec() {
  const qc = useQueryClient()
  return useMutation({
    mutationFn: (id: number) => request({ url: `/tasks/${id}/exec`, method: 'POST' }),
    // 执行异步落表：清单的调度态随查询失效刷新
    onSuccess: () => qc.invalidateQueries({ queryKey: TASKS_KEY }),
  })
}

/** 执行日志分页（日志弹窗打开时启用查询） */
export function useTaskLogs(taskId: Ref<number | null>, page: Ref<number>, size: Ref<number>) {
  return useQuery({
    queryKey: [...TASKS_KEY, taskId, 'logs', page, size],
    enabled: computed(() => taskId.value !== null),
    queryFn: () =>
      request<PageResultTaskLog>({
        url: `/tasks/${taskId.value}/logs?page=${page.value}&size=${size.value}`,
      }),
  })
}

export type { TaskLog }
