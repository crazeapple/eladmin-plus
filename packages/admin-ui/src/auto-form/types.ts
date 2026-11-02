import type { ZodType } from 'zod'

export type { ZodType }

/** 字段类型克制清单（清单外类型经 field-<name> 具名插槽手写） */
export type AutoFormFieldType =
  'input' | 'select' | 'date' | 'radio' | 'checkbox' | 'tree-select' | 'upload'

export interface AutoFormOption {
  label: string
  value: string | number | boolean
}

export interface AutoFormField {
  /** 字段名：对应 modelValue 键与 zod schema 顶层 path，同时决定插槽名 field-<name> */
  name: string
  label: string
  type: AutoFormFieldType
  placeholder?: string
  /** select / radio / checkbox 的选项清单 */
  options?: AutoFormOption[]
  /** 透传给 EP 组件的属性（如 tree-select 的 data、date 的 valueFormat 覆盖） */
  props?: Record<string, unknown>
}

/** 表单模型：键值对，值类型由 zod schema 约定 */
export type AutoFormModel = Record<string, unknown>
