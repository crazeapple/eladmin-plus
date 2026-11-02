/** 图表 option 构造：纯函数注入色板与文案，偏好变化时由计算属性重算（不注册 ECharts 主题对象） */
import type {
  BarSeriesOption,
  LineSeriesOption,
  PieSeriesOption,
  RadarSeriesOption,
} from 'echarts/charts'
import type {
  GridComponentOption,
  LegendComponentOption,
  RadarComponentOption,
  TooltipComponentOption,
} from 'echarts/components'
import type { ComposeOption } from 'echarts/core'

import { primaryVariants } from '@/theme/colors'

export type ChartOption = ComposeOption<
  | LineSeriesOption
  | PieSeriesOption
  | BarSeriesOption
  | RadarSeriesOption
  | GridComponentOption
  | LegendComponentOption
  | TooltipComponentOption
  | RadarComponentOption
>

/** 图表色板：主色与变体槽位（取值随主色与深浅分支变化，公式复用主题模块） */
export interface ChartPalette {
  primary: string
  light3: string
  light5: string
  light7: string
  dark2: string
}

export function chartPalette(primaryColor: string, dark: boolean): ChartPalette {
  const variants = primaryVariants(primaryColor, dark)
  return {
    primary: variants['--el-color-primary'],
    light3: variants['--el-color-primary-light-3'],
    light5: variants['--el-color-primary-light-5'],
    light7: variants['--el-color-primary-light-7'],
    dark2: variants['--el-color-primary-dark-2'],
  }
}

/** 折线图：双系列（预期深变体、实际主色带面积） */
export function buildLineOption(params: {
  palette: ChartPalette
  labels: string[]
  names: [string, string]
  expected: number[]
  actual: number[]
}): ChartOption {
  return {
    color: [params.palette.dark2, params.palette.primary],
    tooltip: { trigger: 'axis' },
    legend: { data: [...params.names] },
    grid: { left: 10, right: 10, top: 30, bottom: 20, containLabel: true },
    xAxis: { type: 'category', boundaryGap: false, data: params.labels },
    yAxis: { type: 'value' },
    series: [
      { name: params.names[0], type: 'line', smooth: true, data: params.expected },
      {
        name: params.names[1],
        type: 'line',
        smooth: true,
        data: params.actual,
        areaStyle: { color: params.palette.light7 },
      },
    ],
  }
}

/** 饼图：环形，四槽位色板按数据顺序取色 */
export function buildPieOption(params: {
  palette: ChartPalette
  data: { name: string; value: number }[]
}): ChartOption {
  return {
    color: [
      params.palette.primary,
      params.palette.light3,
      params.palette.dark2,
      params.palette.light5,
    ],
    tooltip: { trigger: 'item' },
    legend: { data: params.data.map((item) => item.name) },
    series: [
      {
        type: 'pie',
        radius: ['42%', '68%'],
        center: ['50%', '54%'],
        avoidLabelOverlap: true,
        itemStyle: { borderRadius: 4 },
        label: { show: false },
        data: params.data,
      },
    ],
  }
}

/** 柱状图：两系列（主色、深变体） */
export function buildBarOption(params: {
  palette: ChartPalette
  labels: string[]
  series: { name: string; data: number[] }[]
}): ChartOption {
  return {
    color: [params.palette.primary, params.palette.dark2],
    tooltip: { trigger: 'axis' },
    legend: { data: params.series.map((item) => item.name) },
    grid: { left: 10, right: 10, top: 30, bottom: 20, containLabel: true },
    xAxis: { type: 'category', data: params.labels },
    yAxis: { type: 'value' },
    series: params.series.map((item) => ({ ...item, type: 'bar' as const, barGap: 0 })),
  }
}

/** 雷达图：单系列主色带面积填充 */
export function buildRadarOption(params: {
  palette: ChartPalette
  legend: string
  indicators: string[]
  max: number
  data: number[]
}): ChartOption {
  return {
    color: [params.palette.primary],
    legend: { data: [params.legend] },
    radar: {
      indicator: params.indicators.map((name) => ({ name, max: params.max })),
      radius: '62%',
      center: ['50%', '54%'],
    },
    series: [
      {
        type: 'radar',
        data: [
          { value: params.data, name: params.legend, areaStyle: { color: params.palette.light7 } },
        ],
      },
    ],
  }
}
