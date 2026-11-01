<script setup lang="ts">
import { ref } from 'vue'
import { useI18n } from 'vue-i18n'

defineOptions({ name: 'DemoNoCache' })

const { t } = useI18n()
const draft = ref('')
</script>

<template>
  <div class="page-placeholder">
    <h1>{{ t('demo.noCacheTitle') }}</h1>
    <p>{{ t('demo.noCacheDesc') }}</p>
    <el-input v-model="draft" :placeholder="t('demo.noCachePlaceholder')" />
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
    margin: 0 0 12px;
    color: var(--el-text-color-secondary, #909399);
  }
}
</style>
