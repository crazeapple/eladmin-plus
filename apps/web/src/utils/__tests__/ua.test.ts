import { describe, expect, it } from 'vitest'

import { parseUa, truncateUa } from '../ua'

const CHROME_UA =
  'Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36'

describe('UA 解析封装（三列与失败兜底）', () => {
  it('标准 UA 解析出浏览器、版本、操作系统', () => {
    expect(parseUa(CHROME_UA)).toEqual({
      browser: 'Chrome',
      version: '120.0.0.0',
      os: 'macOS',
    })
  })

  it('非标准 UA（识别不出浏览器）原样截断兜底，其余列占位不空', () => {
    const result = parseUa('curl/8.4.0')
    expect(result.browser).toBe('curl/8.4.0')
    expect(result.version).toBe('—')
    expect(result.os).toBe('—')
  })

  it('超长兜底文本先截断（列内不爆长）', () => {
    const weird = 'x'.repeat(100)
    expect(truncateUa(weird)).toBe(`${'x'.repeat(40)}…`)
    expect(parseUa(weird).browser).toBe(`${'x'.repeat(40)}…`)
  })

  it('空值三列占位，不报错不空列', () => {
    expect(parseUa('')).toEqual({ browser: '—', version: '—', os: '—' })
    expect(parseUa('   ')).toEqual({ browser: '—', version: '—', os: '—' })
    expect(parseUa(null)).toEqual({ browser: '—', version: '—', os: '—' })
    expect(parseUa(undefined)).toEqual({ browser: '—', version: '—', os: '—' })
  })
})
