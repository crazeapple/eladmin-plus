import { describe, expect, it } from 'vitest'

import { formatDateTime } from '../datetime'

describe('时间列本地化格式', () => {
  it('ISO 本地时间串转 yyyy-MM-dd HH:mm:ss（T 换空格并补秒）', () => {
    // 无时区后缀按本地时区解析，墙钟时间原样保留
    expect(formatDateTime('2025-01-02T10:30:00')).toBe('2025-01-02 10:30:00')
    // 分秒缺省补零
    expect(formatDateTime('2025-01-02T09:05:03')).toBe('2025-01-02 09:05:03')
  })

  it('空值与非法值返回空串', () => {
    expect(formatDateTime(undefined)).toBe('')
    expect(formatDateTime(null)).toBe('')
    expect(formatDateTime('')).toBe('')
    expect(formatDateTime('not-a-date')).toBe('')
  })

  it('Date 与毫秒数输入同样成格式', () => {
    const date = new Date(2025, 0, 2, 10, 30, 0)
    expect(formatDateTime(date)).toBe('2025-01-02 10:30:00')
    expect(formatDateTime(date.getTime())).toBe('2025-01-02 10:30:00')
  })
})
