<script setup lang="ts">
import { storeToRefs } from 'pinia'
import { computed, defineAsyncComponent, watch } from 'vue'
import { useI18n } from 'vue-i18n'

import { applyLocale, elementPlusLocale } from '@/i18n'
import { usePreferencesStore } from '@/stores/preferences'
import { useTheme } from '@/theme'

// 主题三态与主色接线（偏好 store 驱动）
useTheme()

// 语言切换即时生效：i18n 实例、EP 组件文案、根元素 lang 三处同步
const i18n = useI18n()
const { resolvedLocale } = storeToRefs(usePreferencesStore())
watch(resolvedLocale, (locale) => applyLocale(i18n, locale), { immediate: true })
const epLocale = computed(() => elementPlusLocale(resolvedLocale.value))

// 查询调试面板仅开发态按需加载，生产构建经死代码消除不进产物；测试态不挂（无 QueryClient 上下文）
const devtools =
  import.meta.env.DEV && !import.meta.env.VITEST
    ? defineAsyncComponent(() =>
        import('@tanstack/vue-query-devtools').then((m) => m.VueQueryDevtools),
      )
    : undefined
</script>

<template>
  <el-config-provider :locale="epLocale">
    <router-view />
    <component :is="devtools" v-if="devtools" />
  </el-config-provider>
</template>
