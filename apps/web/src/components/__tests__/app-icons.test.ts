import { describe, expect, it } from 'vitest'

import { appIconSvg, hasAppIcon } from '../app-icons'

// 消毒管线 viewBox 注入：资产库全量在册图形必须自带坐标系，缺者按宽高注入——
// 否则 1em 盒缩放渲染时内容不缩放、只露坐标系左上角（侧栏隐形/残块缺陷类）
describe('app-icons 消毒管线', () => {
  it('全量在册资产消毒后 svg 开标签均带 viewBox', () => {
    const names = Object.keys(
      import.meta.glob<string>('../../assets/icons/*.svg', { eager: true }),
    ).map((p) =>
      p
        .split('/')
        .pop()!
        .replace(/\.svg$/, ''),
    )
    expect(names.length).toBeGreaterThan(0)
    const missing = names.filter((name) => {
      const svg = appIconSvg(name)
      return !svg || !/viewBox="/.test(svg)
    })
    expect(missing).toEqual([])
  })

  it('非方形资产 viewBox 按原宽高注入（内容不变形）', () => {
    expect(hasAppIcon('people')).toBe(true)
    const svg = appIconSvg('people')!
    expect(svg).toMatch(/viewBox="0 0 128 128"/)
  })
})
