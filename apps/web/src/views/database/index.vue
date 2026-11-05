<script setup lang="ts">
import { ElTabs, ElTabPane } from 'element-plus'
import { useI18n } from 'vue-i18n'

import ConfigPanel from './config-panel.vue'

defineOptions({ name: 'OpsDatabase' })

const { t } = useI18n()
</script>

<template>
  <div class="database" data-test="database-page">
    <el-tabs data-test="database-tabs">
      <el-tab-pane :label="t('database.configTab')" data-test="database-tab-config">
        <ConfigPanel />
      </el-tab-pane>
      <el-tab-pane :label="t('database.recordTab')" data-test="database-tab-record">
        <div data-test="database-record-panel" />
      </el-tab-pane>
    </el-tabs>
  </div>
</template>

<style scoped lang="scss">
.database {
  :deep(.el-tabs__content) {
    overflow: visible;
  }
}
</style>
