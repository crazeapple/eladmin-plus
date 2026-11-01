<script setup lang="ts">
import { defineAsyncComponent } from 'vue'

// 查询调试面板仅开发态按需加载，生产构建经死代码消除不进产物；测试态不挂（无 QueryClient 上下文）
const devtools =
  import.meta.env.DEV && !import.meta.env.VITEST
    ? defineAsyncComponent(() =>
        import('@tanstack/vue-query-devtools').then((m) => m.VueQueryDevtools),
      )
    : undefined
</script>

<template>
  <router-view />
  <component :is="devtools" v-if="devtools" />
</template>
