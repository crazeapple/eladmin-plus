/** 性别常量（与后端语义一致：0 未知 1 男 2 女；字典后端 P-013 就位后如需对齐另议） */
export interface GenderOption {
  value: number
  /** i18n key */
  labelKey: string
}

export const GENDER_OPTIONS: GenderOption[] = [
  { value: 1, labelKey: 'users.genderMale' },
  { value: 2, labelKey: 'users.genderFemale' },
  { value: 0, labelKey: 'users.genderUnknown' },
]

export const GENDER_LABEL_KEYS: Record<number, string> = {
  0: 'users.genderUnknown',
  1: 'users.genderMale',
  2: 'users.genderFemale',
}
