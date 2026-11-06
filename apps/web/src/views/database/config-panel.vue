<script setup lang="ts">
import { ElButton, ElMessage, ElMessageBox } from 'element-plus'
import { computed, h, ref } from 'vue'
import { useI18n } from 'vue-i18n'
import { useTableTexts } from '@/composables/use-table-texts'
import { formatDateTime } from '@/utils/datetime'
import { z } from 'zod'

import { AutoForm, DataTable } from '@eladmin-plus/admin-ui'
import type { AutoFormField, AutoFormModel, DataTableColumn } from '@eladmin-plus/admin-ui'

import type { DatabaseConfigView } from '@/api/generated'
import {
  useDatabaseBackup,
  useDatabaseConfigRemove,
  useDatabaseConfigSave,
  useDatabaseConfigs,
} from '@/api/database'
import { useAuth } from '@/composables/use-auth'

/** 连接配置区块：全量列表（密码列不展示）+ 新增 / 编辑弹窗 + 删除确认 */
defineOptions({ name: 'DatabaseConfigPanel' })

const { t } = useI18n()
const tableTexts = useTableTexts()
const { hasAuth } = useAuth()

const { data, isLoading } = useDatabaseConfigs()
const saveConfig = useDatabaseConfigSave()
const removeConfig = useDatabaseConfigRemove()

function emptyModel(): AutoFormModel {
  return { id: null, host: '', port: 3306, name: '', userName: '', password: '', remark: '' }
}

const dialogVisible = ref(false)
const submitting = ref(false)
const model = ref<AutoFormModel>(emptyModel())

/** 编辑态带 id：密码留空即保持原值（必填语义随形态切换，占位文案明示） */
const editing = computed(() => Boolean(model.value.id))

const schema = computed(() =>
  z.object({
    // id 声明进 schema 才不会被 parse strip：编辑带 id 走 PUT，新增 null 走 POST
    id: z.coerce.number().nullable().optional(),
    host: z.string().trim().min(1),
    port: z.coerce.number().int().min(1).max(65535),
    name: z.string().trim().min(1),
    userName: z.string().trim().min(1),
    password: editing.value ? z.string().optional() : z.string().min(1),
    remark: z.string().trim().optional(),
  }),
)

const fields = computed<AutoFormField[]>(() => [
  { name: 'host', label: t('database.host'), type: 'input' },
  { name: 'port', label: t('database.port'), type: 'input', props: { type: 'number' } },
  { name: 'name', label: t('database.name'), type: 'input' },
  { name: 'userName', label: t('database.userName'), type: 'input' },
  {
    name: 'password',
    label: t('database.password'),
    type: 'input',
    props: { type: 'password', showPassword: true },
    placeholder: editing.value ? t('database.passwordKeepHint') : undefined,
  },
  {
    name: 'remark',
    label: t('database.remark'),
    type: 'input',
    placeholder: t('database.remarkPlaceholder'),
  },
])

function openCreate() {
  model.value = emptyModel()
  dialogVisible.value = true
}

function openEdit(row: DatabaseConfigView) {
  // 密码不回显（后端契约），编辑态留空提交即保持原值
  model.value = {
    id: row.id ?? null,
    host: row.host ?? '',
    port: row.port ?? 3306,
    name: row.name ?? '',
    userName: row.userName ?? '',
    password: '',
    remark: row.remark ?? '',
  }
  dialogVisible.value = true
}

async function onSubmit(submitted: unknown) {
  submitting.value = true
  try {
    await saveConfig.mutateAsync(submitted as never)
    ElMessage.success(t('database.saveSuccess'))
    dialogVisible.value = false
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : t('database.saveFailed'))
  } finally {
    submitting.value = false
  }
}

/** 删除：二次确认（取消不调接口），成功刷新列表 */
async function onRemove(row: DatabaseConfigView) {
  try {
    await ElMessageBox.confirm(
      t('database.removeOneConfirm', { name: row.name ?? '' }),
      t('database.deleteTitle'),
      { type: 'warning' },
    )
  } catch {
    return
  }
  try {
    await removeConfig.mutateAsync(row.id ?? 0)
    ElMessage.success(t('database.removeSuccess'))
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : t('database.removeFailed'))
  }
}

const rows = computed(() => data.value ?? [])

/** —— 备份触发：行操作确认后同步请求（mysqldump 真实执行数秒），loading 防重复提交 —— */

const backup = useDatabaseBackup()
const backingUpId = ref<number | null>(null)
/** 备份失败页内提示（含后端原因文案），不整页报错；下一次动作即清 */
const backupError = ref('')

async function onBackup(row: DatabaseConfigView) {
  if (backingUpId.value !== null) {
    return
  }
  try {
    await ElMessageBox.confirm(
      t('database.backupConfirm', { name: row.name ?? '' }),
      t('database.backupTitle'),
      { type: 'info' },
    )
  } catch {
    return
  }
  backupError.value = ''
  backingUpId.value = row.id ?? 0
  try {
    const record = await backup.mutateAsync(row.id ?? 0)
    ElMessage.success(t('database.backupSuccess', { name: record.fileName ?? '' }))
  } catch (error) {
    backupError.value = error instanceof Error ? error.message : t('database.backupFailed')
  } finally {
    backingUpId.value = null
  }
}

const columns = computed<DataTableColumn<DatabaseConfigView>[]>(() => [
  { accessorKey: 'host', header: t('database.host') },
  { accessorKey: 'port', header: t('database.port') },
  { accessorKey: 'name', header: t('database.name') },
  { accessorKey: 'userName', header: t('database.userName') },
  { accessorKey: 'remark', header: t('database.remark') },
  {
    accessorKey: 'createdAt',
    header: t('database.createdAt'),
    cell: ({ row }) => h('span', formatDateTime(row.original.createdAt)),
  },
  {
    id: 'actions',
    header: t('database.actions'),
    cell: ({ row }) =>
      h('span', { class: 'database-config__row-actions' }, [
        hasAuth('database:backup')
          ? h(
              ElButton,
              {
                link: true,
                type: 'success',
                'data-test': 'database-config-backup',
                loading: backingUpId.value === (row.original.id ?? 0),
                onClick: () => onBackup(row.original),
              },
              () => t('database.backup'),
            )
          : null,
        hasAuth('database:edit')
          ? h(
              ElButton,
              {
                link: true,
                type: 'primary',
                'data-test': 'database-config-edit',
                onClick: () => openEdit(row.original),
              },
              () => t('database.edit'),
            )
          : null,
        hasAuth('database:del')
          ? h(
              ElButton,
              {
                link: true,
                type: 'danger',
                'data-test': 'database-config-delete',
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
  <div class="database-config" data-test="database-config-panel">
    <div class="database-config__toolbar">
      <el-button
        v-if="hasAuth('database:add')"
        type="primary"
        plain
        data-test="database-config-create"
        @click="openCreate"
      >
        {{ t('database.create') }}
      </el-button>
    </div>

    <DataTable
      :texts="tableTexts"
      :columns="columns"
      :data="rows"
      :loading="isLoading"
      row-key="id"
      data-test="database-config-table"
    />

    <!-- 备份失败页内提示：后端 5xx 原因文案权威，直显不整页报错 -->
    <el-alert
      v-if="backupError"
      :title="backupError"
      type="error"
      show-icon
      closable
      class="database-config__backup-error"
      data-test="database-backup-error"
      @close="backupError = ''"
    />

    <el-dialog
      v-model="dialogVisible"
      :title="editing ? t('database.edit') : t('database.create')"
      width="520px"
      destroy-on-close
      data-test="database-config-dialog"
    >
      <AutoForm
        v-model="model"
        :schema="schema"
        :fields="fields"
        :submitting="submitting"
        :submit-text="t('database.submit')"
        @submit="onSubmit"
      />
    </el-dialog>
  </div>
</template>

<style scoped lang="scss">
.database-config__backup-error {
  margin-top: 12px;
}

.database-config__toolbar {
  display: flex;
  justify-content: flex-end;
  margin-bottom: 12px;
}

.database-config__row-actions {
  display: inline-flex;
  gap: 4px;
}
</style>
