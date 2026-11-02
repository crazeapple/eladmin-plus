<script setup lang="ts">
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

const cards = [
  { key: 'users', icon: 'user', labelKey: 'home.cards.users' },
  { key: 'online', icon: 'peoples', labelKey: 'home.cards.online' },
  { key: 'visits', icon: 'ipvisits', labelKey: 'home.cards.visits' },
  { key: 'logs', icon: 'log', labelKey: 'home.cards.logs' },
] as const
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
