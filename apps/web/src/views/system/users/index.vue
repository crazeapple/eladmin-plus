<script setup lang="ts">
import { ElButton, ElMessage, ElMessageBox, ElSwitch, ElTag } from 'element-plus'
import type { FormInstance } from 'element-plus'
import { computed, h, reactive, ref } from 'vue'
import { useI18n } from 'vue-i18n'

import { DataTable } from '@eladmin-plus/admin-ui'
import type { DataTableColumn } from '@eladmin-plus/admin-ui'

import { useDeptTree } from '@/api/depts'
import { request } from '@/api/request'
import type { DeptTreeNode, PageResultJobView, PageResultRoleView, UserView } from '@/api/generated'
import {
  useUserEnabled,
  useUserPage,
  useUserRemove,
  useUserResetPassword,
  useUserSave,
} from '@/api/users'
import { GENDER_LABEL_KEYS, GENDER_OPTIONS } from '@/constants/gender'
import { useAuth } from '@/composables/use-auth'
import { useTableTexts } from '@/composables/use-table-texts'
import { formatDateTime } from '@/utils/datetime'
import { useMenusStore } from '@/stores/menus'
import { buildUserRules, type FormMode } from './rules'

defineOptions({ name: 'SystemUsers' })

const { t } = useI18n()
const { hasAuth } = useAuth()
const menusStore = useMenusStore()
const tableTexts = useTableTexts()

/** 查询与筛选：筛选经「查询」应用并重置 page 0；部门树点选即时过滤 */
const page = ref(0)
const pageSize = ref(20)
const filter = reactive<{
  name: string
  email: string
  enabled: number | undefined
  range: [string, string] | null
}>({ name: '', email: '', enabled: undefined, range: null })
const applied = reactive<{
  deptId?: number
  name?: string
  email?: string
  enabled?: number
  startTime?: string
  endTime?: string
}>({})

const query = computed(() => ({
  page: page.value,
  size: pageSize.value,
  deptId: applied.deptId,
  name: applied.name,
  email: applied.email,
  enabled: applied.enabled,
  startTime: applied.startTime,
  endTime: applied.endTime,
}))

const { data, isLoading } = useUserPage(query)
const setEnabled = useUserEnabled()
const saveUser = useUserSave()
const removeUser = useUserRemove()
const resetPassword = useUserResetPassword()

const { data: deptTree } = useDeptTree()

const currentDeptId = ref<number | null>(null)

function onDeptClick(node: DeptTreeNode) {
  // 再点当前节点取消过滤
  currentDeptId.value = currentDeptId.value === node.id ? null : (node.id ?? null)
  applied.deptId = currentDeptId.value ?? undefined
  page.value = 0
}

function onSearch() {
  applied.name = filter.name.trim() || undefined
  applied.email = filter.email.trim() || undefined
  applied.enabled = filter.enabled
  applied.startTime = filter.range ? `${filter.range[0]}T00:00:00` : undefined
  applied.endTime = filter.range ? `${filter.range[1]}T23:59:59` : undefined
  page.value = 0
}

function onReset() {
  filter.name = ''
  filter.email = ''
  filter.enabled = undefined
  filter.range = null
  applied.name = undefined
  applied.email = undefined
  applied.enabled = undefined
  applied.startTime = undefined
  applied.endTime = undefined
  currentDeptId.value = null
  applied.deptId = undefined
  page.value = 0
}

/** 自我保护藏显：当前登录用户与内置 admin 行不显示停用与删除（后端权威拒绝兜底） */
const currentUserId = computed(() => menusStore.user?.id ?? null)
function isProtectedRow(row: UserView): boolean {
  return row.id === currentUserId.value || row.username === 'admin'
}

async function onToggle(row: UserView) {
  const disabling = row.enabled === 1
  if (disabling) {
    // 停用即后端强退该用户全部会话，确认明示
    try {
      await ElMessageBox.confirm(
        t('users.disableConfirm', { name: row.username }),
        t('users.disableTitle'),
        { type: 'warning' },
      )
    } catch {
      return
    }
  }
  try {
    await setEnabled.mutateAsync({ id: row.id ?? 0, value: disabling ? 0 : 1 })
    ElMessage.success(disabling ? t('users.disabledOk') : t('users.enabledOk'))
  } catch (error) {
    // 保护规则（自我保护、admin 保护）由后端权威拒绝，toast 直显
    ElMessage.error(error instanceof Error ? error.message : t('users.toggleFailed'))
  }
}

/** —— 复杂表单（手写 el-form：联动规则零组件依赖纯函数见 rules.ts） —— */

const dialogVisible = ref(false)
const submitting = ref(false)
const formMode = ref<FormMode>('create')
const formRef = ref<FormInstance | null>(null)
const form = reactive<{
  id: number | null
  username: string
  nickName: string
  email: string
  phone: string
  gender: number
  enabled: number
  password: string
  deptId: number | null
  jobIds: number[]
  roleIds: number[]
}>({
  id: null,
  username: '',
  nickName: '',
  email: '',
  phone: '',
  gender: 0,
  enabled: 1,
  password: '',
  deptId: null,
  jobIds: [],
  roleIds: [],
})

const rules = computed(() => buildUserRules(formMode.value, t))

/** 岗位与角色选项：分页端点取大页一帘（选项集远小于页容量） */
const jobOptions = ref<{ id: number; name: string }[]>([])
const roleOptions = ref<{ id: number; name: string }[]>([])

async function loadOptions() {
  const [jobPage, rolePage] = await Promise.all([
    request<PageResultJobView>({ url: '/jobs?page=0&size=100' }),
    request<PageResultRoleView>({ url: '/roles?page=0&size=100' }),
  ])
  jobOptions.value = (jobPage.content ?? []) as unknown as { id: number; name: string }[]
  roleOptions.value = (rolePage.content ?? []) as { id: number; name: string }[]
}

function openCreate() {
  formMode.value = 'create'
  Object.assign(form, {
    id: null,
    username: '',
    nickName: '',
    email: '',
    phone: '',
    gender: 0,
    enabled: 1,
    password: '',
    deptId: applied.deptId ?? null,
    jobIds: [],
    roleIds: [],
  })
  dialogVisible.value = true
  void loadOptions()
}

function openEdit(row: UserView) {
  formMode.value = 'edit'
  Object.assign(form, {
    id: row.id,
    username: row.username,
    nickName: row.nickName,
    email: row.email,
    phone: row.phone,
    gender: row.gender ?? 0,
    enabled: row.enabled,
    password: '',
    deptId: row.deptId ?? null,
    jobIds: row.jobIds ?? [],
    roleIds: row.roleIds ?? [],
  })
  dialogVisible.value = true
  void loadOptions()
}

async function onFormSubmit() {
  const valid = await formRef.value?.validate().catch(() => false)
  if (!valid) {
    return
  }
  submitting.value = true
  try {
    const payload: Record<string, unknown> = { ...form }
    // 编辑不改密码（字段不渲染，密码不进提交体）
    if (formMode.value === 'edit') {
      delete payload.password
    }
    await saveUser.mutateAsync(payload as never)
    ElMessage.success(t('users.saveSuccess'))
    dialogVisible.value = false
  } catch (error) {
    // 唯一冲突、角色越权、自我保护等由后端权威拒绝，toast 直显
    ElMessage.error(error instanceof Error ? error.message : t('users.saveFailed'))
  } finally {
    submitting.value = false
  }
}

/** —— 批量操作 —— */

const selectedRows = ref<UserView[]>([])

function onSelectionChange(rows: UserView[]) {
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
      t('users.batchRemoveConfirm', { count: selectedIds.value.length }),
      t('users.deleteTitle'),
      { type: 'warning' },
    )
  } catch {
    return
  }
  try {
    await removeUser.mutateAsync(selectedIds.value)
    ElMessage.success(t('users.removeSuccess'))
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : t('users.removeFailed'))
  }
}

async function onBatchResetPassword() {
  if (!selectedIds.value.length) {
    return
  }
  try {
    await ElMessageBox.confirm(
      t('users.resetConfirm', { count: selectedIds.value.length }),
      t('users.resetTitle'),
      { type: 'warning' },
    )
  } catch {
    return
  }
  try {
    await resetPassword.mutateAsync(selectedIds.value)
    ElMessage.success(t('users.resetSuccess'))
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : t('users.resetFailed'))
  }
}

async function onResetPasswordOne(row: UserView) {
  try {
    await ElMessageBox.confirm(
      t('users.resetOneConfirm', { name: row.username }),
      t('users.resetTitle'),
      {
        type: 'warning',
      },
    )
  } catch {
    return
  }
  try {
    await resetPassword.mutateAsync([row.id ?? 0])
    ElMessage.success(t('users.resetSuccess'))
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : t('users.resetFailed'))
  }
}

async function onRemoveOne(row: UserView) {
  try {
    await ElMessageBox.confirm(
      t('users.removeOneConfirm', { name: row.username }),
      t('users.deleteTitle'),
      { type: 'warning' },
    )
  } catch {
    return
  }
  try {
    await removeUser.mutateAsync([row.id ?? 0])
    ElMessage.success(t('users.removeSuccess'))
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : t('users.removeFailed'))
  }
}

/** —— 列定义 —— */

const columns = computed<DataTableColumn<UserView>[]>(() => [
  { id: 'selection', type: 'selection' },
  { accessorKey: 'username', header: t('users.username') },
  { accessorKey: 'nickName', header: t('users.nickName') },
  { accessorKey: 'deptName', header: t('users.deptName') },
  { accessorKey: 'email', header: t('users.email') },
  {
    accessorKey: 'gender',
    header: t('users.gender'),
    cell: ({ row }) => {
      const labelKey = GENDER_LABEL_KEYS[row.original.gender ?? 0]
      return h('span', labelKey ? t(labelKey) : '')
    },
  },
  {
    accessorKey: 'enabled',
    header: t('users.status'),
    cell: ({ row }) =>
      isProtectedRow(row.original)
        ? h(ElTag, { type: row.original.enabled === 1 ? 'success' : 'info' }, () =>
            row.original.enabled === 1 ? t('users.enabled') : t('users.disabled'),
          )
        : h(ElSwitch, {
            modelValue: row.original.enabled === 1,
            loading: setEnabled.isPending.value,
            'data-test': 'users-enabled',
            onChange: () => onToggle(row.original),
          }),
  },
  {
    accessorKey: 'createdAt',
    header: t('users.createdAt'),
    cell: ({ row }) => h('span', formatDateTime(row.original.createdAt)),
  },
  {
    id: 'actions',
    header: t('users.actions'),
    cell: ({ row }) =>
      h('span', { class: 'users__row-actions' }, [
        hasAuth('system:user:edit') && !isProtectedRow(row.original)
          ? h(
              ElButton,
              {
                link: true,
                type: 'primary',
                'data-test': 'users-edit',
                onClick: () => openEdit(row.original),
              },
              () => t('users.edit'),
            )
          : null,
        hasAuth('system:user:resetPwd') && !isProtectedRow(row.original)
          ? h(
              ElButton,
              {
                link: true,
                type: 'warning',
                'data-test': 'users-reset-pwd',
                onClick: () => onResetPasswordOne(row.original),
              },
              () => t('users.resetPwd'),
            )
          : null,
        hasAuth('system:user:del') && !isProtectedRow(row.original)
          ? h(
              ElButton,
              {
                link: true,
                type: 'danger',
                'data-test': 'users-remove',
                onClick: () => onRemoveOne(row.original),
              },
              () => t('users.delete'),
            )
          : null,
      ]),
  },
])

const rows = computed(() => data.value?.content ?? [])
const total = computed(() => data.value?.total ?? 0)
</script>

<template>
  <div class="users" data-test="users-page">
    <aside class="users__aside">
      <el-tree
        :data="deptTree ?? []"
        node-key="id"
        :props="{ label: 'name', children: 'children' }"
        :current-node-key="currentDeptId ?? undefined"
        default-expand-all
        highlight-current
        data-test="users-dept-tree"
        @node-click="onDeptClick"
      />
    </aside>
    <section class="users__main">
      <div class="users__filter">
        <el-input
          v-model="filter.name"
          :placeholder="t('users.filterName')"
          clearable
          data-test="users-filter-name"
          @keyup.enter="onSearch"
        />
        <el-input
          v-model="filter.email"
          :placeholder="t('users.filterEmail')"
          clearable
          data-test="users-filter-email"
          @keyup.enter="onSearch"
        />
        <el-select
          v-model="filter.enabled"
          :placeholder="t('users.filterStatus')"
          clearable
          data-test="users-filter-status"
        >
          <el-option :label="t('users.enabled')" :value="1" />
          <el-option :label="t('users.disabled')" :value="0" />
        </el-select>
        <el-date-picker
          v-model="filter.range"
          type="daterange"
          value-format="YYYY-MM-DD"
          :start-placeholder="t('users.startDate')"
          :end-placeholder="t('users.endDate')"
          data-test="users-filter-range"
        />
        <el-button type="primary" data-test="users-search" @click="onSearch">
          {{ t('users.search') }}
        </el-button>
        <el-button data-test="users-reset" @click="onReset">{{ t('users.reset') }}</el-button>
        <el-button
          v-if="hasAuth('system:user:add')"
          type="primary"
          plain
          data-test="users-create"
          @click="openCreate"
        >
          {{ t('users.create') }}
        </el-button>
        <el-button
          v-if="hasAuth('system:user:del') && selectedIds.length"
          type="danger"
          plain
          data-test="users-batch-remove"
          @click="onBatchRemove"
        >
          {{ t('users.batchRemove') }}
        </el-button>
        <el-button
          v-if="hasAuth('system:user:resetPwd') && selectedIds.length"
          type="warning"
          plain
          data-test="users-batch-reset"
          @click="onBatchResetPassword"
        >
          {{ t('users.batchReset') }}
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
        :texts="tableTexts"
        data-test="users-table"
        @page-change="
          ({ page: p, pageSize: s }) => {
            page = p
            pageSize = s
          }
        "
        @selection-change="onSelectionChange"
      />

      <el-dialog
        v-model="dialogVisible"
        :title="formMode === 'create' ? t('users.create') : t('users.edit')"
        width="640px"
        destroy-on-close
        data-test="users-dialog"
      >
        <el-form ref="formRef" :model="form" :rules="rules" label-width="100px">
          <el-form-item :label="t('users.username')" prop="username">
            <el-input
              v-model="form.username"
              :disabled="formMode === 'edit'"
              data-test="users-username"
            />
          </el-form-item>
          <el-form-item :label="t('users.nickName')" prop="nickName">
            <el-input v-model="form.nickName" data-test="users-nickname" />
          </el-form-item>
          <el-form-item :label="t('users.email')" prop="email">
            <el-input v-model="form.email" data-test="users-email" />
          </el-form-item>
          <el-form-item :label="t('users.phone')" prop="phone">
            <el-input v-model="form.phone" data-test="users-phone" />
          </el-form-item>
          <el-form-item :label="t('users.gender')">
            <el-radio-group v-model="form.gender">
              <el-radio v-for="option in GENDER_OPTIONS" :key="option.value" :value="option.value">
                {{ t(option.labelKey) }}
              </el-radio>
            </el-radio-group>
          </el-form-item>
          <el-form-item :label="t('users.status')">
            <el-radio-group v-model="form.enabled">
              <el-radio :value="1">{{ t('users.enabled') }}</el-radio>
              <el-radio :value="0">{{ t('users.disabled') }}</el-radio>
            </el-radio-group>
          </el-form-item>
          <el-form-item v-if="formMode === 'create'" :label="t('users.password')" prop="password">
            <el-input
              v-model="form.password"
              type="password"
              show-password
              data-test="users-password"
            />
          </el-form-item>
          <el-form-item :label="t('users.dept')">
            <el-tree-select
              v-model="form.deptId"
              :data="deptTree ?? []"
              node-key="id"
              check-strictly
              clearable
              :props="{ label: 'name', children: 'children' }"
              data-test="users-dept-select"
            />
          </el-form-item>
          <el-form-item :label="t('users.jobs')">
            <el-select v-model="form.jobIds" multiple data-test="users-job-select">
              <el-option
                v-for="job in jobOptions"
                :key="job.id"
                :label="job.name"
                :value="job.id"
              />
            </el-select>
          </el-form-item>
          <el-form-item :label="t('users.roles')">
            <el-select v-model="form.roleIds" multiple data-test="users-role-select">
              <el-option
                v-for="role in roleOptions"
                :key="role.id"
                :label="role.name"
                :value="role.id"
              />
            </el-select>
          </el-form-item>
        </el-form>
        <template #footer>
          <el-button @click="dialogVisible = false">{{ t('users.cancel') }}</el-button>
          <el-button
            type="primary"
            :loading="submitting"
            data-test="users-form-submit"
            @click="onFormSubmit"
          >
            {{ t('users.submit') }}
          </el-button>
        </template>
      </el-dialog>
    </section>
  </div>
</template>

<style scoped lang="scss">
.users {
  display: flex;
  gap: 16px;

  .users__aside {
    width: 220px;
    flex-shrink: 0;
  }

  .users__main {
    flex: 1;
    min-width: 0;
  }

  // 窄屏纵排：部门树置顶收窄，列表满幅（L2 不破版）
  @media (max-width: 768px) {
    flex-direction: column;

    .users__aside {
      width: 100%;
      max-height: 200px;
      overflow: auto;
    }
  }
}

.users__filter {
  display: flex;
  flex-wrap: wrap;
  gap: 12px;
  align-items: center;
  margin-bottom: 12px;

  .el-input {
    width: 160px;
  }

  .el-select {
    width: 120px;
  }
}
</style>
