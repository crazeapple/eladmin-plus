import type { ColumnDef, RowData } from '@tanstack/vue-table'
import type { dataTableFeatures } from './features'

/** 列定义：TanStack ColumnDef 薄封装，外加选择列声明（type: 'selection'） */
export type DataTableColumn<TData extends RowData = RowData> = ColumnDef<
  typeof dataTableFeatures,
  TData
> & { type?: 'selection' }

/** 文案注入（包内不绑定 i18n 实例，默认中文，页面可传覆盖） */
export interface DataTableTexts {
  refresh?: string
  columnSettings?: string
  empty?: string
}

/** 排序契约：单字段语义，字段名对应列 id（accessorKey） */
export interface DataTableSortState {
  field: string
  order: 'asc' | 'desc'
}

/** 分页事件载荷：page 0 起（组件内完成与 EP 分页 1 起页码的换算） */
export interface DataTablePageChangePayload {
  page: number
  pageSize: number
}
