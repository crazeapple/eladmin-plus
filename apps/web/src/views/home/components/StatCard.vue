<script setup lang="ts">
import { toRef } from 'vue'
import { useI18n } from 'vue-i18n'

import AppIcon from '@/components/AppIcon.vue'
import { useCountUp } from '@/composables/useCountUp'

const props = defineProps<{ icon: string; labelKey: string; value?: number }>()

const { t } = useI18n()
const { display } = useCountUp(toRef(props, 'value'))
</script>

<template>
  <div class="stat-card" data-test="stat-card">
    <div class="stat-card__icon">
      <app-icon :name="icon" />
    </div>
    <div class="stat-card__body">
      <div class="stat-card__label">{{ t(labelKey) }}</div>
      <div class="stat-card__num">{{ value === undefined ? '—' : display }}</div>
    </div>
  </div>
</template>

<style scoped lang="scss">
.stat-card {
  display: flex;
  align-items: center;
  height: 108px;
  margin-bottom: 16px;
  padding: 16px;
  overflow: hidden;
  cursor: pointer;
  background: var(--el-bg-color);
  border-radius: 8px;
  box-shadow: var(--el-box-shadow-lighter);

  &__icon {
    display: flex;
    align-items: center;
    justify-content: center;
    flex-shrink: 0;
    width: 64px;
    height: 64px;
    margin-right: 16px;
    border-radius: 6px;
    color: var(--el-color-primary);
    background: var(--el-color-primary-light-9);
    transition: all 0.38s ease-out;

    svg {
      width: 32px;
      height: 32px;
    }
  }

  &:hover .stat-card__icon {
    color: #fff;
    background: var(--el-color-primary);
  }

  &__label {
    margin-bottom: 8px;
    font-size: 14px;
    line-height: 18px;
    color: var(--el-text-color-secondary);
  }

  &__num {
    font-size: 20px;
    font-weight: 600;
    color: var(--el-text-color-primary);
  }
}
</style>
