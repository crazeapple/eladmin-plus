<script setup lang="ts">
import { ElButton, ElMessage, ElMessageBox } from 'element-plus'
import { computed, h, reactive, ref } from 'vue'
import { useI18n } from 'vue-i18n'
import { useTableTexts } from '@/composables/use-table-texts'
import { formatDateTime } from '@/utils/datetime'

import { DataTable } from '@eladmin-plus/admin-ui'
import type { DataTableColumn } from '@eladmin-plus/admin-ui'

import type { BackupRecordView } from '@/api/generated'
import { exportBackupRecord, useBackupRecordRemove, useBackupRecords } from '@/api/database'
import type { BackupRecordQuery } from '@/api/database'
import { useAuth } from '@/composables/use-auth'

/** 备份记录区块：存储来源视图（storage_file source=backup）分页列表 + 文件名模糊筛选 + 行级导出与删除 */
defineOptions({ name: 'DatabaseRecordPanel' })

const { t } = useI18n()
const tableTexts = useTableTexts()
const { hasAuth } = useAuth()

const page = ref(0)
const pageSize = ref(20)
const filter = reactive<{ filename: string }>({ filename: '' })
const applied = reactive<{ filename?: string }>({})

const query = computed<BackupRecordQuery>(() => ({
  page: page.value,
  size: pageSize.value,
  filename: applied.filename,
}))

const { data, isLoading } = useBackupRecords(query)
const removeRecord = useBackupRecordRemove()

function applyFilters() {
  applied.filename = filter.filename.trim() || undefined
  page.value = 0
}

function onReset() {
  filter.filename = ''
  applied.filename = undefined
  page.value = 0
}

/** 行级导出：blob 落盘原名 sql 文件；已删文件 404 提示（后端消息权威） */
const exporting = ref(false)

async function onExport(row: BackupRecordView) {
  if (exporting.value) {
    return
  }
  exporting.value = true
  try {
    const filename = await exportBackupRecord(row.id ?? 0)
    ElMessage.success(t('database.exportSuccess', { name: filename }))
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : t('database.exportFailed'))
  } finally {
    exporting.value = false
  }
}

/** 行级删除：删除代理删存储对象与记录行（后端语义），确认后行消失 */
async function onRemove(row: BackupRecordView) {
  try {
    await ElMessageBox.confirm(
      t('database.removeOneConfirm', { name: row.fileName ?? '' }),
      t('database.deleteTitle'),
      { type: 'warning' },
    )
  } catch {
    return
  }
  try {
    await removeRecord.mutateAsync(row.id ?? 0)
    ElMessage.success(t('database.removeSuccess'))
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : t('database.removeFailed'))
  }
}

/** 展示用大小：字节人性化（B / KB / MB / GB），存储页同款 */
function formatSize(bytes: number | undefined): string {
  const value = bytes ?? 0
  if (value < 1024) {
    return `${value} B`
  }
  const kb = value / 1024
  if (kb < 1024) {
    return `${kb.toFixed(1)} KB`
  }
  const mb = kb / 1024
  if (mb < 1024) {
    return `${mb.toFixed(1)} MB`
  }
  return `${(mb / 1024).toFixed(2)} GB`
}

const rows = computed(() => data.value?.content ?? [])
const total = computed(() => data.value?.total ?? 0)

const columns = computed<DataTableColumn<BackupRecordView>[]>(() => [
  { accessorKey: 'dbName', header: t('database.dbName') },
  { accessorKey: 'fileName', header: t('database.fileName') },
  {
    accessorKey: 'size',
    header: t('database.size'),
    cell: ({ row }) => h('span', formatSize(row.original.size)),
  },
  {
    accessorKey: 'createdAt',
    header: t('database.backupAt'),
    cell: ({ row }) => h('span', formatDateTime(row.original.createdAt)),
  },
  {
    id: 'actions',
    header: t('database.actions'),
    cell: ({ row }) =>
      h('span', { class: 'database-record__row-actions' }, [
        // 导出端点授权面即 backup 点（列表可见走 list 点，行内动作分立挂点）
        hasAuth('database:backup')
          ? h(
              ElButton,
              {
                link: true,
                type: 'primary',
                'data-test': 'database-record-export',
                onClick: () => onExport(row.original),
              },
              () => t('database.export'),
            )
          : null,
        hasAuth('database:del')
          ? h(
              ElButton,
              {
                link: true,
                type: 'danger',
                'data-test': 'database-record-delete',
                onClick: () => onRemove(row.original),
              },
              () => t('database.delete'),
            )
          : null,
      ]),
  },
])
</script>

<template>
  <div class="database-record" data-test="database-record-panel">
    <div class="database-record__filter">
      <el-input
        v-model="filter.filename"
        :placeholder="t('database.filterFilename')"
        clearable
        data-test="database-record-filter"
        @change="applyFilters"
        @clear="applyFilters"
      />
      <el-button data-test="database-record-reset" @click="onReset">
        {{ t('database.reset') }}
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
      data-test="database-record-table"
      @page-change="
        ({ page: p, pageSize: s }) => {
          page = p
          pageSize = s
        }
      "
    />
  </div>
</template>

<style scoped lang="scss">
.database-record__filter {
  display: flex;
  gap: 12px;
  align-items: center;
  margin-bottom: 12px;

  .el-input {
    width: 220px;
  }
}

.database-record__row-actions {
  display: inline-flex;
  gap: 4px;
}
</style>
