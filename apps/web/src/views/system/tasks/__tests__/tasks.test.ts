import { VueQueryPlugin } from '@tanstack/vue-query'
import { cleanup, fireEvent, render, waitFor } from '@testing-library/vue'
import { createPinia, setActivePinia } from 'pinia'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'

import { buildI18n } from '@/i18n'
import { useMenusStore } from '@/stores/menus'
import Tasks from '../index.vue'
import { cronHint, CRON_PRESETS } from '../cron'
import type { TaskTargetView, TaskView } from '@/api/generated'

const taskData: TaskView[] = [
  {
    id: 1,
    name: '样例任务',
    beanName: 'sampleJob',
    methodName: 'run',
    param: 'demo',
    cron: '0 * * * * ?',
    status: 1,
    scheduleState: 'running',
  },
  {
    id: 2,
    name: '夜间清理',
    beanName: 'sampleJob',
    methodName: 'run',
    cron: '0 0 0 * * ?',
    status: 0,
    scheduleState: 'paused',
  },
]

const targetData: TaskTargetView[] = [
  { beanName: 'sampleJob', methodName: 'run', param: '演示参数，原样打印到日志' },
]

const {
  saveAsync,
  removeAsync,
  pauseAsync,
  resumeAsync,
  execAsync,
  refetchLogsMock,
  confirmMock,
  targetsBox,
  logsBox,
} = vi.hoisted(() => ({
  saveAsync: vi.fn().mockResolvedValue(undefined),
  removeAsync: vi.fn().mockResolvedValue(undefined),
  pauseAsync: vi.fn().mockResolvedValue(undefined),
  resumeAsync: vi.fn().mockResolvedValue(undefined),
  execAsync: vi.fn().mockResolvedValue(undefined),
  refetchLogsMock: vi.fn().mockResolvedValue(undefined),
  confirmMock: vi.fn().mockResolvedValue(true),
  targetsBox: { value: [] as Array<Record<string, unknown>> },
  logsBox: {
    value: {
      content: [] as Array<Record<string, unknown>>,
      total: 0,
      page: 0,
      size: 10,
    },
  },
}))

vi.mock('@/api/tasks', async (importOriginal) => {
  const actual = await importOriginal<typeof import('@/api/tasks')>()
  const { ref } = await import('vue')
  return {
    ...actual,
    useTaskList: () => ({ data: ref(taskData), isLoading: ref(false) }),
    useTaskTargets: () => ({ data: ref(targetsBox.value) }),
    useTaskSave: () => ({ mutateAsync: saveAsync }),
    useTaskRemove: () => ({ mutateAsync: removeAsync }),
    useTaskPause: () => ({ mutateAsync: pauseAsync }),
    useTaskResume: () => ({ mutateAsync: resumeAsync }),
    useTaskExec: () => ({ mutateAsync: execAsync }),
    useTaskLogs: () => ({
      data: ref(logsBox.value),
      isLoading: ref(false),
      refetch: refetchLogsMock,
    }),
  }
})

vi.mock('element-plus', async (importOriginal) => {
  const actual = await importOriginal<typeof import('element-plus')>()
  return { ...actual, ElMessageBox: { ...actual.ElMessageBox, confirm: confirmMock } }
})

function byTest(test: string): HTMLElement[] {
  return Array.from(document.querySelectorAll(`[data-test="${test}"]`)) as HTMLElement[]
}

function inputByTest(test: string): HTMLInputElement {
  return document.querySelector(`[data-test="${test}"]`) as HTMLInputElement
}

/** teleported=false 的行内下拉：点开触发器后点选目标项 */
async function pickSelectOption(selectTest: string, optionText: string) {
  const select = document.querySelector(`[data-test="${selectTest}"]`) as HTMLElement
  const trigger = select.querySelector('.el-select__wrapper') ?? select
  await fireEvent.click(trigger)
  await waitFor(() => {
    const items = Array.from(document.querySelectorAll('.el-select-dropdown__item'))
    expect(items.length).toBeGreaterThan(0)
  })
  const item = Array.from(document.querySelectorAll('.el-select-dropdown__item')).find((node) =>
    node.textContent?.includes(optionText),
  )
  expect(item).toBeTruthy()
  await fireEvent.click(item as HTMLElement)
}

beforeEach(() => {
  vi.spyOn(window.navigator, 'language', 'get').mockReturnValue('zh-CN')
  targetsBox.value = targetData as unknown as Array<Record<string, unknown>>
  logsBox.value = {
    content: [
      {
        id: 21,
        taskId: 1,
        executeTime: '2026-11-04 12:00:00',
        success: 1,
        durationMs: 12,
        errorMsg: null,
      },
      {
        id: 22,
        taskId: 1,
        executeTime: '2026-11-04 12:05:00',
        success: 0,
        durationMs: 3400,
        errorMsg: 'RuntimeException: 目标方法抛出异常（摘要完整不截断）',
      },
    ],
    total: 2,
    page: 0,
    size: 10,
  } as unknown as typeof logsBox.value
})

afterEach(() => {
  cleanup()
  localStorage.clear()
  vi.restoreAllMocks()
  vi.clearAllMocks()
})

async function renderPage() {
  const pinia = createPinia()
  setActivePinia(pinia)
  useMenusStore(pinia).roles = ['ROLE_ROOT']
  render(Tasks, { global: { plugins: [pinia, buildI18n('zh-CN'), VueQueryPlugin] } })
}

describe('定时任务页', () => {
  it('列表渲染：状态与调度态、暂停恢复按钮随期望态翻转', async () => {
    await renderPage()
    expect(document.body.textContent).toContain('样例任务')
    expect(document.body.textContent).toContain('夜间清理')
    expect(document.body.textContent).toContain('sampleJob.run')
    // 启用行给暂停、暂停行给恢复
    expect(byTest('tasks-pause').length).toBe(1)
    expect(byTest('tasks-resume').length).toBe(1)
  })

  it('暂停恢复行内直驱：调对应接口', async () => {
    await renderPage()
    await fireEvent.click(byTest('tasks-pause')[0]!)
    await waitFor(() => expect(pauseAsync).toHaveBeenCalledWith(1))
    await fireEvent.click(byTest('tasks-resume')[0]!)
    await waitFor(() => expect(resumeAsync).toHaveBeenCalledWith(2))
  })

  it('新增表单：目标下拉取 targets 清单，预设选即填可手改', async () => {
    await renderPage()
    await fireEvent.click(byTest('tasks-create')[0]!)
    const dialog = document.querySelector('[data-test="tasks-dialog"]') as HTMLElement
    await waitFor(() => expect(dialog).toBeTruthy())
    // 目标下拉选项在册（演示 Bean）
    await pickSelectOption('tasks-target', 'sampleJob.run')
    expect(inputByTest('tasks-cron')).toBeTruthy()
    // 预设选即填
    await pickSelectOption('tasks-cron-presets', '每分钟')
    await waitFor(() => expect(inputByTest('tasks-cron').value).toBe('0 * * * * ?'))
    // 手改保留
    await fireEvent.update(inputByTest('tasks-cron'), '0 5 * * * ?')
    await fireEvent.update(inputByTest('tasks-name'), 'E2E任务')
    await fireEvent.click(byTest('tasks-submit')[0]!)
    await waitFor(() => {
      const payload = saveAsync.mock.calls[0]?.[0] as {
        beanName: string
        methodName: string
        cron: string
      }
      expect(payload.beanName).toBe('sampleJob')
      expect(payload.methodName).toBe('run')
      expect(payload.cron).toBe('0 5 * * * ?')
    })
  })

  it('cron 前端初检只提示：非法结构出提示且不阻断保存，422 后端消息直显', async () => {
    saveAsync.mockRejectedValueOnce(new Error('cron 表达式不合法'))
    await renderPage()
    await fireEvent.click(byTest('tasks-create')[0]!)
    await waitFor(() => expect(document.querySelector('[data-test="tasks-dialog"]')).toBeTruthy())
    await fireEvent.update(inputByTest('tasks-name'), '坏任务')
    await pickSelectOption('tasks-target', 'sampleJob.run')
    await fireEvent.update(inputByTest('tasks-cron'), '0 0 0')
    // 初检提示在册
    await waitFor(() => {
      const hint = document.querySelector('[data-test="tasks-cron-hint"]')
      expect(hint?.textContent).toContain('6~7 个字段')
    })
    // 不阻断：保存仍发起，后端消息直显
    await fireEvent.click(byTest('tasks-submit')[0]!)
    await waitFor(() => {
      const error = document.querySelector('.el-message--error')
      expect(error?.textContent).toContain('cron 表达式不合法')
    })
  })

  it('targets 清单为空：目标下拉空态提示', async () => {
    targetsBox.value = []
    await renderPage()
    await fireEvent.click(byTest('tasks-create')[0]!)
    await waitFor(() => {
      expect(document.body.textContent).toContain('无可用任务目标')
    })
  })

  it('立即执行：确认后触发，取消不调', async () => {
    await renderPage()
    await fireEvent.click(byTest('tasks-exec')[0]!)
    await waitFor(() => expect(execAsync).toHaveBeenCalledWith(1))
    expect(confirmMock).toHaveBeenCalledTimes(1)

    confirmMock.mockRejectedValueOnce(new Error('cancel'))
    await fireEvent.click(byTest('tasks-exec')[1]!)
    await waitFor(() => expect(confirmMock).toHaveBeenCalledTimes(2))
    await waitFor(() => expect(execAsync).toHaveBeenCalledTimes(1))
  })

  it('删除确认：确认后调接口', async () => {
    await renderPage()
    await fireEvent.click(byTest('tasks-delete')[0]!)
    await waitFor(() => expect(removeAsync).toHaveBeenCalledWith(1))
  })

  it('执行日志弹窗：分页数据渲染、失败摘要可见、手动刷新', async () => {
    await renderPage()
    await fireEvent.click(byTest('tasks-logs')[0]!)
    const dialog = document.querySelector('[data-test="tasks-log-dialog"]') as HTMLElement
    await waitFor(() => expect(dialog).toBeTruthy())
    expect(document.body.textContent).toContain('2026-11-04 12:00:00')
    // 失败行异常摘要完整可见
    expect(document.body.textContent).toContain(
      'RuntimeException: 目标方法抛出异常（摘要完整不截断）',
    )
    // 手动刷新触发重查
    await fireEvent.click(byTest('tasks-log-refresh')[0]!)
    expect(refetchLogsMock).toHaveBeenCalledTimes(1)
  })
})

describe('cron 纯函数', () => {
  it('cronHint 合法结构放行、可疑结构给提示 key', () => {
    expect(cronHint('0 * * * * ?')).toBeNull()
    expect(cronHint('0 0 0 ? * MON 2026')).toBeNull()
    expect(cronHint('')).toBe('tasks.cronHintEmpty')
    expect(cronHint('0 0 0')).toBe('tasks.cronHintFields')
    expect(cronHint('0 0 0 * * ^')).toBe('tasks.cronHintChars')
  })

  it('预设常量 5 项且表达式结构合法', () => {
    expect(CRON_PRESETS).toHaveLength(5)
    for (const preset of CRON_PRESETS) {
      expect(cronHint(preset.expression)).toBeNull()
      expect(preset.labelKey).toContain('tasks.')
    }
  })
})
