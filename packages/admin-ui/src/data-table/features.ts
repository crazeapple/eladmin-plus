import { columnVisibilityFeature, rowSelectionFeature, tableFeatures } from '@tanstack/vue-table'

/** DataTable 启用的 TanStack 特性集：列显隐 + 行选择 */
export const dataTableFeatures = tableFeatures({
  columnVisibilityFeature,
  rowSelectionFeature,
})
