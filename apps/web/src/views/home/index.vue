<script setup lang="ts">
import { computed, ref } from 'vue'
import { useI18n } from 'vue-i18n'
import { usePreferredDark } from '@vueuse/core'

import BaseChart from '@/components/charts/BaseChart.vue'
import {
  buildBarOption,
  buildLineOption,
  buildPieOption,
  buildRadarOption,
  chartPalette,
} from '@/components/charts/options'
import { useOverviewStatsQuery } from '@/api/stats'
import { usePreferencesStore } from '@/stores/preferences'
import { resolveDarkness } from '@/theme'
import { barDemo, lineDatasets, pieDemo, radarDemo, weekdayKeys, type CardKey } from './demo-data'
import StatCards from './components/StatCards.vue'

defineOptions({ name: 'Home' })

const { t } = useI18n()

// 卡片数字消费统计端点（占位契约，真实数据仅换后端实现）；图表仍前端静态演示
const { data, isError, refetch } = useOverviewStatsQuery()

// 契约 int64 在 zod 侧为 coerce bigint（字段可缺省）：视图边界统一转 number，缺省保持 undefined 显占位符
const toCount = (value: bigint | undefined): number | undefined =>
  value === undefined ? undefined : Number(value)

const stats = computed(() => {
  const source = data.value
  return source
    ? {
        users: toCount(source.userCount),
        online: toCount(source.onlineCount),
        visits: toCount(source.todayVisitCount),
        logs: toCount(source.logCount),
      }
    : {}
})

// 色板随偏好即时重算（主色与深浅，auto 随系统），option 为计算属性——图表实例不重建
const prefs = usePreferencesStore()
const prefersDark = usePreferredDark()
const palette = computed(() =>
  chartPalette(prefs.primaryColor, resolveDarkness(prefs.theme, prefersDark.value)),
)
const selectedKey = ref<CardKey>('users')

const lineOption = computed(() => {
  const dataset = lineDatasets[selectedKey.value]
  return buildLineOption({
    palette: palette.value,
    labels: weekdayKeys.map((key) => t(key)),
    names: [t('home.chart.expected'), t('home.chart.actual')],
    expected: dataset.expected,
    actual: dataset.actual,
  })
})

const radarOption = computed(() =>
  buildRadarOption({
    palette: palette.value,
    legend: t(radarDemo.legendKey),
    indicators: radarDemo.indicatorKeys.map((key) => t(key)),
    max: radarDemo.max,
    data: radarDemo.data,
  }),
)

const pieOption = computed(() =>
  buildPieOption({
    palette: palette.value,
    data: pieDemo.map((item) => ({ name: t(item.nameKey), value: item.value })),
  }),
)

const barOption = computed(() =>
  buildBarOption({
    palette: palette.value,
    labels: weekdayKeys.map((key) => t(key)),
    series: barDemo.series.map((item) => ({ name: t(item.nameKey), data: item.data })),
  }),
)
</script>

<template>
  <div class="home" data-test="home-dashboard">
    <div v-if="isError" class="home__error" data-test="stats-error">
      <span>{{ t('home.statsError') }}</span>
      <el-button size="small" data-test="stats-retry" @click="refetch()">
        {{ t('common.retry') }}
      </el-button>
    </div>

    <StatCards :stats="stats" @select="selectedKey = $event" />

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

.home__error {
  display: flex;
  align-items: center;
  gap: 12px;
  margin-bottom: 16px;
  padding: 8px 16px;
  color: var(--el-color-danger);
  background: var(--el-color-danger-light-9);
  border-radius: 4px;
}

.home__chart {
  height: 320px;
  margin-bottom: 16px;
  padding: 16px 16px 8px;
  background: var(--el-bg-color);
  border-radius: 8px;
}
</style>
