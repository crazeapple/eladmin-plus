<script setup lang="ts">
import { ElButton, ElMessage, ElMessageBox } from 'element-plus'
import { computed, h, reactive, ref } from 'vue'
import { useI18n } from 'vue-i18n'

import { DataTable } from '@eladmin-plus/admin-ui'
import type { DataTableColumn } from '@eladmin-plus/admin-ui'

import type { OperationLog } from '@/api/generated'
import {
  exportOperationLogs,
  useOperationLogClear,
  useOperationLogPage,
  useOperationLogRemove,
  useOperationLogRemoveBatch,
} from '@/api/monitor'
import type { LogPageQuery } from '@/api/monitor'
import { useAuth } from '@/composables/use-auth'

defineOptions({ name: 'MonitorOperationLogs' })

const { t } = useI18n()
const { hasAuth } = useAuth()

/** 四维筛选：文本框 blur / 回车即应用（筛选变更即重查并重置分页），日期选毕即应用 */
const page = ref(0)
const pageSize = ref(20)
const filter = reactive<{
  username: string
  ip: string
  description: string
  range: [string, string] | null
}>({ username: '', ip: '', description: '', range: null })
const applied = reactive<{
  username?: string
  ip?: string
  description?: string
  startTime?: string
  endTime?: string
}>({})

const query = computed<LogPageQuery>(() => ({
  page: page.value,
  size: pageSize.value,
  username: applied.username,
  ip: applied.ip,
  description: applied.description,
  startTime: applied.startTime,
  endTime: applied.endTime,
}))

const { data, isLoading } = useOperationLogPage(query)
const removeOne = useOperationLogRemove()
const removeBatch = useOperationLogRemoveBatch()
const clearAll = useOperationLogClear()

function applyFilters() {
  applied.username = filter.username.trim() || undefined
  applied.ip = filter.ip.trim() || undefined
  applied.description = filter.description.trim() || undefined
  applied.startTime = filter.range ? `${filter.range[0]}T00:00:00` : undefined
  applied.endTime = filter.range ? `${filter.range[1]}T23:59:59` : undefined
  page.value = 0
}

function onRangeChange() {
  applyFilters()
}

function onReset() {
  filter.username = ''
  filter.ip = ''
  filter.description = ''
  filter.range = null
  applied.username = undefined
  applied.ip = undefined
  applied.description = undefined
  applied.startTime = undefined
  applied.endTime = undefined
  page.value = 0
}

/** —— 详情弹窗：字段全文（请求参数不截断，pre 换行展示） —— */

const detailVisible = ref(false)
const detail = ref<OperationLog | null>(null)

function openDetail(row: OperationLog) {
  detail.value = row
  detailVisible.value = true
}

/** 展示用时间：ISO 的 T 换空格（仅显示层，筛选仍用原值） */
function displayTime(value: string | undefined): string {
  return value?.replace('T', ' ') ?? ''
}

/** —— 删除 / 批删 / 清空（一律二次确认，取消不调接口） —— */

async function onRemoveOne(row: OperationLog) {
  try {
    await ElMessageBox.confirm(
      t('operationLogs.removeOneConfirm', { name: row.description || (row.id ?? '') }),
      t('operationLogs.deleteTitle'),
      { type: 'warning' },
    )
  } catch {
    return
  }
  try {
    await removeOne.mutateAsync(row.id ?? 0)
    ElMessage.success(t('operationLogs.removeSuccess'))
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : t('operationLogs.removeFailed'))
  }
}

const selectedRows = ref<OperationLog[]>([])

function onSelectionChange(rows: OperationLog[]) {
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
      t('operationLogs.batchRemoveConfirm', { count: selectedIds.value.length }),
      t('operationLogs.deleteTitle'),
      { type: 'warning' },
    )
  } catch {
    return
  }
  try {
    await removeBatch.mutateAsync(selectedIds.value)
    ElMessage.success(t('operationLogs.removeSuccess'))
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : t('operationLogs.removeFailed'))
  }
}

async function onClearAll() {
  try {
    await ElMessageBox.confirm(t('operationLogs.clearConfirm'), t('operationLogs.clearTitle'), {
      type: 'warning',
    })
  } catch {
    return
  }
  try {
    await clearAll.mutateAsync()
    ElMessage.success(t('operationLogs.clearSuccess'))
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : t('operationLogs.clearFailed'))
  }
}

/** —— 导出：复用当前筛选（不分页），请求期间按钮防重复点击 —— */

const exporting = ref(false)

async function onExport() {
  if (exporting.value) {
    return
  }
  exporting.value = true
  try {
    const filename = await exportOperationLogs(query.value)
    ElMessage.success(t('operationLogs.exportSuccess', { name: filename }))
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : t('operationLogs.exportFailed'))
  } finally {
    exporting.value = false
  }
}

/** —— 列定义 —— */

const columns = computed<DataTableColumn<OperationLog>[]>(() => [
  { id: 'selection', type: 'selection' },
  { accessorKey: 'username', header: t('operationLogs.username') },
  { accessorKey: 'ip', header: t('operationLogs.ip') },
  {
    accessorKey: 'description',
    header: t('operationLogs.description'),
    cell: ({ row }) => h('span', { class: 'op-logs__desc' }, row.original.description ?? ''),
  },
  {
    accessorKey: 'elapsedMs',
    header: t('operationLogs.elapsedMs'),
    cell: ({ row }) =>
      h('span', row.original.elapsedMs === undefined ? '' : `${row.original.elapsedMs} ms`),
  },
  {
    accessorKey: 'createdAt',
    header: t('operationLogs.createdAt'),
    cell: ({ row }) => h('span', displayTime(row.original.createdAt)),
  },
  {
    id: 'actions',
    header: t('operationLogs.actions'),
    cell: ({ row }) =>
      h('span', { class: 'op-logs__row-actions' }, [
        hasAuth('monitor:log:list')
          ? h(
              ElButton,
              {
                link: true,
                type: 'primary',
                'data-test': 'op-logs-detail',
                onClick: () => openDetail(row.original),
              },
              () => t('operationLogs.detail'),
            )
          : null,
        hasAuth('monitor:log:del')
          ? h(
              ElButton,
              {
                link: true,
                type: 'danger',
                'data-test': 'op-logs-remove',
                onClick: () => onRemoveOne(row.original),
              },
              () => t('operationLogs.delete'),
            )
          : null,
      ]),
  },
])

const rows = computed(() => data.value?.content ?? [])
const total = computed(() => data.value?.total ?? 0)
</script>

<template>
  <div class="op-logs" data-test="op-logs-page">
    <div class="op-logs__filter">
      <el-input
        v-model="filter.username"
        :placeholder="t('operationLogs.filterUsername')"
        clearable
        data-test="op-logs-filter-username"
        @change="applyFilters"
        @clear="applyFilters"
      />
      <el-input
        v-model="filter.ip"
        :placeholder="t('operationLogs.filterIp')"
        clearable
        data-test="op-logs-filter-ip"
        @change="applyFilters"
        @clear="applyFilters"
      />
      <el-input
        v-model="filter.description"
        :placeholder="t('operationLogs.filterDescription')"
        clearable
        data-test="op-logs-filter-description"
        @change="applyFilters"
        @clear="applyFilters"
      />
      <el-date-picker
        v-model="filter.range"
        type="daterange"
        value-format="YYYY-MM-DD"
        :start-placeholder="t('operationLogs.startDate')"
        :end-placeholder="t('operationLogs.endDate')"
        data-test="op-logs-filter-range"
        @change="onRangeChange"
      />
      <el-button data-test="op-logs-reset" @click="onReset">
        {{ t('operationLogs.reset') }}
      </el-button>
      <el-button
        v-if="hasAuth('monitor:log:list')"
        type="primary"
        plain
        :loading="exporting"
        data-test="op-logs-export"
        @click="onExport"
      >
        {{ t('operationLogs.export') }}
      </el-button>
      <el-button
        v-if="hasAuth('monitor:log:del')"
        type="danger"
        plain
        data-test="op-logs-clear"
        @click="onClearAll"
      >
        {{ t('operationLogs.clear') }}
      </el-button>
      <el-button
        v-if="hasAuth('monitor:log:del') && selectedIds.length"
        type="danger"
        plain
        data-test="op-logs-batch-remove"
        @click="onBatchRemove"
      >
        {{ t('operationLogs.batchRemove') }}
      </el-button>
    </div>

    <DataTable
      :columns="columns"
      :data="rows"
      :loading="isLoading"
      :page="page"
      :page-size="pageSize"
      :total="total"
      row-key="id"
      data-test="op-logs-table"
      @page-change="
        ({ page: p, pageSize: s }) => {
          page = p
          pageSize = s
        }
      "
      @selection-change="onSelectionChange"
    />

    <el-dialog
      v-model="detailVisible"
      :title="t('operationLogs.detailTitle')"
      width="680px"
      destroy-on-close
      data-test="op-logs-detail-dialog"
    >
      <template v-if="detail">
        <div class="op-logs__detail">
          <div class="op-logs__detail-row">
            <span class="op-logs__detail-label">{{ t('operationLogs.username') }}</span>
            <span data-test="op-logs-detail-username">{{ detail.username }}</span>
          </div>
          <div class="op-logs__detail-row">
            <span class="op-logs__detail-label">{{ t('operationLogs.ip') }}</span>
            <span data-test="op-logs-detail-ip">{{ detail.ip }}</span>
          </div>
          <div class="op-logs__detail-row">
            <span class="op-logs__detail-label">{{ t('operationLogs.description') }}</span>
            <span data-test="op-logs-detail-description">{{ detail.description }}</span>
          </div>
          <div class="op-logs__detail-row">
            <span class="op-logs__detail-label">{{ t('operationLogs.elapsedMs') }}</span>
            <span data-test="op-logs-detail-elapsed">
              {{ detail.elapsedMs === undefined ? '' : `${detail.elapsedMs} ms` }}
            </span>
          </div>
          <div class="op-logs__detail-row">
            <span class="op-logs__detail-label">{{ t('operationLogs.createdAt') }}</span>
            <span data-test="op-logs-detail-created">{{ displayTime(detail.createdAt) }}</span>
          </div>
          <div class="op-logs__detail-row">
            <span class="op-logs__detail-label">{{ t('operationLogs.requestParam') }}</span>
            <pre class="op-logs__param" data-test="op-logs-detail-param">{{
              detail.requestParam
            }}</pre>
          </div>
        </div>
      </template>
      <template #footer>
        <el-button data-test="op-logs-detail-close" @click="detailVisible = false">
          {{ t('operationLogs.close') }}
        </el-button>
      </template>
    </el-dialog>
  </div>
</template>

<style scoped lang="scss">
.op-logs__filter {
  display: flex;
  flex-wrap: wrap;
  gap: 12px;
  align-items: center;
  margin-bottom: 12px;

  .el-input {
    width: 160px;
  }
}

.op-logs__desc {
  display: inline-block;
  max-width: 260px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  vertical-align: bottom;
}

.op-logs__row-actions {
  display: inline-flex;
  gap: 4px;
}

.op-logs__detail {
  display: flex;
  flex-direction: column;
  gap: 10px;
}

.op-logs__detail-row {
  display: flex;
  gap: 12px;
  align-items: baseline;

  .op-logs__detail-label {
    width: 80px;
    flex-shrink: 0;
    color: var(--el-text-color-secondary);
  }
}

.op-logs__param {
  flex: 1;
  min-width: 0;
  max-height: 280px;
  overflow: auto;
  margin: 0;
  padding: 8px;
  white-space: pre-wrap;
  word-break: break-all;
  background: var(--el-fill-color-light);
  border-radius: 4px;
}
</style>
