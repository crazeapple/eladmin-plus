/** ECharts 按需注册：集中一处，只注册项目用到的图表与组件 */
import { BarChart, LineChart, PieChart, RadarChart } from 'echarts/charts'
import { GridComponent, LegendComponent, TooltipComponent } from 'echarts/components'
import { CanvasRenderer } from 'echarts/renderers'
import { use } from 'echarts/core'

/** 注册集导出供测试整核对（echarts 运行时查不到已注册清单） */
export const registeredModules = [
  LineChart,
  PieChart,
  BarChart,
  RadarChart,
  GridComponent,
  TooltipComponent,
  LegendComponent,
  CanvasRenderer,
]

use(registeredModules)
