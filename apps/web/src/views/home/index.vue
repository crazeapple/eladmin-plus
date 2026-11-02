<script setup lang="ts">
import { computed, ref } from 'vue'
import { useI18n } from 'vue-i18n'

import BaseChart from '@/components/charts/BaseChart.vue'
import {
  buildBarOption,
  buildLineOption,
  buildPieOption,
  buildRadarOption,
  chartPalette,
  type ChartPalette,
} from '@/components/charts/options'
import { barDemo, lineDatasets, pieDemo, radarDemo, weekdayKeys, type CardKey } from './demo-data'
import StatCards from './components/StatCards.vue'

defineOptions({ name: 'Home' })

const { t } = useI18n()

// 演示态：数字与色板为静态默认值——端点消费与偏好联动随后任务接线
const stats = { users: 10240, online: 321, visits: 8123, logs: 2048 }
const palette: ChartPalette = chartPalette('#409eff', false)
const selectedKey = ref<CardKey>('users')

const lineOption = computed(() => {
  const dataset = lineDatasets[selectedKey.value]
  return buildLineOption({
    palette,
    labels: weekdayKeys.map((key) => t(key)),
    names: [t('home.chart.expected'), t('home.chart.actual')],
    expected: dataset.expected,
    actual: dataset.actual,
  })
})

const radarOption = computed(() =>
  buildRadarOption({
    palette,
    legend: t(radarDemo.legendKey),
    indicators: radarDemo.indicatorKeys.map((key) => t(key)),
    max: radarDemo.max,
    data: radarDemo.data,
  }),
)

const pieOption = computed(() =>
  buildPieOption({
    palette,
    data: pieDemo.map((item) => ({ name: t(item.nameKey), value: item.value })),
  }),
)

const barOption = computed(() =>
  buildBarOption({
    palette,
    labels: weekdayKeys.map((key) => t(key)),
    series: barDemo.series.map((item) => ({ name: t(item.nameKey), data: item.data })),
  }),
)
</script>

<template>
  <div class="home" data-test="home-dashboard">
    <StatCards :stats="stats" />

    <div class="home__chart home__chart--wide" data-test="chart-line">
      <BaseChart :option="lineOption" />
    </div>

    <el-row :gutter="16">
      <el-col :xs="24" :sm="24" :lg="8">
        <div class="home__chart" data-test="chart-radar">
          <BaseChart :option="radarOption" />
        </div>
      </el-col>
      <el-col :xs="24" :sm="24" :lg="8">
        <div class="home__chart" data-test="chart-pie">
          <BaseChart :option="pieOption" />
        </div>
      </el-col>
      <el-col :xs="24" :sm="24" :lg="8">
        <div class="home__chart" data-test="chart-bar">
          <BaseChart :option="barOption" />
        </div>
      </el-col>
    </el-row>
  </div>
</template>

<style scoped lang="scss">
.home {
  padding: 16px;
}

.home__chart {
  height: 320px;
  margin-bottom: 16px;
  padding: 16px 16px 8px;
  background: var(--el-bg-color);
  border-radius: 8px;
}
</style>
