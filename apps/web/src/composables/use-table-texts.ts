import { useI18n } from 'vue-i18n'
import { computed } from 'vue'

import type { DataTableTexts } from '@eladmin-plus/admin-ui'

/**
 * 列表页工具栏文案：DataTable 的 texts 注入点统一收口到 i18n，
 * 「列设置」「刷新」等随语言即时联动（组件层不持语言包）。
 */
export function useTableTexts() {
  const { t } = useI18n()
  return computed<DataTableTexts>(() => ({
    columnSettings: t('common.columnSettings'),
    refresh: t('common.refresh'),
    empty: t('common.empty'),
    selectAll: t('common.selectAll'),
    selectRow: t('common.selectRow'),
  }))
}
