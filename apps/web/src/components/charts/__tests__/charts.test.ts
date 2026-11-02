import { BarChart, LineChart, PieChart, RadarChart } from 'echarts/charts'
import { GridComponent, LegendComponent, TooltipComponent } from 'echarts/components'
import { CanvasRenderer } from 'echarts/renderers'
import { describe, expect, it } from 'vitest'

import {
  buildBarOption,
  buildLineOption,
  buildPieOption,
  buildRadarOption,
  chartPalette,
} from '../options'
import { registeredModules } from '../register'

/** option 字段类型上可单对象或数组（Arrayable）——统一收成数组形态断言 */
function asArray<T>(value: T | T[] | undefined): T[] {
  return value === undefined ? [] : Array.isArray(value) ? value : [value]
}

describe('按需注册', () => {
  it('注册集只含所用图表、组件与渲染器', () => {
    expect(registeredModules).toHaveLength(8)
    for (const module of [
      LineChart,
      PieChart,
      BarChart,
      RadarChart,
      GridComponent,
      TooltipComponent,
      LegendComponent,
      CanvasRenderer,
    ]) {
      expect(registeredModules).toContain(module)
    }
  })
})

describe('图表色板', () => {
  it('浅色分支：主色变体取 EP 官方浅色值', () => {
    expect(chartPalette('#409EFF', false)).toEqual({
      primary: '#409eff',
      light3: '#79bbff',
      light5: '#a0cfff',
      light7: '#c6e2ff',
      dark2: '#337ecc',
    })
  })

  it('深色分支：变体向暗底反转混合，dark-2 向白', () => {
    expect(chartPalette('#409EFF', true)).toEqual({
      primary: '#409eff',
      light3: '#3375b9',
      light5: '#2a598a',
      light7: '#213d5b',
      dark2: '#66b1ff',
    })
  })
})

describe('option 构造', () => {
  const palette = chartPalette('#409EFF', false)

  it('折线：双系列、图例与轴随入参、根色板两槽位', () => {
    const option = buildLineOption({
      palette,
      labels: ['周一', '周二'],
      names: ['预期', '实际'],
      expected: [10, 20],
      actual: [30, 40],
    })
    expect(option.color).toEqual(['#337ecc', '#409eff'])
    expect(asArray(option.legend)[0]?.data).toEqual(['预期', '实际'])
    expect(option.xAxis).toMatchObject({
      type: 'category',
      boundaryGap: false,
      data: ['周一', '周二'],
    })
    const series = asArray(option.series)
    expect(series).toHaveLength(2)
    expect(series[0]).toMatchObject({ name: '预期', type: 'line', smooth: true, data: [10, 20] })
    expect(series[1]).toMatchObject({
      name: '实际',
      type: 'line',
      data: [30, 40],
      areaStyle: { color: '#c6e2ff' },
    })
  })

  it('饼图：环形系列、图例随数据名、根色板四槽位', () => {
    const option = buildPieOption({
      palette,
      data: [
        { name: '直接', value: 320 },
        { name: '邮件', value: 302 },
        { name: '联盟', value: 251 },
        { name: '视频', value: 147 },
      ],
    })
    expect(option.color).toEqual(['#409eff', '#79bbff', '#337ecc', '#a0cfff'])
    expect(asArray(option.legend)[0]?.data).toEqual(['直接', '邮件', '联盟', '视频'])
    const series = asArray(option.series)
    expect(series).toHaveLength(1)
    expect(series[0]).toMatchObject({ type: 'pie' })
  })

  it('柱状：系列随入参映射为柱类型、根色板两槽位', () => {
    const option = buildBarOption({
      palette,
      labels: ['周一', '周二'],
      series: [
        { name: '销量', data: [110, 130] },
        { name: '数量', data: [90, 100] },
      ],
    })
    expect(option.color).toEqual(['#409eff', '#337ecc'])
    const series = asArray(option.series)
    expect(series).toHaveLength(2)
    expect(series[0]).toMatchObject({ name: '销量', type: 'bar', data: [110, 130] })
    expect(series[1]).toMatchObject({ name: '数量', type: 'bar', data: [90, 100] })
  })

  it('雷达：指标随入参、单系列带面积、根色板主色单槽位', () => {
    const option = buildRadarOption({
      palette,
      legend: '预算分配',
      indicators: ['销售', '管理', '技术', '客服', '研发'],
      max: 200,
      data: [160, 130, 150, 120, 180],
    })
    expect(option.color).toEqual(['#409eff'])
    expect(asArray(option.legend)[0]?.data).toEqual(['预算分配'])
    expect(asArray(option.radar)[0]?.indicator).toEqual([
      { name: '销售', max: 200 },
      { name: '管理', max: 200 },
      { name: '技术', max: 200 },
      { name: '客服', max: 200 },
      { name: '研发', max: 200 },
    ])
    expect(asArray(option.series)[0]).toMatchObject({
      type: 'radar',
      data: [
        { value: [160, 130, 150, 120, 180], name: '预算分配', areaStyle: { color: '#c6e2ff' } },
      ],
    })
  })
})
