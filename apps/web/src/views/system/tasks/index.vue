<script setup lang="ts">
import {
  ElButton,
  ElDialog,
  ElForm,
  ElFormItem,
  ElInput,
  ElMessage,
  ElMessageBox,
  ElOption,
  ElRadioButton,
  ElRadioGroup,
  ElSelect,
  ElTag,
} from 'element-plus'
import { computed, h, ref } from 'vue'
import { useI18n } from 'vue-i18n'
import { useTableTexts } from '@/composables/use-table-texts'
import { formatDateTime } from '@/utils/datetime'

import {
  useTaskExec,
  useTaskList,
  useTaskLogs,
  useTaskPause,
  useTaskRemove,
  useTaskResume,
  useTaskSave,
  useTaskTargets,
} from '@/api/tasks'
import type { TaskLog, TaskView } from '@/api/generated'
import { DataTable } from '@eladmin-plus/admin-ui'
import type { DataTableColumn } from '@eladmin-plus/admin-ui'
import { useAuth } from '@/composables/use-auth'
import { cronHint, CRON_PRESETS } from './cron'

defineOptions({ name: 'SystemTasks' })

const { t } = useI18n()
const tableTexts = useTableTexts()
const { hasAuth } = useAuth()

const { data: tasks, isLoading } = useTaskList()
const saveTask = useTaskSave()
const removeTask = useTaskRemove()
const pauseTask = useTaskPause()
const resumeTask = useTaskResume()
const execTask = useTaskExec()

/** —— 清单：整表返回，客户端分页 —— */

const page = ref(0)
const pageSize = ref(20)

const allRows = computed(() => tasks.value ?? [])
const total = computed(() => allRows.value.length)
const rows = computed(() =>
  allRows.value.slice(page.value * pageSize.value, (page.value + 1) * pageSize.value),
)

const SCHEDULE_STATE_KEYS: Record<string, string> = {
  running: 'tasks.scheduleRunning',
  paused: 'tasks.schedulePaused',
  none: 'tasks.scheduleNone',
}

function statusTag(task: TaskView) {
  return h(ElTag, { type: task.status === 1 ? 'success' : 'info', size: 'small' }, () =>
    task.status === 1 ? t('tasks.enabled') : t('tasks.paused'),
  )
}

const columns = computed<DataTableColumn<TaskView>[]>(() => [
  { accessorKey: 'name', header: t('tasks.name') },
  {
    id: 'target',
    header: t('tasks.target'),
    cell: ({ row }) =>
      h('span', [
        `${row.original.beanName}.${row.original.methodName}`,
        row.original.param
          ? h('span', { class: 'tasks__param' }, `（${row.original.param}）`)
          : null,
      ]),
  },
  { accessorKey: 'cron', header: t('tasks.cron') },
  {
    id: 'status',
    header: t('tasks.status'),
    cell: ({ row }) =>
      h('span', { class: 'tasks__status' }, [
        statusTag(row.original),
        h(
          'span',
          { class: 'tasks__schedule' },
          SCHEDULE_STATE_KEYS[row.original.scheduleState ?? '']
            ? t(SCHEDULE_STATE_KEYS[row.original.scheduleState ?? ''])
            : '',
        ),
      ]),
  },
  {
    id: 'actions',
    header: t('tasks.actions'),
    cell: ({ row }) =>
      h('span', { class: 'tasks__actions' }, [
        hasAuth('system:task:edit')
          ? h(
              ElButton,
              {
                link: true,
                type: row.original.status === 1 ? 'warning' : 'success',
                'data-test': row.original.status === 1 ? 'tasks-pause' : 'tasks-resume',
                onClick: () => onToggle(row.original),
              },
              () => (row.original.status === 1 ? t('tasks.pause') : t('tasks.resume')),
            )
          : null,
        hasAuth('system:task:edit')
          ? h(
              ElButton,
              {
                link: true,
                type: 'primary',
                'data-test': 'tasks-exec',
                onClick: () => onExec(row.original),
              },
              () => t('tasks.exec'),
            )
          : null,
        hasAuth('system:task:list')
          ? h(
              ElButton,
              {
                link: true,
                type: 'primary',
                'data-test': 'tasks-logs',
                onClick: () => openLogs(row.original),
              },
              () => t('tasks.logs'),
            )
          : null,
        hasAuth('system:task:edit')
          ? h(
              ElButton,
              {
                link: true,
                type: 'primary',
                'data-test': 'tasks-edit',
                onClick: () => openEdit(row.original),
              },
              () => t('tasks.edit'),
            )
          : null,
        hasAuth('system:task:del')
          ? h(
              ElButton,
              {
                link: true,
                type: 'danger',
                'data-test': 'tasks-delete',
                onClick: () => onRemove(row.original),
              },
              () => t('tasks.delete'),
            )
          : null,
      ]),
  },
])

/** —— 表单：目标下拉 + cron 文本预设 + 单 String 参数 —— */

const dialogVisible = ref(false)
const submitting = ref(false)
const model = ref<{
  id: number | null
  name: string
  target: string
  param: string
  cron: string
  status: number
}>({ id: null, name: '', target: '', param: '', cron: '', status: 1 })

const preset = ref('')

const { data: targets } = useTaskTargets()
const targetOptions = computed(() =>
  (targets.value ?? []).map((item) => ({
    value: `${item.beanName}.${item.methodName}`,
    label: item.param
      ? `${item.beanName}.${item.methodName}（${item.param}）`
      : `${item.beanName}.${item.methodName}`,
  })),
)

/** cron 前端初检：只提示不阻断（后端 422 权威直显） */
const cronHintText = computed(() => {
  const hint = cronHint(model.value.cron)
  return hint ? t(hint) : ''
})

function onPresetChange(expression: string) {
  if (expression) {
    model.value.cron = expression
  }
}

function openCreate() {
  model.value = { id: null, name: '', target: '', param: '', cron: '', status: 1 }
  preset.value = ''
  dialogVisible.value = true
}

function openEdit(row: TaskView) {
  model.value = {
    id: row.id ?? null,
    name: row.name ?? '',
    target: `${row.beanName ?? ''}.${row.methodName ?? ''}`,
    param: row.param ?? '',
    cron: row.cron ?? '',
    status: row.status ?? 1,
  }
  preset.value = ''
  dialogVisible.value = true
}

async function onSubmit() {
  if (!model.value.name.trim()) {
    ElMessage.warning(t('tasks.nameRequired'))
    return
  }
  if (!model.value.target) {
    ElMessage.warning(t('tasks.targetRequired'))
    return
  }
  const dot = model.value.target.lastIndexOf('.')
  const beanName = model.value.target.slice(0, dot)
  const methodName = model.value.target.slice(dot + 1)
  submitting.value = true
  try {
    await saveTask.mutateAsync({
      id: model.value.id ?? undefined,
      name: model.value.name.trim(),
      beanName,
      methodName,
      param: model.value.param.trim() || undefined,
      cron: model.value.cron.trim(),
      status: model.value.status,
    })
    ElMessage.success(t('tasks.saveSuccess'))
    dialogVisible.value = false
  } catch (error) {
    // cron 非法 422、目标不在白名单 422、任务名唯一 409 由后端权威拒绝，toast 直显
    ElMessage.error(error instanceof Error ? error.message : t('tasks.saveFailed'))
  } finally {
    submitting.value = false
  }
}

/** —— 行内操作：暂停恢复直驱、立即执行确认、删除确认 —— */

async function onToggle(row: TaskView) {
  try {
    if (row.status === 1) {
      await pauseTask.mutateAsync(row.id ?? 0)
      ElMessage.success(t('tasks.pausedOk'))
    } else {
      await resumeTask.mutateAsync(row.id ?? 0)
      ElMessage.success(t('tasks.resumedOk'))
    }
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : t('tasks.toggleFailed'))
  }
}

async function onExec(row: TaskView) {
  try {
    await ElMessageBox.confirm(
      t('tasks.execConfirm', { name: row.name ?? '' }),
      t('tasks.execTitle'),
      { type: 'info' },
    )
  } catch {
    return
  }
  try {
    // 立即异步执行一次：不阻塞请求，落表随日志弹窗查询
    await execTask.mutateAsync(row.id ?? 0)
    ElMessage.success(t('tasks.execOk'))
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : t('tasks.execFailed'))
  }
}

async function onRemove(row: TaskView) {
  try {
    await ElMessageBox.confirm(
      t('tasks.deleteConfirm', { name: row.name ?? '' }),
      t('tasks.deleteTitle'),
      {
        type: 'warning',
      },
    )
  } catch {
    return
  }
  try {
    await removeTask.mutateAsync(row.id ?? 0)
    ElMessage.success(t('tasks.deleteSuccess'))
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : t('tasks.deleteFailed'))
  }
}

/** —— 执行日志弹窗：服务端分页 + 手动刷新，失败行异常摘要完整可见 —— */

const logTask = ref<TaskView | null>(null)
const logPage = ref(0)
const logPageSize = ref(10)
const logDialogVisible = computed({
  get: () => logTask.value !== null,
  set: (visible: boolean) => {
    if (!visible) {
      logTask.value = null
    }
  },
})
const logTaskId = computed(() => logTask.value?.id ?? null)

const {
  data: logs,
  isLoading: logsLoading,
  refetch: refetchLogs,
} = useTaskLogs(logTaskId, logPage, logPageSize)

const logRows = computed(() => logs.value?.content ?? [])
const logTotal = computed(() => logs.value?.total ?? 0)

const logColumns = computed<DataTableColumn<TaskLog>[]>(() => [
  {
    accessorKey: 'executeTime',
    header: t('tasks.logTime'),
    cell: ({ row }) => h('span', formatDateTime(row.original.executeTime)),
  },
  {
    accessorKey: 'success',
    header: t('tasks.logResult'),
    cell: ({ row }) =>
      h(ElTag, { type: row.original.success === 1 ? 'success' : 'danger', size: 'small' }, () =>
        row.original.success === 1 ? t('tasks.logSuccess') : t('tasks.logFailure'),
      ),
  },
  {
    accessorKey: 'durationMs',
    header: t('tasks.logDuration'),
    cell: ({ row }) =>
      h('span', row.original.durationMs != null ? `${row.original.durationMs} ms` : ''),
  },
  {
    accessorKey: 'errorMsg',
    header: t('tasks.logError'),
    cell: ({ row }) => h('span', { class: 'tasks__error' }, row.original.errorMsg ?? ''),
  },
])

function openLogs(row: TaskView) {
  logTask.value = row
  logPage.value = 0
}

function refreshLogs() {
  void refetchLogs()
}
</script>

<template>
  <div class="tasks" data-test="tasks-page">
    <div class="tasks__toolbar">
      <el-button
        v-if="hasAuth('system:task:add')"
        type="primary"
        data-test="tasks-create"
        @click="openCreate"
      >
        {{ t('tasks.create') }}
      </el-button>
    </div>

    <DataTable
      :texts="tableTexts"
      :columns="columns"
      :data="rows"
      :loading="isLoading"
      :page="page"
      :page-size="pageSize"
      :total="total"
      data-test="tasks-table"
      @page-change="
        ({ page: p, pageSize: s }) => {
          page = p
          pageSize = s
        }
      "
    />

    <el-dialog
      v-model="dialogVisible"
      :title="model.id ? t('tasks.edit') : t('tasks.create')"
      width="640px"
      destroy-on-close
      data-test="tasks-dialog"
    >
      <el-form label-width="90px">
        <el-form-item :label="t('tasks.name')" data-field="name">
          <el-input v-model="model.name" data-test="tasks-name" />
        </el-form-item>
        <el-form-item :label="t('tasks.target')" data-field="target">
          <span data-test="tasks-target">
            <el-select
              v-model="model.target"
              :disabled="!targetOptions.length"
              :placeholder="t('tasks.noTargets')"
              :teleported="false"
            >
              <el-option
                v-for="option in targetOptions"
                :key="option.value"
                :value="option.value"
                :label="option.label"
              />
            </el-select>
          </span>
        </el-form-item>
        <el-form-item :label="t('tasks.param')" data-field="param">
          <el-input
            v-model="model.param"
            :placeholder="t('tasks.paramPlaceholder')"
            data-test="tasks-param"
          />
        </el-form-item>
        <el-form-item :label="t('tasks.cron')" data-field="cron">
          <el-input v-model="model.cron" data-test="tasks-cron" />
          <span v-if="cronHintText" class="tasks__cron-hint" data-test="tasks-cron-hint">
            {{ cronHintText }}
          </span>
          <span data-test="tasks-cron-presets">
            <el-select
              v-model="preset"
              clearable
              :placeholder="t('tasks.cronPresetPlaceholder')"
              :teleported="false"
              @change="onPresetChange"
            >
              <el-option
                v-for="item in CRON_PRESETS"
                :key="item.expression"
                :value="item.expression"
                :label="`${t(item.labelKey)}（${item.expression}）`"
              />
            </el-select>
          </span>
        </el-form-item>
        <el-form-item :label="t('tasks.status')" data-field="status">
          <el-radio-group v-model="model.status">
            <el-radio-button :value="1">{{ t('tasks.enabled') }}</el-radio-button>
            <el-radio-button :value="0">{{ t('tasks.paused') }}</el-radio-button>
          </el-radio-group>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button data-test="tasks-cancel" @click="dialogVisible = false">
          {{ t('tasks.cancel') }}
        </el-button>
        <el-button type="primary" :loading="submitting" data-test="tasks-submit" @click="onSubmit">
          {{ t('tasks.submit') }}
        </el-button>
      </template>
    </el-dialog>

    <el-dialog
      v-model="logDialogVisible"
      :title="t('tasks.logTitle', { name: logTask?.name ?? '' })"
      width="760px"
      destroy-on-close
      data-test="tasks-log-dialog"
    >
      <div class="tasks__log-toolbar">
        <el-button size="small" data-test="tasks-log-refresh" @click="refreshLogs">
          {{ t('tasks.logRefresh') }}
        </el-button>
      </div>
      <DataTable
        :texts="tableTexts"
        :columns="logColumns"
        :data="logRows"
        :loading="logsLoading"
        :page="logPage"
        :page-size="logPageSize"
        :total="logTotal"
        data-test="tasks-log-table"
        @page-change="
          ({ page: p, pageSize: s }) => {
            logPage = p
            logPageSize = s
          }
        "
      />
    </el-dialog>
  </div>
</template>

<style scoped lang="scss">
.tasks__toolbar {
  display: flex;
  gap: 12px;
  align-items: center;
  margin-bottom: 12px;
}

.tasks__param {
  margin-left: 4px;
  font-size: 12px;
  color: var(--el-text-color-secondary, #909399);
}

.tasks__status {
  display: inline-flex;
  gap: 8px;
  align-items: center;
}

.tasks__schedule {
  font-size: 12px;
  color: var(--el-text-color-secondary, #909399);
}

.tasks__actions {
  display: inline-flex;
  gap: 8px;
}

.tasks__cron-hint {
  width: 100%;
  font-size: 12px;
  color: var(--el-color-warning, #e6a23c);
}

.tasks__log-toolbar {
  display: flex;
  justify-content: flex-end;
  margin-bottom: 8px;
}

:deep(.el-select) {
  width: 100%;
}

:deep(.tasks__error) {
  white-space: normal;
  word-break: break-all;
}
</style>
