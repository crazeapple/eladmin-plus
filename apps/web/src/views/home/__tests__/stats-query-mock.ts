/** useOverviewStatsQuery 测试替身：状态由用例直接驱动（避免真实网络与 vue-query 装配） */
import { ref } from 'vue'
import { vi } from 'vitest'

export interface StatsPayload {
  userCount: number
  onlineCount: number
  todayVisitCount: number
  logCount: number
}

export const statsQueryState = {
  data: ref<StatsPayload | undefined>(undefined),
  isError: ref(false),
  refetch: vi.fn(async () => ({ data: statsQueryState.data.value })),
}

export function useOverviewStatsQuery() {
  return statsQueryState
}
