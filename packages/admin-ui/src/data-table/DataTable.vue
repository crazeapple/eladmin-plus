<script setup lang="ts" generic="TData extends RowData">
import { computed, ref, useSlots } from 'vue'
import { useMediaQuery } from '@vueuse/core'
import {
  ElButton,
  ElCheckbox,
  ElCheckboxGroup,
  ElEmpty,
  ElPagination,
  ElPopover,
} from 'element-plus'
import { FlexRender, useTable } from '@tanstack/vue-table'
import type {
  Cell,
  ColumnVisibilityState,
  Header,
  PaginationState,
  RowData,
  RowSelectionState,
  SortingState,
  Updater,
} from '@tanstack/vue-table'
import { dataTableFeatures } from './features'
import type {
  DataTableColumn,
  DataTablePageChangePayload,
  DataTableSortState,
  DataTableTexts,
} from './types'

const props = withDefaults(
  defineProps<{
    /** 列定义（TanStack ColumnDef 薄封装，`type: 'selection'` 声明选择列） */
    columns: DataTableColumn<TData>[]
    /** 当前页数据（受控：组件不发请求，网络态归页面 query 层） */
    data: TData[]
    loading?: boolean
    /** 页码 0 起（组件内换算 EP 分页的 1 起页码） */
    page?: number
    pageSize?: number
    /** 总条数（缺省取 data.length，服务端分页时由页面传入） */
    total?: number
    /** 排序契约：单字段语义 */
    sort?: DataTableSortState | null
    /** 行标识字段（选择状态按行 id 记账，跨页保持） */
    rowKey?: string
    texts?: DataTableTexts
  }>(),
  {
    loading: false,
    page: 0,
    pageSize: 20,
    total: undefined,
    sort: null,
    rowKey: 'id',
    texts: undefined,
  },
)

const emit = defineEmits<{
  /** page 0 起；页大小切换时 page 重置为 0 */
  'page-change': [payload: DataTablePageChangePayload]
  /** 单字段排序；null 表示已取消排序 */
  'sort-change': [sort: DataTableSortState | null]
  'selection-change': [rows: TData[]]
  /** 刷新按钮只上抛事件，是否重新请求由页面定 */
  refresh: []
}>()

const slots = useSlots()

// 移动端断点与布局壳一致（768px）；useMediaQuery 初值取 matchMedia 当前值，无闪烁
const isMobile = useMediaQuery('(max-width: 768px)')

// 文案默认中文，页面经 texts 覆盖（i18n 注入点）
const resolvedTexts = computed<Required<DataTableTexts>>(() => ({
  refresh: '刷新',
  columnSettings: '列设置',
  empty: '暂无数据',
  selectAll: '全选',
  selectRow: '选择此行',
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

// 组件自持的 UI 态（列显隐、行选择）；分页与排序受控于 props，经事件回抛
const columnVisibility = ref<ColumnVisibilityState>({})
const rowSelection = ref<RowSelectionState>({})

const sorting = computed<SortingState>(() =>
  props.sort ? [{ id: props.sort.field, desc: props.sort.order === 'desc' }] : [],
)
const pagination = computed<PaginationState>(() => ({
  pageIndex: props.page,
  pageSize: props.pageSize,
}))

const state = computed(() => ({
  sorting: sorting.value,
  pagination: pagination.value,
  columnVisibility: columnVisibility.value,
  rowSelection: rowSelection.value,
}))

function resolveUpdater<T>(updater: Updater<T>, old: T): T {
  // T 本身可能是函数类型，typeof 收窄对泛型不成立，需显式断言
  return typeof updater === 'function' ? (updater as (old: T) => T)(old) : updater
}

const table = useTable<typeof dataTableFeatures, TData>({
  features: dataTableFeatures,
  columns: normColumns,
  data: computed(() => props.data),
  state,
  manualSorting: true,
  manualPagination: true,
  getRowId: (row) => String((row as Record<string, unknown>)[props.rowKey]),
  enableRowSelection: true,
  onSortingChange: (updater) => {
    const next = resolveUpdater(updater, sorting.value)
    emit(
      'sort-change',
      next.length
        ? { field: next[0]!.id, order: next[0]!.desc ? ('desc' as const) : ('asc' as const) }
        : null,
    )
  },
  onPaginationChange: (updater) => {
    const next = resolveUpdater(updater, pagination.value)
    emit('page-change', { page: next.pageIndex, pageSize: next.pageSize })
  },
  onColumnVisibilityChange: (updater) => {
    columnVisibility.value = resolveUpdater(updater, columnVisibility.value)
  },
  onRowSelectionChange: (updater) => {
    const next = resolveUpdater(updater, rowSelection.value)
    rowSelection.value = next
    // 选中行按行 id 从当前数据还原，顺序与数据一致
    emit(
      'selection-change',
      props.data.filter((row) => next[String((row as Record<string, unknown>)[props.rowKey])]),
    )
  },
})

const isEmpty = computed(() => !props.loading && props.data.length === 0)
const totalRows = computed(() => props.total ?? props.data.length)
const columnCount = computed(() => table.getHeaderGroups()[0]?.headers.length ?? 0)

// 列设置清单：可隐藏列（选择列 enableHiding: false 已被排除）
const hideableColumns = computed(() =>
  table
    .getAllLeafColumns()
    .filter((column) => column.getCanHide())
    .map((column) => ({
      id: column.id,
      label: typeof column.columnDef.header === 'string' ? column.columnDef.header : column.id,
      visible: column.getIsVisible(),
    })),
)
const visibleIds = computed(() =>
  hideableColumns.value.filter((column) => column.visible).map((column) => column.id),
)

function applyVisibility(nextIds: Array<string | number | boolean>) {
  const next: ColumnVisibilityState = {}
  for (const column of hideableColumns.value) {
    next[column.id] = nextIds.includes(column.id)
  }
  columnVisibility.value = next
}

function isSelectionCell(cell: Cell<typeof dataTableFeatures, TData, unknown>): boolean {
  return (cell.column.columnDef as DataTableColumn<TData>).type === 'selection'
}

function isSelectionHeader(header: Header<typeof dataTableFeatures, TData>): boolean {
  return (header.column.columnDef as DataTableColumn<TData>).type === 'selection'
}

function canSort(header: Header<typeof dataTableFeatures, TData>): boolean {
  return header.column.getCanSort()
}

function sortMark(header: Header<typeof dataTableFeatures, TData>): string {
  const sorted = header.column.getIsSorted()
  return sorted === 'asc' ? '↑' : sorted === 'desc' ? '↓' : '↕'
}

function ariaSort(
  header: Header<typeof dataTableFeatures, TData>,
): 'ascending' | 'descending' | 'none' | undefined {
  if (!canSort(header)) return undefined
  const sorted = header.column.getIsSorted()
  return sorted === 'asc' ? 'ascending' : sorted === 'desc' ? 'descending' : 'none'
}

// 单元格具名槽：列 id 即槽名（cell-<id>），页面可接管任意列的渲染
function cellSlotName(cell: Cell<typeof dataTableFeatures, TData, unknown>): string {
  return `cell-${cell.column.id}`
}
</script>

<template>
  <div class="data-table" data-test="admin-table">
    <div class="data-table__toolbar" data-test="admin-table-toolbar">
      <slot name="toolbar-extra" />
      <div class="data-table__toolbar-actions">
        <ElPopover trigger="click" placement="bottom-end" :width="160">
          <template #reference>
            <ElButton>{{ resolvedTexts.columnSettings }}</ElButton>
          </template>
          <div class="data-table__column-settings" data-test="admin-table-column-settings">
            <ElCheckboxGroup :model-value="visibleIds" @update:model-value="applyVisibility">
              <ElCheckbox v-for="column in hideableColumns" :key="column.id" :value="column.id">
                {{ column.label }}
              </ElCheckbox>
            </ElCheckboxGroup>
          </div>
        </ElPopover>
        <ElButton data-test="admin-table-refresh" @click="emit('refresh')">
          {{ resolvedTexts.refresh }}
        </ElButton>
      </div>
    </div>

    <div class="data-table__frame">
      <div v-if="loading" class="data-table__loading" data-test="admin-table-loading">
        <span class="data-table__spinner" aria-hidden="true"></span>
      </div>
      <table v-if="!isMobile" class="data-table__table">
        <thead>
          <tr v-for="group in table.getHeaderGroups()" :key="group.id">
            <th v-for="header in group.headers" :key="header.id" :aria-sort="ariaSort(header)">
              <ElCheckbox
                v-if="isSelectionHeader(header)"
                :model-value="table.getIsAllPageRowsSelected()"
                :aria-label="resolvedTexts.selectAll"
                data-test="admin-table-select-all"
                @update:model-value="
                  table.toggleAllPageRowsSelected(!table.getIsAllPageRowsSelected())
                "
              />
              <button
                v-else-if="canSort(header)"
                type="button"
                class="data-table__sort"
                @click="header.column.toggleSorting()"
              >
                <FlexRender v-if="!header.isPlaceholder" :header="header" />
                <span class="data-table__sort-mark" aria-hidden="true">{{ sortMark(header) }}</span>
              </button>
              <FlexRender v-else-if="!header.isPlaceholder" :header="header" />
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
              <ElCheckbox
                v-if="isSelectionCell(cell)"
                :model-value="row.getIsSelected()"
                :aria-label="resolvedTexts.selectRow"
                data-test="admin-table-row-select"
                @update:model-value="row.toggleSelected()"
              />
              <slot
                v-else-if="slots[cellSlotName(cell)]"
                :name="cellSlotName(cell)"
                :row="row.original"
                :value="cell.getValue()"
              />
              <FlexRender v-else :cell="cell" />
            </td>
          </tr>
        </tbody>
      </table>

      <!-- 移动端卡片形态：内容由页面经 card-row 插槽提供，选择/操作列语义由插槽自带 -->
      <div v-else class="data-table__cards" data-test="admin-table-cards">
        <div v-if="isEmpty" class="data-table__empty" data-test="admin-table-empty">
          <ElEmpty :image-size="60" :description="resolvedTexts.empty" />
        </div>
        <div
          v-for="row in table.getRowModel().rows"
          v-else
          :key="row.id"
          class="data-table__card"
          data-test="admin-table-card"
        >
          <slot name="card-row" :row="row.original" />
        </div>
      </div>
    </div>

    <div v-if="totalRows > 0" class="data-table__pagination" data-test="admin-table-pagination">
      <ElPagination
        :current-page="props.page + 1"
        :page-size="props.pageSize"
        :total="totalRows"
        :page-sizes="[10, 20, 50, 100]"
        layout="total, sizes, prev, pager, next, jumper"
        @current-change="
          (next: number) => emit('page-change', { page: next - 1, pageSize: props.pageSize })
        "
        @size-change="(size: number) => emit('page-change', { page: 0, pageSize: size })"
      />
    </div>
  </div>
</template>

<style scoped>
.data-table {
  position: relative;
  color: var(--el-text-color-regular);
  font-size: var(--el-font-size-base);
}

.data-table__toolbar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  margin-bottom: 12px;
}

.data-table__toolbar-actions {
  display: flex;
  align-items: center;
  gap: 8px;
}

.data-table__column-settings {
  display: flex;
  flex-direction: column;
  gap: 4px;
}

.data-table__frame {
  position: relative;
  overflow: hidden;
  background: var(--el-bg-color);
  border: 1px solid var(--el-border-color-lighter);
  /* 品牌 token 直引（带 fallback）：圆角档位与卡片阴影走设计基座 */
  border-radius: var(--ea-radius-md, 8px);
  box-shadow: var(--ea-shadow-1, 0 1px 2px rgb(15 23 42 / 6%));
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

.data-table__sort {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  padding: 0;
  font: inherit;
  font-weight: 600;
  color: inherit;
  cursor: pointer;
  background: none;
  border: none;
}

.data-table__sort-mark {
  font-size: 12px;
  color: var(--el-color-primary);
}

.data-table__empty {
  padding: 8px 0;
}

.data-table__cards {
  padding: 4px 12px;
}

.data-table__card {
  padding: 12px 0;
  border-bottom: 1px solid var(--el-border-color-lighter);
}

.data-table__card:last-child {
  border-bottom: none;
}

.data-table__pagination {
  display: flex;
  justify-content: flex-end;
  margin-top: 12px;
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
  border-top-color: var(--ea-primary, var(--el-color-primary));
  border-radius: 50%;
  animation: data-table-spin 0.8s linear infinite;
}

@keyframes data-table-spin {
  to {
    transform: rotate(360deg);
  }
}
</style>
