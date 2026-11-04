/** label 行 ↔ JSON 互转（纯函数零组件依赖）：组装保证合法 JSON，空语言行不写入 */
export function buildLabelJson(rows: Record<string, string>): string {
  const result: Record<string, string> = {}
  for (const [locale, text] of Object.entries(rows)) {
    const trimmed = text.trim()
    if (trimmed) {
      result[locale] = trimmed
    }
  }
  return JSON.stringify(result)
}

/** label JSON 解析为行（非法或非对象安全回退空对象；展示回退由消费端处理） */
export function parseLabelJson(label: string | null | undefined): Record<string, string> {
  if (!label) {
    return {}
  }
  try {
    const parsed: unknown = JSON.parse(label)
    if (typeof parsed === 'object' && parsed !== null && !Array.isArray(parsed)) {
      return parsed as Record<string, string>
    }
  } catch {
    // 非法 JSON 由后端 422 权威直显，前端展示安全回退空行
  }
  return {}
}
