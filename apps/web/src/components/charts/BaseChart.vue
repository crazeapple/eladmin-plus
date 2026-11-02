<script setup lang="ts">
import VChart from 'vue-echarts'

import type { ChartOption } from './options'
import './register'

defineOptions({ name: 'BaseChart' })

defineProps<{ option: ChartOption }>()
</script>

<template>
  <VChart class="base-chart" :option="option" autoresize />
</template>

<style scoped lang="scss">
.base-chart {
  width: 100%;
  height: 100%;
}
</style>
