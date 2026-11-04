/**
 * cron 常用预设（本地常量，提效捷径不是校验）与前端初检提示。
 * 后端 CronExpression 校验为权威（422 直显），前端初检只提示不阻断。
 */
export interface CronPreset {
  /** 展示名（i18n key） */
  labelKey: string
  expression: string
}

export const CRON_PRESETS: CronPreset[] = [
  { labelKey: 'tasks.cronPresetEveryMinute', expression: '0 * * * * ?' },
  { labelKey: 'tasks.cronPresetEveryHour', expression: '0 0 * * * ?' },
  { labelKey: 'tasks.cronPresetDaily', expression: '0 0 0 * * ?' },
  { labelKey: 'tasks.cronPresetWeekly', expression: '0 0 0 ? * MON' },
  { labelKey: 'tasks.cronPresetMonthly', expression: '0 0 0 1 * ?' },
]

/** Quartz cron 字段合法字符（秒 分 时 日 月 周 [年]） */
const CRON_FIELD_PATTERN = /^[\dA-Za-z*/?,#\-.]+$/

/**
 * 前端初检：字段数 6~7 且逐字段字符合法返回 null（视为可信，交后端权威）；
 * 结构可疑返回提示文案 key。仅提示不阻断保存。
 */
export function cronHint(expression: string): string | null {
  const trimmed = expression.trim()
  if (!trimmed) {
    return 'tasks.cronHintEmpty'
  }
  const fields = trimmed.split(/\s+/)
  if (fields.length < 6 || fields.length > 7) {
    return 'tasks.cronHintFields'
  }
  if (!fields.every((field) => CRON_FIELD_PATTERN.test(field))) {
    return 'tasks.cronHintChars'
  }
  return null
}
