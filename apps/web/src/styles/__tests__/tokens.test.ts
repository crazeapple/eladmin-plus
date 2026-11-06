import { describe, expect, it } from 'vitest'
import sass from 'sass'

import tokensScss from '../tokens.scss?raw'

// token 值表单测：用 dart-sass 真编译 tokens.scss，断言亮暗双态值表在册与覆盖层映射
const css = sass.compileString(tokensScss).css

/** 从一段选择器块里抽取全部自定义属性声明 */
function declarationsOf(block: string): Record<string, string> {
  const map: Record<string, string> = {}
  for (const [, name, value] of block.matchAll(/(--[\w-]+)\s*:\s*([^;]+);/g)) {
    map[name!] = value!.trim()
  }
  return map
}

function blockOf(selector: string): string {
  const start = css.indexOf(`${selector} {`)
  expect(start, `选择器 ${selector} 应在编译产物中`).toBeGreaterThanOrEqual(0)
  const end = css.indexOf('}', start)
  return css.slice(start, end)
}

const light = declarationsOf(blockOf(':root'))
const dark = declarationsOf(blockOf('html.dark'))

describe('品牌 token 值表', () => {
  it('主色为品牌靛蓝基值，亮暗一致', () => {
    expect(light['--ea-primary']).toBe('#5d87ff')
    expect(dark['--ea-primary']).toBe('#5d87ff')
  })

  it('圆角档位三档在册（px 值）', () => {
    const tiers = ['--ea-radius-sm', '--ea-radius-md', '--ea-radius-lg']
    for (const name of tiers) {
      expect(light[name], `亮色 ${name} 应在册`).toMatch(/^\d+px$/)
      expect(dark[name], `暗色 ${name} 应在册`).toMatch(/^\d+px$/)
    }
  })

  it('阴影层级三档在册且暗色取暗色值', () => {
    for (const name of ['--ea-shadow-1', '--ea-shadow-2', '--ea-shadow-3']) {
      expect(light[name], `亮色 ${name} 应在册`).toBeTruthy()
      expect(dark[name], `暗色 ${name} 应在册`).toBeTruthy()
    }
    // 暗色阴影加重：值表不与亮色相同
    expect(dark['--ea-shadow-2']).not.toBe(light['--ea-shadow-2'])
  })

  it('渐变 token 在册且为渐变值', () => {
    for (const name of ['--ea-gradient-primary', '--ea-gradient-brand']) {
      expect(light[name]).toContain('linear-gradient')
      expect(dark[name]).toContain('linear-gradient')
    }
    // 暗色品牌渐变换明度配比：与亮色不同值
    expect(dark['--ea-gradient-brand']).not.toBe(light['--ea-gradient-brand'])
  })

  it('卡片层次与布局语义 token 在册', () => {
    for (const name of [
      '--ea-card-bg',
      '--ea-card-border',
      '--ea-card-shadow',
      '--ea-page-bg',
      '--ea-layout-sidebar-width',
      '--ea-layout-sidebar-collapsed-width',
      '--ea-layout-header-height',
      '--ea-layout-content-padding',
      '--ea-sidebar-bg',
      '--ea-sidebar-active-bg',
      '--ea-header-bg',
      '--ea-mask-bg',
    ]) {
      expect(light[name], `亮色 ${name} 应在册`).toBeTruthy()
      expect(dark[name], `暗色 ${name} 应在册`).toBeTruthy()
    }
    // 卡片底与侧栏底暗色取暗色值（无亮色残留）
    expect(dark['--ea-card-bg']).not.toBe(light['--ea-card-bg'])
    expect(dark['--ea-sidebar-bg']).not.toBe(light['--ea-sidebar-bg'])
    expect(dark['--ea-page-bg']).not.toBe(light['--ea-page-bg'])
  })
})

describe('EP 覆盖层映射', () => {
  it('EP 主色缺省映射到品牌主色变量', () => {
    expect(light['--el-color-primary']).toBe('var(--ea-primary)')
  })

  it('EP 圆角与阴影映射到品牌档位', () => {
    expect(light['--el-border-radius-base']).toBe('var(--ea-radius-sm)')
    expect(light['--el-border-radius-round']).toBe('var(--ea-radius-lg)')
    expect(light['--el-box-shadow-light']).toBe('var(--ea-shadow-2)')
    expect(light['--el-box-shadow-dark']).toBe('var(--ea-shadow-3)')
  })
})
