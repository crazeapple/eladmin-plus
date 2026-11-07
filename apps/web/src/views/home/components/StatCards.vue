<script setup lang="ts">
import type { Component } from 'vue'

import {
  AnalyticsOutline,
  DocumentTextOutline,
  PeopleCircleOutline,
  PersonOutline,
} from '@vicons/ionicons5'

import type { CardKey } from '../demo-data'

import StatCard from './StatCard.vue'

export interface CardStats {
  users?: number
  online?: number
  visits?: number
  logs?: number
}

defineProps<{ stats: CardStats }>()

const emit = defineEmits<{ select: [key: CardKey] }>()

/** 首页统计卡为固定 UI，直接挂具名 ionicons 组件，不经库值字符串动态分派 */
const cards: { key: CardKey; icon: Component; labelKey: string }[] = [
  { key: 'users', icon: PersonOutline, labelKey: 'home.cards.users' },
  { key: 'online', icon: PeopleCircleOutline, labelKey: 'home.cards.online' },
  { key: 'visits', icon: AnalyticsOutline, labelKey: 'home.cards.visits' },
  { key: 'logs', icon: DocumentTextOutline, labelKey: 'home.cards.logs' },
]
</script>

<template>
  <el-row :gutter="16" data-test="stat-cards">
    <el-col v-for="card in cards" :key="card.key" :xs="12" :sm="12" :lg="6">
      <StatCard
        :icon="card.icon"
        :label-key="card.labelKey"
        :value="stats[card.key]"
        @click="emit('select', card.key)"
      />
    </el-col>
  </el-row>
</template>
