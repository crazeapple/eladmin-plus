import { describe, expect, it } from 'vitest'
import sass from 'sass'

import loginVue from '../index.vue?raw'

// 登录页布局契约单测：与 tokens.test.ts 同语言——真编译 SFC 的 scoped 样式块，
// 断言整页背景图、单栏居中形态、表单卡尺寸间距、氛围遮罩与 token 化标语（亮暗双态收敛在 tokens 层）。
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
        const name = `${prefix}${part.trim()}`
        // 同名选择器层叠合并（后者覆盖同键）——贴近浏览器级联语义，组合选择器分块声明才可读全
        rules.set(name, { ...(rules.get(name) ?? {}), ...declarations })
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
  it('整页容器居中布局，品牌标识入卡、主区垂直水平居中', () => {
    const page = rules.get('.login')
    expect(page?.display).toBe('flex')
    expect(page?.['background-color']).toContain('var(--ea-login-base')
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
    // 间距落在 el-form 层（卡 body 唯一子是 form）
    const formRule = rules.get('.login__form')
    expect(pxOf(formRule?.gap)).toBeGreaterThanOrEqual(18)
    expect(formRule?.['flex-direction']).toBe('column')
  })

  it('表单卡层次 token 在册（卡片底 / 边框 / 圆角 / 重阴影）', () => {
    const card = rules.get('.login__card')
    expect(card?.['background-color']).toContain('var(--ea-card-bg')
    expect(card?.border).toContain('var(--ea-card-border')
    expect(card?.['border-radius']).toContain('var(--ea-radius-lg')
    expect(card?.['box-shadow']).toContain('var(--ea-shadow-3')
  })

  it('粒子背景图经伪元素铺满整页，左右区全透明叠加', () => {
    // 背景图在双伪元素上（cover 居中），容器持底色兜底，左右区透明、文字靠图面明区与字色 token 保可读
    const page = rules.get('.login')
    expect(page?.['background-color']).toContain('var(--ea-login-base')
    for (const layer of ['.login::before', '.login::after']) {
      expect(rules.get(layer)?.['background-size']).toBe('cover')
      expect(rules.get(layer)?.['background-position']).toBe('center')
    }
    expect(rules.get('.login__brand')?.['background']).toBeUndefined()
    expect(rules.get('.login__panel')?.['background-image']).toBeUndefined()
    // 左区装饰退役（背景图即视觉主体）
    expect(rules.get('.login__decor')).toBeUndefined()
    // 卡浮于背景图之上
    expect(rules.get('.login__card')?.['z-index']).toBe('1')
  })

  it('背景图亮暗双版在册：swap token 交叉切换，内容浮于图版上', () => {
    // 亮版补透明度、暗版直取 swap——两态同一对素材，切换即交叉淡化
    const lightLayer = rules.get('.login::before')
    const darkLayer = rules.get('.login::after')
    expect(lightLayer?.['background-image']).toContain('login-fabric-light')
    expect(darkLayer?.['background-image']).toContain('login-fabric.jpg')
    expect(lightLayer?.opacity).toContain('calc(1 - var(--ea-login-photo-swap')
    expect(darkLayer?.opacity).toContain('var(--ea-login-photo-swap')
    // 标语字色与阴影走 token（亮态深字无阴影、暗态白字带阴影，分支在 tokens 层）
    const slogan = rules.get('.login__slogan')
    expect(slogan?.color).toContain('var(--ea-login-hero-fg')
    expect(slogan?.['text-shadow']).toContain('var(--ea-login-hero-shadow')
    expect(rules.get('.login__slogan-sub')?.color).toContain('var(--ea-login-hero-sub-fg')
    // 主区浮于背景图版之上
    expect(rules.get('.login__main')?.['z-index']).toBe('1')
    // 组件样式零暗色分支（亮暗双态收敛在 tokens 层）
    expect(css).not.toContain('html.dark')
  })
})
