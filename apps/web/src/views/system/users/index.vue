<script setup lang="ts">
import { ElButton, ElMessage, ElMessageBox, ElSwitch } from 'element-plus'
import { computed, h, reactive, ref } from 'vue'
import { useI18n } from 'vue-i18n'

import { DataTable } from '@eladmin-plus/admin-ui'
import type { DataTableColumn } from '@eladmin-plus/admin-ui'

import { useDeptTree } from '@/api/depts'
import type { DeptTreeNode, UserView } from '@/api/generated'
import { useUserEnabled, useUserPage } from '@/api/users'

defineOptions({ name: 'SystemUsers' })

const { t } = useI18n()

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

const columns = computed<DataTableColumn<UserView>[]>(() => [
  { accessorKey: 'username', header: t('users.username') },
  { accessorKey: 'nickName', header: t('users.nickName') },
  { accessorKey: 'deptName', header: t('users.deptName') },
  { accessorKey: 'email', header: t('users.email') },
  {
    accessorKey: 'enabled',
    header: t('users.status'),
    cell: ({ row }) =>
      h(ElSwitch, {
        modelValue: row.original.enabled === 1,
        loading: setEnabled.isPending.value,
        'data-test': 'users-enabled',
        onChange: () => onToggle(row.original),
      }),
  },
  { accessorKey: 'createdAt', header: t('users.createdAt') },
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
      </div>

      <DataTable
        :columns="columns"
        :data="rows"
        :loading="isLoading"
        :page="page"
        :page-size="pageSize"
        :total="total"
        data-test="users-table"
        @page-change="
          ({ page: p, pageSize: s }) => {
            page = p
            pageSize = s
          }
        "
      />
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
