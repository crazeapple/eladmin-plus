<script setup lang="ts">
import { ElButton, ElMessage, ElMessageBox, ElTag, ElTreeSelect } from 'element-plus'
import { computed, h, reactive, ref } from 'vue'
import { useI18n } from 'vue-i18n'
import { z } from 'zod'

import { AutoForm, DataTable } from '@eladmin-plus/admin-ui'
import type { AutoFormField, AutoFormModel, DataTableColumn } from '@eladmin-plus/admin-ui'

import { useAuth } from '@/composables/use-auth'
import { useDeptTree } from '@/api/depts'
import { fetchRolePageEn, useRolePage, useRoleRemove, useRoleSave } from '@/api/roles'
import type { RoleView } from '@/api/generated'

defineOptions({ name: 'SystemRoles' })

const { t } = useI18n()
const { hasAuth } = useAuth()

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

const { data, isLoading } = useRolePage(query)
const saveRole = useRoleSave()
const removeRole = useRoleRemove()

const { data: deptTree } = useDeptTree()

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

/** 表单语义：code 与 level 编辑内置角色时由后端权威拒绝（前端禁用作为 UX 首道） */
const roleSchema = z.object({
  // id 声明进 schema 才不会被 parse strip：编辑带 id 走 PUT，新增 null 走 POST
  id: z.coerce.number().nullable().optional(),
  code: z.string().trim().min(1),
  name: z.string().trim().min(1),
  nameEn: z.string().trim().optional(),
  description: z.string().trim().optional(),
  level: z.coerce.number().int(),
  dataScope: z.enum(['ALL', 'DEPT', 'CUSTOM']),
  customDeptIds: z.array(z.coerce.number()).optional(),
  enabled: z.union([z.literal(1), z.literal(0)]),
})

const dialogVisible = ref(false)
const submitting = ref(false)
const model = ref<AutoFormModel>({
  id: null,
  code: '',
  name: '',
  nameEn: '',
  description: '',
  level: 10,
  dataScope: 'ALL',
  customDeptIds: [],
  enabled: 1,
})

const editingBuiltIn = computed(() => Boolean(model.value.id && model.value.builtIn))

const fields = computed<AutoFormField[]>(() => [
  {
    name: 'code',
    label: t('roles.code'),
    type: 'input',
    props: { disabled: editingBuiltIn.value },
  },
  { name: 'name', label: t('roles.name'), type: 'input' },
  { name: 'nameEn', label: t('roles.nameEn'), type: 'input', placeholder: t('roles.nameEnHint') },
  { name: 'description', label: t('roles.description'), type: 'input' },
  {
    name: 'level',
    label: t('roles.level'),
    type: 'input',
    props: { type: 'number', disabled: editingBuiltIn.value },
  },
  {
    name: 'dataScope',
    label: t('roles.dataScope'),
    type: 'radio',
    options: [
      { label: t('roles.scopeAll'), value: 'ALL' },
      { label: t('roles.scopeDept'), value: 'DEPT' },
      { label: t('roles.scopeCustom'), value: 'CUSTOM' },
    ],
  },
  { name: 'customDeptIds', label: t('roles.customDepts'), type: 'tree-select' },
  {
    name: 'enabled',
    label: t('roles.status'),
    type: 'radio',
    options: [
      { label: t('roles.enabled'), value: 1 },
      { label: t('roles.disabled'), value: 0 },
    ],
  },
])

/** 自定义部门集合的收窄模型（AutoFormModel 值为 unknown，v-model 需确定类型） */
const customDeptIdsModel = computed<number[]>({
  get: () => (model.value.customDeptIds as number[] | undefined) ?? [],
  set: (value: number[]) => {
    model.value.customDeptIds = value
  },
})

/** 编辑回填英文名（与默认名一致即无覆盖，留空提交无害） */
async function openEdit(row: RoleView) {
  model.value = {
    id: row.id,
    code: row.code,
    name: row.name,
    nameEn: '',
    description: row.description,
    level: row.level,
    dataScope: (row.dataScope as 'ALL' | 'DEPT' | 'CUSTOM') ?? 'ALL',
    customDeptIds: row.customDeptIds ?? [],
    enabled: row.enabled,
    builtIn: row.builtIn,
  }
  dialogVisible.value = true
  const enRoles = await fetchRolePageEn()
  const hit = enRoles.find((role) => role.id === row.id)
  if (hit?.name && hit.name !== row.name) {
    model.value.nameEn = hit.name
  }
}

function openCreate() {
  model.value = {
    id: null,
    code: '',
    name: '',
    nameEn: '',
    description: '',
    level: 10,
    dataScope: 'ALL',
    customDeptIds: [],
    enabled: 1,
    builtIn: false,
  }
  dialogVisible.value = true
}

async function onSubmit(submitted: unknown) {
  submitting.value = true
  try {
    await saveRole.mutateAsync(submitted as never)
    ElMessage.success(t('roles.saveSuccess'))
    dialogVisible.value = false
  } catch (error) {
    // 越权约束（高于自身级别不可操作）、内置保护、重名由后端权威拒绝，toast 直显
    ElMessage.error(error instanceof Error ? error.message : t('roles.saveFailed'))
  } finally {
    submitting.value = false
  }
}

async function onRemove(row: RoleView) {
  try {
    await ElMessageBox.confirm(
      t('roles.deleteConfirm', { name: row.name }),
      t('roles.deleteTitle'),
      {
        type: 'warning',
      },
    )
  } catch {
    return
  }
  try {
    await removeRole.mutateAsync(row.id ?? 0)
    ElMessage.success(t('roles.deleteSuccess'))
  } catch (error) {
    // 内置角色不可删、有用户关联不能删由后端权威拒绝，toast 直显
    ElMessage.error(error instanceof Error ? error.message : t('roles.deleteFailed'))
  }
}

const DATA_SCOPE_LABEL_KEYS: Record<string, string> = {
  ALL: 'roles.scopeAll',
  DEPT: 'roles.scopeDept',
  CUSTOM: 'roles.scopeCustom',
}

const columns = computed<DataTableColumn<RoleView>[]>(() => [
  { accessorKey: 'code', header: t('roles.code') },
  { accessorKey: 'name', header: t('roles.name') },
  { accessorKey: 'level', header: t('roles.level') },
  {
    accessorKey: 'dataScope',
    header: t('roles.dataScope'),
    cell: ({ row }) => {
      const labelKey = DATA_SCOPE_LABEL_KEYS[row.original.dataScope ?? 'ALL']
      return h('span', labelKey ? t(labelKey) : '')
    },
  },
  {
    accessorKey: 'enabled',
    header: t('roles.status'),
    cell: ({ row }) =>
      h(ElTag, { type: row.original.enabled === 1 ? 'success' : 'info' }, () =>
        row.original.enabled === 1 ? t('roles.enabled') : t('roles.disabled'),
      ),
  },
  {
    id: 'actions',
    header: t('roles.actions'),
    cell: ({ row }) =>
      h('span', { class: 'roles__actions' }, [
        hasAuth('system:role:edit')
          ? h(
              ElButton,
              {
                link: true,
                type: 'primary',
                'data-test': 'roles-edit',
                onClick: () => openEdit(row.original),
              },
              () => t('roles.edit'),
            )
          : null,
        hasAuth('system:role:del')
          ? h(
              ElButton,
              {
                link: true,
                type: 'danger',
                'data-test': 'roles-delete',
                onClick: () => onRemove(row.original),
              },
              () => t('roles.delete'),
            )
          : null,
      ]),
  },
])

const rows = computed(() => data.value?.content ?? [])
const total = computed(() => data.value?.total ?? 0)
</script>

<template>
  <div class="roles" data-test="roles-page">
    <div class="roles__filter">
      <el-input
        v-model="filter.name"
        :placeholder="t('roles.filterName')"
        clearable
        data-test="roles-filter-name"
        @keyup.enter="onSearch"
      />
      <el-select
        v-model="filter.enabled"
        :placeholder="t('roles.filterStatus')"
        clearable
        data-test="roles-filter-status"
      >
        <el-option :label="t('roles.enabled')" :value="1" />
        <el-option :label="t('roles.disabled')" :value="0" />
      </el-select>
      <el-button type="primary" data-test="roles-search" @click="onSearch">
        {{ t('roles.search') }}
      </el-button>
      <el-button data-test="roles-reset" @click="onReset">{{ t('roles.reset') }}</el-button>
      <el-button
        v-if="hasAuth('system:role:add')"
        type="primary"
        plain
        data-test="roles-create"
        @click="openCreate"
      >
        {{ t('roles.create') }}
      </el-button>
    </div>

    <DataTable
      :columns="columns"
      :data="rows"
      :loading="isLoading"
      :page="page"
      :page-size="pageSize"
      :total="total"
      data-test="roles-table"
      @page-change="
        ({ page: p, pageSize: s }) => {
          page = p
          pageSize = s
        }
      "
    />

    <el-dialog
      v-model="dialogVisible"
      :title="model.id ? t('roles.edit') : t('roles.create')"
      width="640px"
      destroy-on-close
      data-test="roles-dialog"
    >
      <AutoForm
        :schema="roleSchema"
        :fields="fields"
        v-model="model"
        :submitting="submitting"
        :submit-text="t('roles.submit')"
        @submit="onSubmit"
      >
        <!-- 数据范围联动：CUSTOM 显隐自定义部门树（模型在页面作用域，联动零 AutoForm 改造） -->
        <template #field-customDeptIds>
          <ElTreeSelect
            v-if="model.dataScope === 'CUSTOM'"
            v-model="customDeptIdsModel"
            :data="deptTree ?? []"
            node-key="id"
            multiple
            show-checkbox
            check-strictly
            :props="{ label: 'name', children: 'children' }"
            data-test="roles-custom-depts"
          />
        </template>
      </AutoForm>
    </el-dialog>
  </div>
</template>

<style scoped lang="scss">
.roles__filter {
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
