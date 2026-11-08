export interface DictOption {
  label: string
  value: string | number
}

/** 字典数据源契约：type → 选项清单（字典后端就位后经 setDictSource 换源） */
export type DictSource = (type: string) => Promise<DictOption[]>
