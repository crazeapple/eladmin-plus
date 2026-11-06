import { describe, expect, it } from 'vitest'
import sass from 'sass'

import loginVue from '../index.vue?raw'

// 登录页布局契约单测：与 tokens.test.ts 同语言——真编译 SFC 的 scoped 样式块，
// 断言分栏基数、表单卡尺寸间距、层次 token 在册与右区图案底（亮暗双态）。
const styleBlock = loginVue.match(/<style[^>]*>([\s\S]*?)<\/style>/)?.[1] ?? ''
// 注释剥离后再解析：注释内的「50:50」形态会被误读成声明、块前注释会挡住 at 规则头识别
const css = sass.compileString(styleBlock).css.replace(/\/\*[\s\S]*?\*\//g, '')

/** 编译产物压平为「选择器 → 声明表」；@media 内层规则选择器带媒体头前缀，防窄屏折叠规则盖掉基础规则 */
function rulesOf(source: string, prefix = ''): Map<string, Record<string, string>> {
  const rules = new Map<string, Record<string, string>>()
  let cursor = 0
  while (cursor < source.length) {
    const open = source.indexOf('{', cursor)
    if (open < 0) break
    const head = source.slice(cursor, open)
    const selector = head.slice(head.lastIndexOf(';') + 1).trim()
    // 花括号深度配对取块体（@media 内层规则不逃逸前缀）
    let depth = 1
    let close = open
    while (depth > 0 && close < source.length) {
      close += 1
      if (source[close] === '{') depth += 1
      if (source[close] === '}') depth -= 1
    }
    const body = source.slice(open + 1, close)
    if (selector.startsWith('@')) {
      for (const [inner, declarations] of rulesOf(body, `${selector} `)) {
        rules.set(inner, declarations)
      }
    } else {
      const declarations: Record<string, string> = {}
      for (const [, name, value] of body.matchAll(/(--[\w-]+|[\w-]+)\s*:\s*([^;]+);/g)) {
        declarations[name!] = value!.trim()
      }
      for (const part of selector.split(',')) {
        rules.set(`${prefix}${part.trim()}`, declarations)
      }
    }
    cursor = close + 1
  }
  return rules
}

const rules = rulesOf(css)

/** 声明值里的 px 数值（取第一个） */
function pxOf(value: string | undefined): number {
  const match = value?.match(/(\d+(?:\.\d+)?)px/)
  if (!match) throw new Error(`声明值不含 px 数值：${value}`)
  return Number(match[1])
}

describe('登录页布局契约', () => {
  it('分栏对称基数 50:50（品牌区与表单区互为等宽）', () => {
    expect(rules.get('.login__brand')?.flex).toBe('50')
    expect(rules.get('.login__panel')?.flex).toBe('50')
  })

  it('表单卡宽 400px，卡内边距 28px，元素垂直间距 ≥18px', () => {
    const card = rules.get('.login__card')
    expect(card?.width).toBe('400px')
    expect(pxOf(card?.['--el-card-padding'])).toBeGreaterThanOrEqual(28)
    expect(pxOf(card?.gap)).toBeGreaterThanOrEqual(18)
  })

  it('表单卡层次 token 在册（卡片底 / 边框 / 圆角 / 重阴影）', () => {
    const card = rules.get('.login__card')
    expect(card?.['background-color']).toContain('var(--ea-card-bg')
    expect(card?.border).toContain('var(--ea-card-border')
    expect(card?.['border-radius']).toContain('var(--ea-radius-lg')
    expect(card?.['box-shadow']).toContain('var(--ea-shadow-3')
  })

  it('右区几何图案底在册（品牌主色点阵 + 网格程序化平铺，透明度守 4%~8%）', () => {
    const panelImage = rules.get('.login__panel')?.['background-image'] ?? ''
    // 点阵（radial）与网格（linear）双层同在，色值走品牌主色 token
    expect(panelImage).toContain('radial-gradient')
    expect(panelImage).toContain('linear-gradient')
    expect(panelImage).toContain('var(--ea-primary')
    // 极淡档：声明里的透明度百分数全部落在 4%~8%
    const alphas = [...panelImage.matchAll(/(\d+)%/g)].map((m) => Number(m[1]))
    expect(alphas.length).toBeGreaterThan(0)
    for (const alpha of alphas) {
      expect(alpha).toBeGreaterThanOrEqual(4)
      expect(alpha).toBeLessThanOrEqual(8)
    }
  })

  it('图案底亮暗双态：暗色分支独立成规且提亮不越档', () => {
    const darkImage = rules.get('html.dark .login__panel')?.['background-image'] ?? ''
    expect(darkImage).toContain('radial-gradient')
    // 暗色与亮色取不同透明度配比（双态值表不重合）
    expect(darkImage).not.toBe(rules.get('.login__panel')?.['background-image'])
    for (const alpha of [...darkImage.matchAll(/(\d+)%/g)].map((m) => Number(m[1]))) {
      expect(alpha).toBeGreaterThanOrEqual(4)
      expect(alpha).toBeLessThanOrEqual(8)
    }
  })
})
