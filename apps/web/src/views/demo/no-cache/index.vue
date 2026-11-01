<script setup lang="ts">
import { ref } from 'vue'

defineOptions({ name: 'DemoNoCache' })

const draft = ref('')
</script>

<template>
  <div class="page-placeholder">
    <h1>演示·不缓存</h1>
    <p>此页 meta.noCache：页签可开可关，不入缓存集，关闭即销毁。</p>
    <el-input v-model="draft" placeholder="输入点什么，切走再切回会清空" />
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
