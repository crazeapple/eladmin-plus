/** 查询串构造：空值字段不入串（分页字段恒在）；分页列表 GET 端点共用 */
export function queryString(params: Record<string, string | number | undefined>): string {
  const qs = new URLSearchParams()
  for (const [key, value] of Object.entries(params)) {
    if (value !== undefined && value !== '') {
      qs.set(key, String(value))
    }
  }
  return qs.toString()
}
