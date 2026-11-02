import {
  columnVisibilityFeature,
  rowPaginationFeature,
  rowSelectionFeature,
  rowSortingFeature,
  tableFeatures,
} from '@tanstack/vue-table'

/**
 * DataTable 启用的 TanStack 特性集：列显隐 + 行选择 + 分页与排序状态
 * （分页排序均为 manual 模式，行模型不本地切片/排序，数据即服务端当前页）
 */
export const dataTableFeatures = tableFeatures({
  columnVisibilityFeature,
  rowPaginationFeature,
  rowSelectionFeature,
  rowSortingFeature,
})
