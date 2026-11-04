<script setup lang="ts">
import { ElButton } from 'element-plus'
import { usePreferredDark } from '@vueuse/core'
import { computed, onMounted, onUnmounted } from 'vue'
import { useI18n } from 'vue-i18n'

import BaseChart from '@/components/charts/BaseChart.vue'
import { buildGaugeOption, chartPalette } from '@/components/charts/options'
import { useServerMonitor } from '@/api/monitor'
import { usePreferencesStore } from '@/stores/preferences'
import { resolveDarkness } from '@/theme'

defineOptions({ name: 'MonitorServerInfo' })

const { t } = useI18n()

/**
 * 服务监控页：指标轮询（10 秒）+ 手动刷新共用同一取数函数（refetch）；
 * 单次轮询失败静默（retry 已关，不弹错误风暴），下一轮继续；离开页面销毁定时器。
 */
const { data, isFetching, refetch } = useServerMonitor()

const POLL_INTERVAL_MS = 10_000

let timer: ReturnType<typeof setInterval> | null = null

/** 手动刷新与轮询共用：refetch 即取数函数 */
async function refresh() {
  await refetch()
}

onMounted(() => {
  timer = setInterval(() => {
    void refresh()
  }, POLL_INTERVAL_MS)
})

onUnmounted(() => {
  if (timer) {
    clearInterval(timer)
    timer = null
  }
})

const view = computed(() => data.value ?? null)

// 色板随偏好即时重算（与首页同接线），option 为计算属性——图表实例不重建
const prefs = usePreferencesStore()
const prefersDark = usePreferredDark()
const palette = computed(() =>
  chartPalette(prefs.primaryColor, resolveDarkness(prefs.theme, prefersDark.value)),
)

const EMPTY = '—'

const BYTE_UNITS = ['B', 'KB', 'MB', 'GB', 'TB', 'PB']

function formatBytes(value: number | undefined | null): string {
  if (value === undefined || value === null) {
    return EMPTY
  }
  let size = value
  let unit = 0
  while (size >= 1024 && unit < BYTE_UNITS.length - 1) {
    size /= 1024
    unit += 1
  }
  return `${unit === 0 || size >= 100 ? Math.round(size) : size.toFixed(1)} ${BYTE_UNITS[unit]}`
}

/** —— 三环形仪表（CPU / 内存 / JVM 堆），置空组整卡占位 —— */

const cpuOption = computed(() => {
  const cpu = view.value?.cpu
  return cpu
    ? buildGaugeOption({
        palette: palette.value,
        name: t('serverInfo.cpu'),
        percent: cpu.usedPercent ?? 0,
      })
    : null
})

const cpuPercent = computed(() => {
  const percent = view.value?.cpu?.usedPercent
  return percent === undefined || percent === null ? null : `${percent.toFixed(1)}%`
})

const memoryOption = computed(() => {
  const memory = view.value?.memory
  return memory
    ? buildGaugeOption({
        palette: palette.value,
        name: t('serverInfo.memory'),
        percent: memory.usedPercent ?? 0,
      })
    : null
})

const memoryPercent = computed(() => {
  const percent = view.value?.memory?.usedPercent
  return percent === undefined || percent === null ? null : `${percent.toFixed(1)}%`
})

const memorySub = computed(() => {
  const memory = view.value?.memory
  return memory
    ? t('serverInfo.usedOfTotal', {
        used: formatBytes(memory.usedBytes),
        total: formatBytes(memory.totalBytes),
      })
    : ''
})

const jvmHeapPercent = computed(() => {
  const jvm = view.value?.jvm
  if (!jvm || jvm.heapMaxBytes === null || jvm.heapMaxBytes === undefined) {
    return null
  }
  const percent = ((jvm.heapUsedBytes ?? 0) / jvm.heapMaxBytes) * 100
  return `${percent.toFixed(1)}%`
})

const jvmOption = computed(() => {
  const jvm = view.value?.jvm
  if (!jvm || jvmHeapPercent.value === null) {
    return null
  }
  return buildGaugeOption({
    palette: palette.value,
    name: t('serverInfo.jvmHeap'),
    percent: Number.parseFloat(jvmHeapPercent.value),
  })
})

const jvmSub = computed(() => {
  const jvm = view.value?.jvm
  return jvm
    ? `${t('serverInfo.usedOfTotal', {
        used: formatBytes(jvm.heapUsedBytes),
        total: formatBytes(jvm.heapMaxBytes),
      })} · ${t('serverInfo.threads', { count: jvm.threadCount ?? EMPTY })}`
    : ''
})

/** —— 卡片（Swap / 磁盘 / 连接池）：进度条 + 明细行，置空占位 —— */

const swapPercent = computed(() => {
  const swap = view.value?.swap
  if (!swap || !swap.totalBytes) {
    return null
  }
  return Math.round(((swap.usedBytes ?? 0) / swap.totalBytes) * 100)
})

const swapSub = computed(() => {
  const swap = view.value?.swap
  return swap
    ? t('serverInfo.usedOfTotal', {
        used: formatBytes(swap.usedBytes),
        total: formatBytes(swap.totalBytes),
      })
    : ''
})

interface DiskRow {
  mount: string
  percent: number | null
  sub: string
}

const diskRows = computed<DiskRow[]>(() => {
  const disks = view.value?.disks
  if (!disks?.length) {
    return []
  }
  return disks.map((disk) => ({
    mount: disk.mount ?? EMPTY,
    percent: disk.totalBytes
      ? Math.round((((disk.totalBytes ?? 0) - (disk.usableBytes ?? 0)) / disk.totalBytes) * 100)
      : null,
    sub: t('serverInfo.usedOfTotal', {
      used: formatBytes(disk.totalBytes ? (disk.totalBytes ?? 0) - (disk.usableBytes ?? 0) : null),
      total: formatBytes(disk.totalBytes),
    }),
  }))
})

const pool = computed(() => view.value?.pool ?? null)

const poolPercent = computed(() => {
  const value = pool.value
  if (!value || value.max === null || value.max === undefined) {
    return null
  }
  return Math.round(((value.allocated ?? 0) / value.max) * 100)
})

const poolStats = computed(() => {
  const value = pool.value
  if (!value) {
    return []
  }
  return [
    { key: 'active', label: t('serverInfo.poolActive'), value: value.active ?? EMPTY },
    { key: 'idle', label: t('serverInfo.poolIdle'), value: value.idle ?? EMPTY },
    { key: 'pending', label: t('serverInfo.poolPending'), value: value.pending ?? EMPTY },
    { key: 'max', label: t('serverInfo.poolMax'), value: value.max ?? EMPTY },
  ]
})
</script>

<template>
  <div class="server-info" data-test="server-info-page">
    <div class="server-info__bar">
      <el-button
        type="primary"
        plain
        :loading="isFetching"
        data-test="server-info-refresh"
        @click="refresh"
      >
        {{ t('serverInfo.refresh') }}
      </el-button>
    </div>

    <div class="server-info__gauges">
      <div class="server-info__gauge" data-test="server-info-gauge-cpu">
        <div class="server-info__gauge-title">{{ t('serverInfo.cpu') }}</div>
        <template v-if="cpuOption">
          <div class="server-info__gauge-chart">
            <BaseChart :option="cpuOption" />
            <span class="server-info__gauge-value" data-test="server-info-cpu-value">
              {{ cpuPercent }}
            </span>
          </div>
          <div class="server-info__gauge-sub">
            {{
              t('serverInfo.cores', {
                physical: view?.cpu?.physicalCores ?? EMPTY,
                logical: view?.cpu?.logicalCores ?? EMPTY,
              })
            }}
          </div>
        </template>
        <div v-else class="server-info__empty" data-test="server-info-cpu-empty">{{ EMPTY }}</div>
      </div>

      <div class="server-info__gauge" data-test="server-info-gauge-memory">
        <div class="server-info__gauge-title">{{ t('serverInfo.memory') }}</div>
        <template v-if="memoryOption">
          <div class="server-info__gauge-chart">
            <BaseChart :option="memoryOption" />
            <span class="server-info__gauge-value" data-test="server-info-memory-value">
              {{ memoryPercent }}
            </span>
          </div>
          <div class="server-info__gauge-sub">{{ memorySub }}</div>
        </template>
        <div v-else class="server-info__empty" data-test="server-info-memory-empty">
          {{ EMPTY }}
        </div>
      </div>

      <div class="server-info__gauge" data-test="server-info-gauge-jvm">
        <div class="server-info__gauge-title">{{ t('serverInfo.jvmHeap') }}</div>
        <template v-if="jvmOption">
          <div class="server-info__gauge-chart">
            <BaseChart :option="jvmOption" />
            <span class="server-info__gauge-value" data-test="server-info-jvm-value">
              {{ jvmHeapPercent }}
            </span>
          </div>
          <div class="server-info__gauge-sub">{{ jvmSub }}</div>
        </template>
        <div v-else class="server-info__empty" data-test="server-info-jvm-empty">{{ EMPTY }}</div>
      </div>
    </div>

    <div class="server-info__cards">
      <div class="server-info__card" data-test="server-info-card-swap">
        <div class="server-info__card-title">{{ t('serverInfo.swap') }}</div>
        <template v-if="view?.swap">
          <el-progress
            :percentage="swapPercent ?? 0"
            :stroke-width="14"
            data-test="server-info-swap-progress"
          />
          <div class="server-info__card-sub" data-test="server-info-swap-sub">{{ swapSub }}</div>
        </template>
        <div v-else class="server-info__empty" data-test="server-info-swap-empty">{{ EMPTY }}</div>
      </div>

      <div class="server-info__card" data-test="server-info-card-disks">
        <div class="server-info__card-title">{{ t('serverInfo.disks') }}</div>
        <template v-if="diskRows.length">
          <div v-for="disk in diskRows" :key="disk.mount" class="server-info__disk">
            <div class="server-info__disk-mount">{{ disk.mount }}</div>
            <el-progress
              v-if="disk.percent !== null"
              :percentage="disk.percent"
              :stroke-width="14"
            />
            <div class="server-info__card-sub">{{ disk.sub }}</div>
          </div>
        </template>
        <div v-else class="server-info__empty" data-test="server-info-disks-empty">{{ EMPTY }}</div>
      </div>

      <div class="server-info__card" data-test="server-info-card-pool">
        <div class="server-info__card-title">{{ t('serverInfo.pool') }}</div>
        <template v-if="pool">
          <el-progress
            v-if="poolPercent !== null"
            :percentage="poolPercent"
            :stroke-width="14"
            data-test="server-info-pool-progress"
          />
          <div class="server-info__pool-stats">
            <span v-for="stat in poolStats" :key="stat.key" class="server-info__pool-stat">
              {{ stat.label }}
              <b :data-test="`server-info-pool-${stat.key}`">{{ stat.value }}</b>
            </span>
          </div>
        </template>
        <div v-else class="server-info__empty" data-test="server-info-pool-empty">{{ EMPTY }}</div>
      </div>
    </div>
  </div>
</template>

<style scoped lang="scss">
.server-info__bar {
  display: flex;
  justify-content: flex-end;
  margin-bottom: 12px;
}

.server-info__gauges {
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  gap: 16px;
  margin-bottom: 16px;
}

.server-info__gauge {
  padding: 16px;
  background: var(--el-bg-color);
  border: 1px solid var(--el-border-color-lighter);
  border-radius: 8px;
}

.server-info__gauge-title,
.server-info__card-title {
  margin-bottom: 8px;
  font-weight: 600;
}

.server-info__gauge-chart {
  position: relative;
  height: 160px;
}

.server-info__gauge-value {
  position: absolute;
  top: 50%;
  left: 50%;
  transform: translate(-50%, -50%);
  font-weight: 600;
}

.server-info__gauge-sub,
.server-info__card-sub {
  margin-top: 8px;
  color: var(--el-text-color-secondary);
  font-size: 12px;
}

.server-info__empty {
  display: flex;
  align-items: center;
  justify-content: center;
  height: 140px;
  color: var(--el-text-color-secondary);
  font-size: 20px;
}

.server-info__cards {
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  gap: 16px;
}

.server-info__card {
  padding: 16px;
  background: var(--el-bg-color);
  border: 1px solid var(--el-border-color-lighter);
  border-radius: 8px;
}

.server-info__disk + .server-info__disk {
  margin-top: 12px;
}

.server-info__disk-mount {
  margin-bottom: 4px;
  font-size: 12px;
}

.server-info__pool-stats {
  display: flex;
  flex-wrap: wrap;
  gap: 12px;
  margin-top: 8px;
}

.server-info__pool-stat {
  color: var(--el-text-color-secondary);
  font-size: 12px;

  b {
    color: var(--el-text-color-primary);
    font-weight: 600;
  }
}

@media (max-width: 768px) {
  .server-info__gauges,
  .server-info__cards {
    grid-template-columns: 1fr;
  }
}
</style>
