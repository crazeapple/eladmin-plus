<script setup lang="ts">
defineOptions({ name: 'Home' })
</script>

<template>
  <div class="page-placeholder">
    <h1>首页</h1>
    <p>仪表盘随后续提案接入，当前为占位视图。</p>
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
