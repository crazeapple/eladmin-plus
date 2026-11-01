<script setup lang="ts">
defineOptions({ name: 'NotFound' })
</script>

<template>
  <div class="not-found">
    <h1>404</h1>
    <p>页面不存在或已被移动。</p>
    <router-link to="/home">回首页</router-link>
  </div>
</template>

<style scoped lang="scss">
.not-found {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  min-height: 100vh;
  gap: 8px;

  h1 {
    margin: 0;
    font-size: 72px;
    font-weight: 700;
    color: var(--el-color-info, #909399);
  }

  p {
    margin: 0;
    color: var(--el-text-color-secondary, #909399);
  }
}
</style>
