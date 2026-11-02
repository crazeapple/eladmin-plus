<script setup lang="ts" generic="TData extends RowData">
import { computed, useSlots } from 'vue'
import { ElEmpty } from 'element-plus'
import { FlexRender, useTable } from '@tanstack/vue-table'
import type { Cell, Header, RowData } from '@tanstack/vue-table'
import { dataTableFeatures } from './features'
import type { DataTableColumn, DataTableTexts } from './types'

const props = withDefaults(
  defineProps<{
    /** 列定义（TanStack ColumnDef 薄封装，`type: 'selection'` 声明选择列） */
    columns: DataTableColumn<TData>[]
    /** 当前页数据（受控：组件不发请求，网络态归页面 query 层） */
    data: TData[]
    loading?: boolean
    /** 行标识字段（选择状态按行 id 记账，跨页保持） */
    rowKey?: string
    texts?: DataTableTexts
  }>(),
  {
    loading: false,
    rowKey: 'id',
    texts: undefined,
  },
)

const slots = useSlots()

// 文案默认中文，页面经 texts 覆盖（i18n 注入点）
const resolvedTexts = computed<Required<DataTableTexts>>(() => ({
  refresh: '刷新',
  columnSettings: '列设置',
  empty: '暂无数据',
  ...props.texts,
}))

// 选择列补全 id 与能力关闭标记（不排序、不可隐藏）；业务列原样透传
const normColumns = computed<DataTableColumn<TData>[]>(() =>
  props.columns.map((def) =>
    def.type === 'selection'
      ? { id: 'selection', enableSorting: false, enableHiding: false, ...def }
      : def,
  ),
)

const table = useTable<typeof dataTableFeatures, TData>({
  features: dataTableFeatures,
  columns: normColumns,
  data: computed(() => props.data),
  getRowId: (row) => String((row as Record<string, unknown>)[props.rowKey]),
  enableRowSelection: true,
})

const isEmpty = computed(() => !props.loading && props.data.length === 0)
const columnCount = computed(() => table.getHeaderGroups()[0]?.headers.length ?? 0)

function isSelectionCell(cell: Cell<typeof dataTableFeatures, TData, unknown>): boolean {
  return (cell.column.columnDef as DataTableColumn<TData>).type === 'selection'
}

function isSelectionHeader(header: Header<typeof dataTableFeatures, TData>): boolean {
  return (header.column.columnDef as DataTableColumn<TData>).type === 'selection'
}

// 单元格具名槽：列 id 即槽名（cell-<id>），页面可接管任意列的渲染
function cellSlotName(cell: Cell<typeof dataTableFeatures, TData, unknown>): string {
  return `cell-${cell.column.id}`
}
</script>

<template>
  <div class="data-table" data-test="admin-table">
    <div class="data-table__frame">
      <div v-if="loading" class="data-table__loading" data-test="admin-table-loading">
        <span class="data-table__spinner" aria-hidden="true"></span>
      </div>
      <table class="data-table__table">
        <thead>
          <tr v-for="group in table.getHeaderGroups()" :key="group.id">
            <th v-for="header in group.headers" :key="header.id">
              <FlexRender
                v-if="!isSelectionHeader(header) && !header.isPlaceholder"
                :header="header"
              />
            </th>
          </tr>
        </thead>
        <tbody>
          <tr v-if="isEmpty">
            <td :colspan="columnCount">
              <div class="data-table__empty" data-test="admin-table-empty">
                <ElEmpty :image-size="60" :description="resolvedTexts.empty" />
              </div>
            </td>
          </tr>
          <tr v-for="row in table.getRowModel().rows" v-else :key="row.id">
            <td v-for="cell in row.getAllCells()" :key="cell.id">
              <slot
                v-if="!isSelectionCell(cell) && slots[cellSlotName(cell)]"
                :name="cellSlotName(cell)"
                :row="row.original"
                :value="cell.getValue()"
              />
              <FlexRender v-else-if="!isSelectionCell(cell)" :cell="cell" />
            </td>
          </tr>
        </tbody>
      </table>
    </div>
  </div>
</template>

<style scoped>
.data-table {
  position: relative;
  color: var(--el-text-color-regular);
  font-size: var(--el-font-size-base);
}

.data-table__frame {
  position: relative;
  overflow: hidden;
  background: var(--el-bg-color);
  border: 1px solid var(--el-border-color-lighter);
  border-radius: var(--el-border-radius-base);
}

.data-table__table {
  width: 100%;
  border-collapse: collapse;
}

.data-table__table th,
.data-table__table td {
  padding: 10px 12px;
  text-align: left;
}

.data-table__table thead th {
  font-weight: 600;
  color: var(--el-text-color-regular);
  white-space: nowrap;
  background: var(--el-fill-color-light);
}

.data-table__table tbody td {
  border-top: 1px solid var(--el-border-color-lighter);
}

.data-table__table tbody tr:hover > td {
  background: var(--el-fill-color-lighter);
}

.data-table__empty {
  padding: 8px 0;
}

.data-table__loading {
  position: absolute;
  inset: 0;
  z-index: 1;
  display: flex;
  align-items: center;
  justify-content: center;
  background: color-mix(in srgb, var(--el-bg-color) 65%, transparent);
}

.data-table__spinner {
  width: 28px;
  height: 28px;
  border: 2px solid var(--el-color-primary-light-7);
  border-top-color: var(--el-color-primary);
  border-radius: 50%;
  animation: data-table-spin 0.8s linear infinite;
}

@keyframes data-table-spin {
  to {
    transform: rotate(360deg);
  }
}
</style>
