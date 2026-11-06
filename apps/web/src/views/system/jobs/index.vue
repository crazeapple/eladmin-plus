<script setup lang="ts">
import { ElButton, ElMessage, ElMessageBox, ElTag } from 'element-plus'
import { h, computed, reactive, ref } from 'vue'
import { useI18n } from 'vue-i18n'
import { useTableTexts } from '@/composables/use-table-texts'
import { z } from 'zod'

import { AutoForm, DataTable } from '@eladmin-plus/admin-ui'
import type { AutoFormField, AutoFormModel, DataTableColumn } from '@eladmin-plus/admin-ui'

import { request } from '@/api/request'
import { useAuth } from '@/composables/use-auth'
import { useJobPage, useJobRemove, useJobSave } from '@/api/jobs'
import type { JobView, PageResultJobView } from '@/api/generated'

defineOptions({ name: 'SystemJobs' })

const { t } = useI18n()
const tableTexts = useTableTexts()
const { hasAuth } = useAuth()

/** 查询与筛选：筛选变化经「查询」应用并重置 page 0 */
const page = ref(0)
const pageSize = ref(20)
const filter = reactive<{ name: string; enabled: number | undefined }>({
  name: '',
  enabled: undefined,
})
const applied = reactive<{ name?: string; enabled?: number }>({})

const query = computed(() => ({
  page: page.value,
  size: pageSize.value,
  name: applied.name,
  enabled: applied.enabled,
}))

const { data, isLoading } = useJobPage(query)
const saveJob = useJobSave()
const removeJob = useJobRemove()

function onSearch() {
  applied.name = filter.name.trim() || undefined
  applied.enabled = filter.enabled
  page.value = 0
}

function onReset() {
  filter.name = ''
  filter.enabled = undefined
  applied.name = undefined
  applied.enabled = undefined
  page.value = 0
}

/** 编辑表单语义（nameEn 空提交即清除英文名，字段提示已注明） */
const jobSchema = z.object({
  // id 声明进 schema 才不会被 parse strip：编辑带 id 走 PUT，新增 null 走 POST
  id: z.coerce.number().nullable().optional(),
  name: z.string().trim().min(1),
  nameEn: z.string().trim().optional(),
  sort: z.coerce.number().int(),
  enabled: z.union([z.literal(1), z.literal(0)]),
})

const dialogVisible = ref(false)
const submitting = ref(false)
const model = ref<AutoFormModel>({ id: null, name: '', nameEn: '', sort: 0, enabled: 1 })

const fields = computed<AutoFormField[]>(() => [
  { name: 'name', label: t('jobs.name'), type: 'input' },
  { name: 'nameEn', label: t('jobs.nameEn'), type: 'input', placeholder: t('jobs.nameEnHint') },
  { name: 'sort', label: t('jobs.sort'), type: 'input', props: { type: 'number' } },
  {
    name: 'enabled',
    label: t('jobs.status'),
    type: 'radio',
    options: [
      { label: t('jobs.enabled'), value: 1 },
      { label: t('jobs.disabled'), value: 0 },
    ],
  },
])

function openCreate() {
  model.value = { id: null, name: '', nameEn: '', sort: 0, enabled: 1 }
  dialogVisible.value = true
}

function openEdit(row: JobView) {
  model.value = {
    id: row.id,
    name: row.name,
    nameEn: '',
    sort: row.sort,
    enabled: row.enabled,
  }
  dialogVisible.value = true
  void fetchEnName(row)
}

/** 编辑回填英文名（与默认名一致即无覆盖，留空提交无害） */
async function fetchEnName(row: JobView) {
  const qs = new URLSearchParams({ page: '0', size: '1', name: row.name ?? '' })
  const enPage = await requestEnPage(qs)
  const hit = (enPage.content ?? []).find((job) => job.id === row.id)
  if (hit?.name && hit.name !== row.name) {
    model.value.nameEn = hit.name
  }
}

async function requestEnPage(qs: URLSearchParams): Promise<PageResultJobView> {
  return request<PageResultJobView>({
    url: `/jobs?${qs.toString()}`,
    headers: { 'Accept-Language': 'en' },
  })
}

async function onSubmit(submitted: unknown) {
  submitting.value = true
  try {
    await saveJob.mutateAsync(submitted as never)
    ElMessage.success(t('jobs.saveSuccess'))
    dialogVisible.value = false
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : t('jobs.saveFailed'))
  } finally {
    submitting.value = false
  }
}

async function onRemove(row: JobView) {
  try {
    await ElMessageBox.confirm(t('jobs.deleteConfirm', { name: row.name }), t('jobs.deleteTitle'), {
      type: 'warning',
    })
  } catch {
    return
  }
  try {
    await removeJob.mutateAsync(row.id ?? 0)
    ElMessage.success(t('jobs.deleteSuccess'))
  } catch (error) {
    // 后端保护（岗位下有用户）消息权威，toast 直显
    ElMessage.error(error instanceof Error ? error.message : t('jobs.deleteFailed'))
  }
}

const columns = computed<DataTableColumn<JobView>[]>(() => [
  { accessorKey: 'name', header: t('jobs.name') },
  { accessorKey: 'sort', header: t('jobs.sort') },
  {
    accessorKey: 'enabled',
    header: t('jobs.status'),
    cell: ({ row }) =>
      h(
        ElTag,
        { type: row.original.enabled === 1 ? 'success' : 'info', 'data-test': 'jobs-status' },
        () => (row.original.enabled === 1 ? t('jobs.enabled') : t('jobs.disabled')),
      ),
  },
  {
    id: 'actions',
    header: t('jobs.actions'),
    cell: ({ row }) =>
      h('span', { class: 'jobs__actions' }, [
        hasAuth('system:job:edit')
          ? h(
              ElButton,
              {
                link: true,
                type: 'primary',
                'data-test': 'jobs-edit',
                onClick: () => openEdit(row.original),
              },
              () => t('jobs.edit'),
            )
          : null,
        hasAuth('system:job:del')
          ? h(
              ElButton,
              {
                link: true,
                type: 'danger',
                'data-test': 'jobs-delete',
                onClick: () => onRemove(row.original),
              },
              () => t('jobs.delete'),
            )
          : null,
      ]),
  },
])

const rows = computed(() => data.value?.content ?? [])
const total = computed(() => data.value?.total ?? 0)
</script>

<template>
  <div class="jobs" data-test="jobs-page">
    <div class="jobs__filter">
      <el-input
        v-model="filter.name"
        :placeholder="t('jobs.filterName')"
        clearable
        data-test="jobs-filter-name"
        @keyup.enter="onSearch"
      />
      <el-select
        v-model="filter.enabled"
        :placeholder="t('jobs.filterStatus')"
        clearable
        data-test="jobs-filter-status"
      >
        <el-option :label="t('jobs.enabled')" :value="1" />
        <el-option :label="t('jobs.disabled')" :value="0" />
      </el-select>
      <el-button type="primary" data-test="jobs-search" @click="onSearch">
        {{ t('jobs.search') }}
      </el-button>
      <el-button data-test="jobs-reset" @click="onReset">{{ t('jobs.reset') }}</el-button>
      <el-button
        v-if="hasAuth('system:job:add')"
        type="primary"
        plain
        data-test="jobs-create"
        @click="openCreate"
      >
        {{ t('jobs.create') }}
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
      data-test="jobs-table"
      @page-change="
        ({ page: p, pageSize: s }) => {
          page = p
          pageSize = s
        }
      "
    />

    <el-dialog
      v-model="dialogVisible"
      :title="model.id ? t('jobs.edit') : t('jobs.create')"
      width="520px"
      destroy-on-close
      data-test="jobs-dialog"
    >
      <AutoForm
        v-model="model"
        :schema="jobSchema"
        :fields="fields"
        :submitting="submitting"
        :submit-text="t('jobs.submit')"
        @submit="onSubmit"
      />
    </el-dialog>
  </div>
</template>

<style scoped lang="scss">
.jobs__filter {
  display: flex;
  gap: 12px;
  align-items: center;
  margin-bottom: 12px;

  .el-input {
    width: 200px;
  }

  .el-select {
    width: 140px;
  }
}
</style>
