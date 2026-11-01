import { queryOptions, useQuery } from '@tanstack/vue-query'

import { overview } from './generated/sdk.gen'
import { zOverviewStats } from './generated/zod.gen'

/** 统计概览查询：query key 按域、实体、参数分层 */
export const overviewStatsQueryOptions = queryOptions({
  queryKey: ['stats', 'overview'] as const,
  queryFn: async () => {
    const { data } = await overview({ throwOnError: true })
    return zOverviewStats.parse(data)
  },
})

export function useOverviewStatsQuery() {
  return useQuery(overviewStatsQueryOptions)
}
