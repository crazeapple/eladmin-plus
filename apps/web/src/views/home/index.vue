<script setup lang="ts">
import { useI18n } from 'vue-i18n'

defineOptions({ name: 'Home' })

const { t } = useI18n()
</script>

<template>
  <div class="page-placeholder">
    <h1>{{ t('routes.home') }}</h1>
    <p>{{ t('home.placeholder') }}</p>
  </div>
</template>

<style scoped lang="scss">
.page-placeholder {
  padding: 16px;

  h1 {
    margin: 0 0 8px;
    font-size: 20px;
    font-weight: 600;
  }

  p {
    margin: 0;
    color: var(--el-text-color-secondary, #909399);
  }
}
</style>
