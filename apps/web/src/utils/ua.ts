import { UAParser } from 'ua-parser-js'

/**
 * UA 解析封装：浏览器、版本、操作系统三列（全量解析取 ua-parser-js）；
 * 非标准 UA（识别不出浏览器）或空值原样截断兜底，不空列不报错。
 */

/** 置空占位（与服务监控置空组占位同形） */
export const UA_UNKNOWN = '—'

/** UA 解析三列结果 */
export interface UaInfo {
  browser: string
  version: string
  os: string
}

/** 原样截断（列内溢出省略之外的长兜底文本先截，tooltip 由列层给全文） */
export function truncateUa(ua: string, length = 40): string {
  return ua.length > length ? `${ua.slice(0, length)}…` : ua
}

/** UA 解析三列 */
export function parseUa(ua: string | null | undefined): UaInfo {
  const raw = ua?.trim() ?? ''
  if (!raw) {
    return { browser: UA_UNKNOWN, version: UA_UNKNOWN, os: UA_UNKNOWN }
  }
  try {
    const { browser, os } = UAParser(raw)
    if (!browser.name) {
      // 识别不出浏览器视为解析失败：浏览器列原样截断兜底，其余占位
      return { browser: truncateUa(raw), version: UA_UNKNOWN, os: UA_UNKNOWN }
    }
    return {
      browser: browser.name,
      version: browser.version || UA_UNKNOWN,
      os: os.name || UA_UNKNOWN,
    }
  } catch {
    // 解析器抛错同样原样截断兜底
    return { browser: truncateUa(raw), version: UA_UNKNOWN, os: UA_UNKNOWN }
  }
}
