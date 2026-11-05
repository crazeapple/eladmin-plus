<script setup lang="ts">
import { ElButton, ElMessage, ElMessageBox, type UploadFile } from 'element-plus'
import { computed, h, reactive, ref } from 'vue'
import { useI18n } from 'vue-i18n'

import { DataTable } from '@eladmin-plus/admin-ui'
import type { DataTableColumn } from '@eladmin-plus/admin-ui'

import type { StorageFileView } from '@/api/generated'
import { useStoragePage, useStorageRemove, useStorageUpload } from '@/api/storage'
import type { StoragePageQuery } from '@/api/storage'
import { useAuth } from '@/composables/use-auth'

defineOptions({ name: 'ToolsStorage' })

const { t } = useI18n()
const { hasAuth } = useAuth()

/** 三维筛选：原名模糊、上传人精确、时间范围；文本回车即应用，日期选毕即应用 */
const page = ref(0)
const pageSize = ref(20)
const filter = reactive<{ name: string; uploader: string; range: [string, string] | null }>({
  name: '',
  uploader: '',
  range: null,
})
const applied = reactive<{
  name?: string
  uploader?: string
  startTime?: string
  endTime?: string
}>({})

const query = computed<StoragePageQuery>(() => ({
  page: page.value,
  size: pageSize.value,
  name: applied.name,
  uploader: applied.uploader,
  startTime: applied.startTime,
  endTime: applied.endTime,
}))

const { data, isLoading } = useStoragePage(query)
const upload = useStorageUpload()
const removeOne = useStorageRemove()

function applyFilters() {
  applied.name = filter.name.trim() || undefined
  applied.uploader = filter.uploader.trim() || undefined
  applied.startTime = filter.range ? `${filter.range[0]}T00:00:00` : undefined
  applied.endTime = filter.range ? `${filter.range[1]}T23:59:59` : undefined
  page.value = 0
}

function onReset() {
  filter.name = ''
  filter.uploader = ''
  filter.range = null
  applied.name = undefined
  applied.uploader = undefined
  applied.startTime = undefined
  applied.endTime = undefined
  page.value = 0
}

/** —— 上传：选文件即直传（页内不缓存未上传文件），成功刷新列表（hook 内失效） —— */

const uploading = ref(false)

async function onFileChange(file: UploadFile) {
  const raw = file.raw
  if (!raw || uploading.value) {
    return
  }
  uploading.value = true
  try {
    await upload.mutateAsync(raw)
    ElMessage.success(t('storage.uploadSuccess', { name: raw.name }))
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : t('storage.uploadFailed'))
  } finally {
    uploading.value = false
  }
}

/** —— 删除：二次确认（取消不调接口），成功刷新列表 —— */

async function onRemove(row: StorageFileView) {
  try {
    await ElMessageBox.confirm(
      t('storage.removeOneConfirm', { name: row.name ?? '' }),
      t('storage.deleteTitle'),
      {
        type: 'warning',
      },
    )
  } catch {
    return
  }
  try {
    await removeOne.mutateAsync(row.id ?? 0)
    ElMessage.success(t('storage.removeSuccess'))
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : t('storage.removeFailed'))
  }
}

/** 展示用大小：字节人性化（B / KB / MB / GB） */
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

/** 展示用时间：ISO 的 T 换空格（仅显示层，筛选仍用原值） */
function displayTime(value: string | undefined): string {
  return value?.replace('T', ' ') ?? ''
}

/** —— 列定义（下载与图片预览随后续任务落位） —— */

const columns = computed<DataTableColumn<StorageFileView>[]>(() => [
  { accessorKey: 'name', header: t('storage.name') },
  {
    accessorKey: 'size',
    header: t('storage.size'),
    cell: ({ row }) => h('span', formatSize(row.original.size)),
  },
  { accessorKey: 'uploader', header: t('storage.uploader') },
  {
    accessorKey: 'createdAt',
    header: t('storage.createdAt'),
    cell: ({ row }) => h('span', displayTime(row.original.createdAt)),
  },
  {
    id: 'actions',
    header: t('storage.actions'),
    cell: ({ row }) =>
      hasAuth('storage:del')
        ? h(
            ElButton,
            {
              link: true,
              type: 'danger',
              'data-test': 'storage-remove',
              onClick: () => onRemove(row.original),
            },
            () => t('storage.delete'),
          )
        : null,
  },
])

const rows = computed(() => data.value?.content ?? [])
const total = computed(() => data.value?.total ?? 0)
</script>

<template>
  <div class="storage" data-test="storage-page">
    <div class="storage__filter">
      <el-input
        v-model="filter.name"
        :placeholder="t('storage.filterName')"
        clearable
        data-test="storage-filter-name"
        @change="applyFilters"
        @clear="applyFilters"
      />
      <el-input
        v-model="filter.uploader"
        :placeholder="t('storage.filterUploader')"
        clearable
        data-test="storage-filter-uploader"
        @change="applyFilters"
        @clear="applyFilters"
      />
      <el-date-picker
        v-model="filter.range"
        type="daterange"
        value-format="YYYY-MM-DD"
        :start-placeholder="t('storage.startDate')"
        :end-placeholder="t('storage.endDate')"
        data-test="storage-filter-range"
        @change="applyFilters"
      />
      <el-button data-test="storage-reset" @click="onReset">
        {{ t('storage.reset') }}
      </el-button>
      <el-upload
        v-if="hasAuth('storage:upload')"
        :auto-upload="false"
        :show-file-list="false"
        :on-change="onFileChange"
        class="storage__upload"
        data-test="storage-upload-picker"
      >
        <el-button type="primary" :loading="uploading" data-test="storage-upload">
          {{ t('storage.upload') }}
        </el-button>
      </el-upload>
    </div>

    <DataTable
      :columns="columns"
      :data="rows"
      :loading="isLoading"
      :page="page"
      :page-size="pageSize"
      :total="total"
      row-key="id"
      data-test="storage-table"
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
.storage__filter {
  display: flex;
  flex-wrap: wrap;
  gap: 12px;
  align-items: center;
  margin-bottom: 12px;

  .el-input {
    width: 180px;
  }
}

.storage__upload {
  margin-left: auto;
}
</style>
