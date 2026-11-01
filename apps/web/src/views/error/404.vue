<script setup lang="ts">
import { useI18n } from 'vue-i18n'

defineOptions({ name: 'NotFound' })

const { t } = useI18n()
</script>

<template>
  <div class="not-found">
    <h1>{{ t('routes.notFound') }}</h1>
    <p>{{ t('common.notFoundDesc') }}</p>
    <router-link to="/home">{{ t('common.backHome') }}</router-link>
  </div>
</template>

<style scoped lang="scss">
.not-found {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  min-height: 100vh;
  gap: 8px;

  h1 {
    margin: 0;
    font-size: 72px;
    font-weight: 700;
    color: var(--el-color-info, #909399);
  }

  p {
    margin: 0;
    color: var(--el-text-color-secondary, #909399);
  }
}
</style>
