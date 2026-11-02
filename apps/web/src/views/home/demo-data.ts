/** 首页演示数据：数值全部前端静态；标签只存 i18n key，渲染时经 t() 解析 */
export type CardKey = 'users' | 'online' | 'visits' | 'logs'

export interface LineDataset {
  expected: number[]
  actual: number[]
}

/** 折线数据集：键与四卡片一一对应（点卡片切换） */
export const lineDatasets: Record<CardKey, LineDataset> = {
  users: {
    expected: [100, 120, 161, 134, 105, 160, 165],
    actual: [120, 82, 91, 154, 162, 140, 145],
  },
  online: {
    expected: [200, 192, 120, 144, 160, 130, 140],
    actual: [180, 160, 151, 106, 145, 150, 130],
  },
  visits: {
    expected: [80, 100, 121, 104, 105, 90, 100],
    actual: [120, 90, 100, 138, 142, 130, 130],
  },
  logs: {
    expected: [130, 140, 141, 142, 145, 150, 160],
    actual: [120, 82, 91, 154, 162, 140, 130],
  },
}

export const weekdayKeys = [
  'home.weekday.mon',
  'home.weekday.tue',
  'home.weekday.wed',
  'home.weekday.thu',
  'home.weekday.fri',
  'home.weekday.sat',
  'home.weekday.sun',
] as const

export const radarDemo = {
  legendKey: 'home.chart.radarLegend',
  indicatorKeys: [
    'home.chart.radarSales',
    'home.chart.radarAdmin',
    'home.chart.radarTech',
    'home.chart.radarSupport',
    'home.chart.radarResearch',
    'home.chart.radarMarketing',
  ],
  max: 200,
  data: [160, 130, 150, 120, 180, 140],
}

export const pieDemo = [
  { nameKey: 'home.chart.pieDirect', value: 320 },
  { nameKey: 'home.chart.pieEmail', value: 302 },
  { nameKey: 'home.chart.pieUnion', value: 251 },
  { nameKey: 'home.chart.pieVideo', value: 147 },
]

export const barDemo = {
  series: [
    { nameKey: 'home.chart.barSales', data: [110, 130, 101, 134, 90, 230, 210] },
    { nameKey: 'home.chart.barOrders', data: [120, 102, 141, 121, 132, 191, 154] },
  ],
}
