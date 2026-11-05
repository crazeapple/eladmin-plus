<script setup lang="ts">
import { ref } from 'vue'
import { useI18n } from 'vue-i18n'

defineOptions({ name: 'Account' })

const { t } = useI18n()

const activeTab = ref('profile')
</script>

<template>
  <div class="account" data-test="account-page">
    <el-tabs v-model="activeTab" class="account__tabs">
      <el-tab-pane
        :label="t('account.profileTab')"
        name="profile"
        data-test="account-tab-profile"
      />
      <el-tab-pane
        :label="t('account.passwordTab')"
        name="password"
        data-test="account-tab-password"
      />
    </el-tabs>
  </div>
</template>

<style scoped lang="scss">
.account {
  padding: 16px;
}

.account__tabs {
  background-color: var(--el-bg-color, #fff);
  border-radius: 4px;
  padding: 8px 16px 16px;
}
</style>
