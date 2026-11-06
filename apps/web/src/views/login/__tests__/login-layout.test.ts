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
  it('背景图铺满整页，品牌标识入卡、主区垂直水平居中', () => {
    const page = rules.get('.login')
    expect(page?.display).toBe('flex')
    expect(page?.['background-image']).toContain('login-fabric')
    expect(page?.['background-size']).toBe('cover')
    const mark = rules.get('.login__mark')
    expect(mark?.color).toContain('var(--el-text-color-primary)')
    expect(mark?.['text-align']).toBe('center')
    const main = rules.get('.login__main')
    expect(main?.['align-items']).toBe('center')
    expect(main?.['justify-content']).toBe('center')
  })

  it('表单卡宽 400px，卡内边距 28px，元素垂直间距 ≥18px', () => {
    const card = rules.get('.login__card')
    expect(card?.width).toBe('400px')
    expect(pxOf(card?.['--el-card-padding'])).toBeGreaterThanOrEqual(28)
    // 间距落在 EP 卡片内容区（body 层）——卡根设 gap 只作用于唯一子元素不生效
    const bodyRule = rules.get('.login__card :deep(.el-card__body)')
    expect(pxOf(bodyRule?.gap)).toBeGreaterThanOrEqual(18)
    expect(bodyRule?.['flex-direction']).toBe('column')
  })

  it('表单卡层次 token 在册（卡片底 / 边框 / 圆角 / 重阴影）', () => {
    const card = rules.get('.login__card')
    expect(card?.['background-color']).toContain('var(--ea-card-bg')
    expect(card?.border).toContain('var(--ea-card-border')
    expect(card?.['border-radius']).toContain('var(--ea-radius-lg')
    expect(card?.['box-shadow']).toContain('var(--ea-shadow-3')
  })

  it('粒子背景图铺满整页，左右区全透明叠加', () => {
    // 背景图在整页容器（cover 居中），左右区透明、文字靠图面暗区与文字阴影保可读
    const page = rules.get('.login')
    expect(page?.['background-image']).toContain('login-fabric')
    expect(page?.['background-size']).toBe('cover')
    expect(page?.['background-position']).toBe('center')
    expect(rules.get('.login__brand')?.['background']).toBeUndefined()
    expect(rules.get('.login__panel')?.['background-image']).toBeUndefined()
    // 文字阴影在册（可读性增强且不遮背景图）
    expect(rules.get('.login__slogan')?.['text-shadow']).toContain('rgb')
    // 左区装饰退役（背景图即视觉主体）
    expect(rules.get('.login__decor')).toBeUndefined()
    // 卡浮于背景图之上
    expect(rules.get('.login__card')?.['z-index']).toBe('1')
  })
})
