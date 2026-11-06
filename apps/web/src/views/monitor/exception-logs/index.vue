<script setup lang="ts">
import { ElButton, ElMessage, ElMessageBox } from 'element-plus'
import { computed, h, reactive, ref } from 'vue'
import { useI18n } from 'vue-i18n'
import { useTableTexts } from '@/composables/use-table-texts'
import { formatDateTime } from '@/utils/datetime'

import { DataTable } from '@eladmin-plus/admin-ui'
import type { DataTableColumn } from '@eladmin-plus/admin-ui'

import type { ExceptionLog } from '@/api/generated'
import {
  exportExceptionLogs,
  useExceptionLogClear,
  useExceptionLogPage,
  useExceptionLogRemove,
  useExceptionLogRemoveBatch,
} from '@/api/monitor'
import type { ExceptionLogQuery } from '@/api/monitor'
import { useAuth } from '@/composables/use-auth'

defineOptions({ name: 'MonitorExceptionLogs' })

const { t } = useI18n()
const tableTexts = useTableTexts()
const { hasAuth } = useAuth()

/** 筛选：操作人 / IP 精确、异常消息模糊、创建时间闭区间；变更即重查并重置分页 */
const page = ref(0)
const pageSize = ref(20)
const filter = reactive<{
  username: string
  ip: string
  message: string
  range: [string, string] | null
}>({ username: '', ip: '', message: '', range: null })
const applied = reactive<{
  username?: string
  ip?: string
  message?: string
  startTime?: string
  endTime?: string
}>({})

const query = computed<ExceptionLogQuery>(() => ({
  page: page.value,
  size: pageSize.value,
  username: applied.username,
  ip: applied.ip,
  message: applied.message,
  startTime: applied.startTime,
  endTime: applied.endTime,
}))

const { data, isLoading } = useExceptionLogPage(query)
const removeOne = useExceptionLogRemove()
const removeBatch = useExceptionLogRemoveBatch()
const clearAll = useExceptionLogClear()

function applyFilters() {
  applied.username = filter.username.trim() || undefined
  applied.ip = filter.ip.trim() || undefined
  applied.message = filter.message.trim() || undefined
  applied.startTime = filter.range ? `${filter.range[0]}T00:00:00` : undefined
  applied.endTime = filter.range ? `${filter.range[1]}T23:59:59` : undefined
  page.value = 0
}

function onReset() {
  filter.username = ''
  filter.ip = ''
  filter.message = ''
  filter.range = null
  applied.username = undefined
  applied.ip = undefined
  applied.message = undefined
  applied.startTime = undefined
  applied.endTime = undefined
  page.value = 0
}

/** —— 堆栈抽屉：全文滚动展示 + 复制按钮 —— */

const stackVisible = ref(false)
const detail = ref<ExceptionLog | null>(null)

function openStack(row: ExceptionLog) {
  detail.value = row
  stackVisible.value = true
}

async function copyStack() {
  const text = detail.value?.stackTrace ?? ''
  if (!text) {
    return
  }
  try {
    if (navigator.clipboard?.writeText) {
      await navigator.clipboard.writeText(text)
    } else {
      // 无 clipboard API 环境：textarea + execCommand 兜底
      const textarea = document.createElement('textarea')
      textarea.value = text
      document.body.appendChild(textarea)
      textarea.select()
      document.execCommand('copy')
      textarea.remove()
    }
    ElMessage.success(t('exceptionLogs.copySuccess'))
  } catch {
    ElMessage.error(t('exceptionLogs.copyFailed'))
  }
}

/** —— 删除 / 批删 / 清空（一律二次确认，取消不调接口） —— */

async function onRemoveOne(row: ExceptionLog) {
  try {
    await ElMessageBox.confirm(
      t('exceptionLogs.removeOneConfirm', { name: row.name }),
      t('exceptionLogs.deleteTitle'),
      { type: 'warning' },
    )
  } catch {
    return
  }
  try {
    await removeOne.mutateAsync(row.id ?? 0)
    ElMessage.success(t('exceptionLogs.removeSuccess'))
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : t('exceptionLogs.removeFailed'))
  }
}

const selectedRows = ref<ExceptionLog[]>([])

function onSelectionChange(rows: ExceptionLog[]) {
  selectedRows.value = rows
}

const selectedIds = computed(() =>
  selectedRows.value.map((row) => row.id ?? 0).filter((id) => id > 0),
)

async function onBatchRemove() {
  if (!selectedIds.value.length) {
    return
  }
  try {
    await ElMessageBox.confirm(
      t('exceptionLogs.batchRemoveConfirm', { count: selectedIds.value.length }),
      t('exceptionLogs.deleteTitle'),
      { type: 'warning' },
    )
  } catch {
    return
  }
  try {
    await removeBatch.mutateAsync(selectedIds.value)
    ElMessage.success(t('exceptionLogs.removeSuccess'))
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : t('exceptionLogs.removeFailed'))
  }
}

async function onClearAll() {
  try {
    await ElMessageBox.confirm(t('exceptionLogs.clearConfirm'), t('exceptionLogs.clearTitle'), {
      type: 'warning',
    })
  } catch {
    return
  }
  try {
    await clearAll.mutateAsync()
    ElMessage.success(t('exceptionLogs.clearSuccess'))
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : t('exceptionLogs.clearFailed'))
  }
}

/** —— 导出：复用当前筛选（不分页，含堆栈列），请求期间按钮防重复点击 —— */

const exporting = ref(false)

async function onExport() {
  if (exporting.value) {
    return
  }
  exporting.value = true
  try {
    const filename = await exportExceptionLogs(query.value)
    ElMessage.success(t('exceptionLogs.exportSuccess', { name: filename }))
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : t('exceptionLogs.exportFailed'))
  } finally {
    exporting.value = false
  }
}

/** —— 列定义 —— */

const columns = computed<DataTableColumn<ExceptionLog>[]>(() => [
  { id: 'selection', type: 'selection' },
  { accessorKey: 'username', header: t('exceptionLogs.username') },
  { accessorKey: 'ip', header: t('exceptionLogs.ip') },
  { accessorKey: 'name', header: t('exceptionLogs.name') },
  {
    accessorKey: 'message',
    header: t('exceptionLogs.message'),
    cell: ({ row }) => h('span', { class: 'ex-logs__msg' }, row.original.message ?? ''),
  },
  {
    accessorKey: 'createdAt',
    header: t('exceptionLogs.createdAt'),
    cell: ({ row }) => h('span', formatDateTime(row.original.createdAt)),
  },
  {
    id: 'actions',
    header: t('exceptionLogs.actions'),
    cell: ({ row }) =>
      h('span', { class: 'ex-logs__row-actions' }, [
        hasAuth('monitor:log:list')
          ? h(
              ElButton,
              {
                link: true,
                type: 'primary',
                'data-test': 'ex-logs-stack',
                onClick: () => openStack(row.original),
              },
              () => t('exceptionLogs.stack'),
            )
          : null,
        hasAuth('monitor:log:del')
          ? h(
              ElButton,
              {
                link: true,
                type: 'danger',
                'data-test': 'ex-logs-remove',
                onClick: () => onRemoveOne(row.original),
              },
              () => t('exceptionLogs.delete'),
            )
          : null,
      ]),
  },
])

const rows = computed(() => data.value?.content ?? [])
const total = computed(() => data.value?.total ?? 0)
</script>

<template>
  <div class="ex-logs" data-test="ex-logs-page">
    <div class="ex-logs__filter">
      <el-input
        v-model="filter.username"
        :placeholder="t('exceptionLogs.filterUsername')"
        clearable
        data-test="ex-logs-filter-username"
        @change="applyFilters"
        @clear="applyFilters"
      />
      <el-input
        v-model="filter.ip"
        :placeholder="t('exceptionLogs.filterIp')"
        clearable
        data-test="ex-logs-filter-ip"
        @change="applyFilters"
        @clear="applyFilters"
      />
      <el-input
        v-model="filter.message"
        :placeholder="t('exceptionLogs.filterMessage')"
        clearable
        data-test="ex-logs-filter-message"
        @change="applyFilters"
        @clear="applyFilters"
      />
      <el-date-picker
        v-model="filter.range"
        type="daterange"
        value-format="YYYY-MM-DD"
        :start-placeholder="t('exceptionLogs.startDate')"
        :end-placeholder="t('exceptionLogs.endDate')"
        data-test="ex-logs-filter-range"
        @change="applyFilters"
      />
      <el-button data-test="ex-logs-reset" @click="onReset">
        {{ t('exceptionLogs.reset') }}
      </el-button>
      <el-button
        v-if="hasAuth('monitor:log:list')"
        type="primary"
        plain
        :loading="exporting"
        data-test="ex-logs-export"
        @click="onExport"
      >
        {{ t('exceptionLogs.export') }}
      </el-button>
      <el-button
        v-if="hasAuth('monitor:log:del')"
        type="danger"
        plain
        data-test="ex-logs-clear"
        @click="onClearAll"
      >
        {{ t('exceptionLogs.clear') }}
      </el-button>
      <el-button
        v-if="hasAuth('monitor:log:del') && selectedIds.length"
        type="danger"
        plain
        data-test="ex-logs-batch-remove"
        @click="onBatchRemove"
      >
        {{ t('exceptionLogs.batchRemove') }}
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
      row-key="id"
      data-test="ex-logs-table"
      @page-change="
        ({ page: p, pageSize: s }) => {
          page = p
          pageSize = s
        }
      "
      @selection-change="onSelectionChange"
    />

    <el-drawer
      v-model="stackVisible"
      :title="detail?.name"
      size="60%"
      destroy-on-close
      data-test="ex-logs-stack-drawer"
    >
      <div class="ex-logs__stack">
        <div class="ex-logs__stack-meta">
          <span data-test="ex-logs-stack-message">{{ detail?.message }}</span>
          <el-button
            type="primary"
            plain
            size="small"
            data-test="ex-logs-stack-copy"
            @click="copyStack"
          >
            {{ t('exceptionLogs.copy') }}
          </el-button>
        </div>
        <pre class="ex-logs__stack-text" data-test="ex-logs-stack-text">{{
          detail?.stackTrace
        }}</pre>
      </div>
    </el-drawer>
  </div>
</template>

<style scoped lang="scss">
.ex-logs__filter {
  display: flex;
  flex-wrap: wrap;
  gap: 12px;
  align-items: center;
  margin-bottom: 12px;

  .el-input {
    width: 160px;
  }
}

.ex-logs__msg {
  display: inline-block;
  max-width: 280px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  vertical-align: bottom;
}

.ex-logs__row-actions {
  display: inline-flex;
  gap: 4px;
}

.ex-logs__stack {
  display: flex;
  flex-direction: column;
  gap: 12px;
  height: 100%;
}

.ex-logs__stack-meta {
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 12px;
}

.ex-logs__stack-text {
  flex: 1;
  min-height: 0;
  overflow: auto;
  margin: 0;
  padding: 12px;
  white-space: pre;
  background: var(--el-fill-color-light);
  border-radius: 4px;
}
</style>
