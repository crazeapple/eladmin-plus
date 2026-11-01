<script setup lang="ts">
defineOptions({ name: 'Layout' })
</script>

<template>
  <div class="layout-min">
    <router-view />
  </div>
</template>

<style scoped lang="scss">
.layout-min {
  min-height: 100%;
}
</style>
