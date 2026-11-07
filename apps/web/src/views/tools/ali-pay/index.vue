<script setup lang="ts">
import { WalletOutline } from '@vicons/ionicons5'
import { useI18n } from 'vue-i18n'

defineOptions({ name: 'ToolsAliPay' })

const { t } = useI18n()
</script>

<template>
  <div class="ali-pay" data-test="ali-pay-page">
    <div class="ali-pay__card" data-test="ali-pay-card">
      <div class="ali-pay__icon">
        <wallet-outline />
      </div>
      <div class="ali-pay__title">{{ t('aliPay.title') }}</div>
      <div class="ali-pay__desc">{{ t('aliPay.desc') }}</div>
      <ul class="ali-pay__points">
        <li>{{ t('aliPay.pointGateway') }}</li>
        <li>{{ t('aliPay.pointConfig') }}</li>
        <li>{{ t('aliPay.pointPage') }}</li>
      </ul>
    </div>
  </div>
</template>

<style scoped lang="scss">
.ali-pay {
  display: flex;
  justify-content: center;
  padding: 40px 16px;
}

.ali-pay__card {
  width: 560px;
  max-width: 100%;
  text-align: center;
  background-color: var(--el-bg-color, #fff);
  border: 1px solid var(--el-border-color-lighter, #ebeef5);
  border-radius: 8px;
  padding: 40px 32px;
}

.ali-pay__icon {
  font-size: 48px;
  color: var(--el-color-primary, #409eff);

  /* 壳页图标为具名 ionicons 组件（裸 svg 无宽高属性），按 em 盒给尺寸 */
  svg {
    width: 1em;
    height: 1em;
  }
}

.ali-pay__title {
  margin-top: 12px;
  font-size: 18px;
  font-weight: 600;
  color: var(--el-text-color-primary, #303133);
}

.ali-pay__desc {
  margin-top: 12px;
  color: var(--el-text-color-regular, #606266);
  line-height: 1.6;
}

.ali-pay__points {
  margin: 20px 0 0;
  padding: 0;
  list-style: none;
  display: flex;
  flex-direction: column;
  gap: 8px;
  color: var(--el-text-color-secondary, #909399);
  font-size: 13px;
}
</style>
