<script setup lang="ts">
import { ElButton, ElDialog, ElInput, ElInputNumber, ElMessage, ElMessageBox } from 'element-plus'
import { computed, h, ref } from 'vue'
import { useI18n } from 'vue-i18n'

import {
  useDictDetailRemove,
  useDictDetailSave,
  useDictDetails,
  useDictList,
  useDictRemove,
  useDictSave,
} from '@/api/dicts'
import type { Dict, DictDetail } from '@/api/generated'
import { DataTable } from '@eladmin-plus/admin-ui'
import type { DataTableColumn } from '@eladmin-plus/admin-ui'
import { useAuth } from '@/composables/use-auth'
import { FALLBACK_LOCALE } from '@/i18n'
import LabelEditor from './label-editor.vue'
import { buildLabelJson, parseLabelJson } from './label-json'

defineOptions({ name: 'SystemDicts' })

const { t, locale } = useI18n()
const { hasAuth } = useAuth()

const { data: dicts, isLoading } = useDictList()
const saveDict = useDictSave()
const removeDict = useDictRemove()

/** —— 字典级：名称 + 描述两级列表的母表 —— */

const dialogVisible = ref(false)
const submitting = ref(false)
const model = ref({ id: null as number | null, name: '', description: '' })

function openCreate() {
  model.value = { id: null, name: '', description: '' }
  dialogVisible.value = true
}

function openEdit(row: Dict) {
  model.value = {
    id: row.id ?? null,
    name: row.name ?? '',
    description: row.description ?? '',
  }
  dialogVisible.value = true
}

async function onSubmit() {
  if (!model.value.name.trim()) {
    ElMessage.warning(t('dicts.nameRequired'))
    return
  }
  submitting.value = true
  try {
    await saveDict.mutateAsync({
      id: model.value.id ?? undefined,
      name: model.value.name.trim(),
      description: model.value.description.trim() || undefined,
    })
    ElMessage.success(t('dicts.saveSuccess'))
    dialogVisible.value = false
  } catch (error) {
    // 字典名唯一冲突 409 由后端权威拒绝，toast 直显
    ElMessage.error(error instanceof Error ? error.message : t('dicts.saveFailed'))
  } finally {
    submitting.value = false
  }
}

async function onRemove(row: Dict) {
  try {
    await ElMessageBox.confirm(
      t('dicts.deleteConfirm', { name: row.name ?? '' }),
      t('dicts.deleteTitle'),
      { type: 'warning' },
    )
  } catch {
    return
  }
  try {
    await removeDict.mutateAsync(row.id ?? 0)
    ElMessage.success(t('dicts.deleteSuccess'))
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : t('dicts.deleteFailed'))
  }
}

/** —— 明细级：对话框二级列表（label 多语言、value、排序） —— */

const detailDict = ref<Dict | null>(null)
const detailDictId = computed(() => detailDict.value?.id ?? null)
const { data: details, isLoading: detailsLoading } = useDictDetails(detailDictId)
const saveDetail = useDictDetailSave(detailDictId)
const removeDetail = useDictDetailRemove(detailDictId)

const detailDialogVisible = computed({
  get: () => detailDict.value !== null,
  set: (visible: boolean) => {
    if (!visible) {
      detailDict.value = null
    }
  },
})

const detailFormVisible = ref(false)
const detailSubmitting = ref(false)
const detailModel = ref<{
  id: number | null
  value: string
  sort: number
  rows: Record<string, string>
}>({ id: null, value: '', sort: 0, rows: {} })

function openDetailCreate() {
  detailModel.value = { id: null, value: '', sort: 0, rows: {} }
  detailFormVisible.value = true
}

function openDetailEdit(row: DictDetail) {
  detailModel.value = {
    id: row.id ?? null,
    value: row.value ?? '',
    sort: row.sort ?? 0,
    rows: parseLabelJson(row.label),
  }
  detailFormVisible.value = true
}

async function onDetailSubmit() {
  if (!detailModel.value.value.trim()) {
    ElMessage.warning(t('dicts.valueRequired'))
    return
  }
  detailSubmitting.value = true
  try {
    await saveDetail.mutateAsync({
      id: detailModel.value.id ?? undefined,
      // 组装保证合法 JSON：空语言行不写入
      label: buildLabelJson(detailModel.value.rows),
      value: detailModel.value.value.trim(),
      sort: detailModel.value.sort,
    })
    ElMessage.success(t('dicts.saveSuccess'))
    detailFormVisible.value = false
  } catch (error) {
    // 同字典内 value 唯一冲突 409、label 非法 JSON 422 由后端权威拒绝，toast 直显
    ElMessage.error(error instanceof Error ? error.message : t('dicts.saveFailed'))
  } finally {
    detailSubmitting.value = false
  }
}

async function onDetailRemove(row: DictDetail) {
  try {
    await ElMessageBox.confirm(
      t('dicts.detailDeleteConfirm', { value: row.value ?? '' }),
      t('dicts.deleteTitle'),
      { type: 'warning' },
    )
  } catch {
    return
  }
  try {
    await removeDetail.mutateAsync(row.id ?? 0)
    ElMessage.success(t('dicts.deleteSuccess'))
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : t('dicts.deleteFailed'))
  }
}

/** label 展示取值链：label[当前 locale] → label[zh-CN] → 对象任一值 */
function detailLabel(row: DictDetail): string {
  const parsed = parseLabelJson(row.label)
  const current = locale.value
  return parsed[current] ?? parsed[FALLBACK_LOCALE] ?? Object.values(parsed)[0] ?? ''
}

const columns = computed<DataTableColumn<Dict>[]>(() => [
  { accessorKey: 'name', header: t('dicts.name') },
  { accessorKey: 'description', header: t('dicts.description') },
  {
    id: 'actions',
    header: t('dicts.actions'),
    cell: ({ row }) =>
      h('span', { class: 'dicts__actions' }, [
        hasAuth('system:dict:list')
          ? h(
              ElButton,
              {
                link: true,
                type: 'primary',
                'data-test': 'dicts-details',
                onClick: () => openDetails(row.original),
              },
              () => t('dicts.manageDetails'),
            )
          : null,
        hasAuth('system:dict:edit')
          ? h(
              ElButton,
              {
                link: true,
                type: 'primary',
                'data-test': 'dicts-edit',
                onClick: () => openEdit(row.original),
              },
              () => t('dicts.edit'),
            )
          : null,
        hasAuth('system:dict:del')
          ? h(
              ElButton,
              {
                link: true,
                type: 'danger',
                'data-test': 'dicts-delete',
                onClick: () => onRemove(row.original),
              },
              () => t('dicts.delete'),
            )
          : null,
      ]),
  },
])

const detailColumns = computed<DataTableColumn<DictDetail>[]>(() => [
  {
    accessorKey: 'label',
    header: t('dicts.label'),
    cell: ({ row }) => h('span', detailLabel(row.original)),
  },
  { accessorKey: 'value', header: t('dicts.value') },
  { accessorKey: 'sort', header: t('dicts.sort') },
  {
    id: 'actions',
    header: t('dicts.actions'),
    cell: ({ row }) =>
      h('span', { class: 'dicts__actions' }, [
        hasAuth('system:dict:edit')
          ? h(
              ElButton,
              {
                link: true,
                type: 'primary',
                'data-test': 'dicts-detail-edit',
                onClick: () => openDetailEdit(row.original),
              },
              () => t('dicts.edit'),
            )
          : null,
        hasAuth('system:dict:del')
          ? h(
              ElButton,
              {
                link: true,
                type: 'danger',
                'data-test': 'dicts-detail-delete',
                onClick: () => onDetailRemove(row.original),
              },
              () => t('dicts.delete'),
            )
          : null,
      ]),
  },
])

const rows = computed(() => dicts.value ?? [])
const detailRows = computed(() => details.value ?? [])

function openDetails(row: Dict) {
  detailDict.value = row
}
</script>

<template>
  <div class="dicts" data-test="dicts-page">
    <div class="dicts__toolbar">
      <el-button
        v-if="hasAuth('system:dict:add')"
        type="primary"
        data-test="dicts-create"
        @click="openCreate"
      >
        {{ t('dicts.create') }}
      </el-button>
    </div>

    <DataTable :columns="columns" :data="rows" :loading="isLoading" data-test="dicts-table" />

    <el-dialog
      v-model="dialogVisible"
      :title="model.id ? t('dicts.edit') : t('dicts.create')"
      width="520px"
      destroy-on-close
      data-test="dicts-dialog"
    >
      <el-form label-width="80px">
        <el-form-item :label="t('dicts.name')" data-field="name">
          <el-input v-model="model.name" data-test="dicts-name" />
        </el-form-item>
        <el-form-item :label="t('dicts.description')" data-field="description">
          <el-input v-model="model.description" data-test="dicts-description" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button data-test="dicts-cancel" @click="dialogVisible = false">
          {{ t('dicts.cancel') }}
        </el-button>
        <el-button type="primary" :loading="submitting" data-test="dicts-submit" @click="onSubmit">
          {{ t('dicts.submit') }}
        </el-button>
      </template>
    </el-dialog>

    <el-dialog
      v-model="detailDialogVisible"
      :title="t('dicts.detailTitle', { name: detailDict?.name ?? '' })"
      width="640px"
      destroy-on-close
      data-test="dicts-detail-dialog"
    >
      <div class="dicts__detail-toolbar">
        <el-button
          v-if="hasAuth('system:dict:add')"
          type="primary"
          plain
          size="small"
          data-test="dicts-detail-create"
          @click="openDetailCreate"
        >
          {{ t('dicts.createDetail') }}
        </el-button>
      </div>
      <DataTable
        :columns="detailColumns"
        :data="detailRows"
        :loading="detailsLoading"
        data-test="dicts-detail-table"
      />
      <template #footer>
        <el-button data-test="dicts-detail-close" @click="detailDialogVisible = false">
          {{ t('dicts.close') }}
        </el-button>
      </template>
    </el-dialog>

    <el-dialog
      v-model="detailFormVisible"
      :title="detailModel.id ? t('dicts.editDetail') : t('dicts.createDetail')"
      width="520px"
      destroy-on-close
      append-to-body
      data-test="dicts-detail-form"
    >
      <el-form label-width="80px">
        <el-form-item :label="t('dicts.label')" data-field="label">
          <LabelEditor v-model="detailModel.rows" />
        </el-form-item>
        <el-form-item :label="t('dicts.value')" data-field="value">
          <el-input v-model="detailModel.value" data-test="dicts-value" />
        </el-form-item>
        <el-form-item :label="t('dicts.sort')" data-field="sort">
          <el-input-number v-model="detailModel.sort" data-test="dicts-sort" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button data-test="dicts-detail-cancel" @click="detailFormVisible = false">
          {{ t('dicts.cancel') }}
        </el-button>
        <el-button
          type="primary"
          :loading="detailSubmitting"
          data-test="dicts-detail-submit"
          @click="onDetailSubmit"
        >
          {{ t('dicts.submit') }}
        </el-button>
      </template>
    </el-dialog>
  </div>
</template>

<style scoped lang="scss">
.dicts__toolbar {
  display: flex;
  gap: 12px;
  align-items: center;
  margin-bottom: 12px;
}

.dicts__detail-toolbar {
  display: flex;
  justify-content: flex-end;
  margin-bottom: 8px;
}

:deep(.dicts__actions .el-button + .el-button) {
  margin-left: 0;
}

.dicts__actions {
  display: inline-flex;
  gap: 8px;
}
</style>
