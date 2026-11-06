<script setup lang="ts">
import { ElButton, ElMessage, ElMessageBox, ElTooltip } from 'element-plus'
import { computed, h } from 'vue'
import { useI18n } from 'vue-i18n'
import { useTableTexts } from '@/composables/use-table-texts'
import { formatDateTime } from '@/utils/datetime'

import { DataTable } from '@eladmin-plus/admin-ui'
import type { DataTableColumn } from '@eladmin-plus/admin-ui'

import type { OnlineUserView } from '@/api/generated'
import { useOnlineKick, useOnlineUsers } from '@/api/monitor'
import { useAuth } from '@/composables/use-auth'
import { useMenusStore } from '@/stores/menus'
import { parseUa } from '@/utils/ua'

defineOptions({ name: 'MonitorOnlineUsers' })

const { t } = useI18n()
const tableTexts = useTableTexts()
const { hasAuth } = useAuth()
const menusStore = useMenusStore()

/** 在线清单与会话索引枚举同源：多端登录多行并列 */
const { data, isLoading } = useOnlineUsers()
const kick = useOnlineKick()

/** 当前登录用户 id：强退目标是自己时确认文案明示将登出当前会话 */
const currentUserId = computed(() => menusStore.user?.id ?? null)

/** UA 列：原文截断展示，tooltip 全文 */
function uaCell(raw: string | undefined) {
  const text = raw ?? ''
  return h(ElTooltip, { content: text, placement: 'top', disabled: !text }, () =>
    h('span', { class: 'online-users__ua' }, text),
  )
}

async function onKick(row: OnlineUserView) {
  const isSelf = row.userId !== undefined && row.userId === currentUserId.value
  const message = isSelf
    ? t('onlineUsers.kickSelfConfirm', { name: row.username })
    : t('onlineUsers.kickConfirm', { name: row.username })
  try {
    await ElMessageBox.confirm(message, t('onlineUsers.kickTitle'), { type: 'warning' })
  } catch {
    return
  }
  try {
    await kick.mutateAsync(row.userId ?? 0)
    // 强退成功列表即刷（api 层失效查询）；强退自己由后续 401 流程接管登出
    ElMessage.success(t('onlineUsers.kickSuccess'))
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : t('onlineUsers.kickFailed'))
  }
}

/** —— 列定义：五字段 + UA 解析三列 —— */

const columns = computed<DataTableColumn<OnlineUserView>[]>(() => [
  { accessorKey: 'nickName', header: t('onlineUsers.nickName') },
  { accessorKey: 'username', header: t('onlineUsers.username') },
  { accessorKey: 'ip', header: t('onlineUsers.ip') },
  {
    accessorKey: 'loginAt',
    header: t('onlineUsers.loginAt'),
    cell: ({ row }) => h('span', formatDateTime(row.original.loginAt)),
  },
  {
    accessorKey: 'browser',
    header: t('onlineUsers.browser'),
    cell: ({ row }) => uaCell(row.original.browser),
  },
  {
    id: 'uaBrowser',
    header: t('onlineUsers.uaBrowser'),
    cell: ({ row }) =>
      h('span', { 'data-test': 'ua-browser' }, parseUa(row.original.browser).browser),
  },
  {
    id: 'uaVersion',
    header: t('onlineUsers.uaVersion'),
    cell: ({ row }) =>
      h('span', { 'data-test': 'ua-version' }, parseUa(row.original.browser).version),
  },
  {
    id: 'uaOs',
    header: t('onlineUsers.uaOs'),
    cell: ({ row }) => h('span', { 'data-test': 'ua-os' }, parseUa(row.original.browser).os),
  },
  {
    id: 'actions',
    header: t('onlineUsers.actions'),
    cell: ({ row }) =>
      hasAuth('monitor:online:del')
        ? h(
            ElButton,
            {
              link: true,
              type: 'danger',
              'data-test': 'online-users-kick',
              onClick: () => onKick(row.original),
            },
            () => t('onlineUsers.kick'),
          )
        : null,
  },
])

const rows = computed(() => data.value ?? [])
</script>

<template>
  <div class="online-users" data-test="online-users-page">
    <div class="online-users__bar">
      <span class="online-users__count">
        {{ t('onlineUsers.onlineCount', { count: rows.length }) }}
      </span>
    </div>

    <DataTable
      :texts="tableTexts"
      :columns="columns"
      :data="rows"
      :loading="isLoading"
      row-key="userId"
      data-test="online-users-table"
    />
  </div>
</template>

<style scoped lang="scss">
.online-users__bar {
  display: flex;
  align-items: center;
  margin-bottom: 12px;
}

.online-users__count {
  color: var(--el-text-color-secondary);
}

.online-users__ua {
  display: inline-block;
  max-width: 220px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  vertical-align: bottom;
}
</style>
