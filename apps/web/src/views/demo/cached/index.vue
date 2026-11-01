<script setup lang="ts">
import { ref } from 'vue'

defineOptions({ name: 'DemoCached' })

const draft = ref('')
</script>

<template>
  <div class="page-placeholder">
    <h1>演示·缓存</h1>
    <p>此页参与 keep-alive 缓存：离开再回来，下面的输入内容保留。</p>
    <el-input v-model="draft" placeholder="输入点什么，然后切走再切回" />
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
