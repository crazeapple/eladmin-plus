<script setup lang="ts">
import { computed } from 'vue'

// 本地图标集 name→svg 映射（构建期静态枚举，与动态路由视图映射同构）
const rawIcons = import.meta.glob<string>('../assets/icons/*.svg', {
  eager: true,
  query: '?raw',
  import: 'default',
})

const iconMap: Record<string, string> = {}
for (const [path, svg] of Object.entries(rawIcons)) {
  const name = path
    .split('/')
    .pop()!
    .replace(/\.svg$/, '')
  iconMap[name] = sanitize(svg)
}

const props = defineProps<{ name: string }>()

const svg = computed(() => {
  const found = iconMap[props.name]
  if (!found) {
    console.warn(`[AppIcon] 未找到图标：${props.name}`)
    return null
  }
  return found
})

// 本地文件本属可信内容，仍做一道去脚本与事件属性的通用消毒
function sanitize(raw: string): string {
  return raw
    .replace(/<script[\s\S]*?<\/script>/gi, '')
    .replace(/\son\w+\s*=\s*"[^"]*"/gi, '')
    .replace(/\son\w+\s*=\s*'[^']*'/gi, '')
}
</script>

<template>
  <!-- v-html 内容为仓库内本地 svg 经消毒后的产物，非用户输入 -->
  <!-- eslint-disable-next-line vue/no-v-html -->
  <span v-if="svg" class="app-icon" aria-hidden="true" v-html="svg" />
  <span v-else class="app-icon app-icon--missing" aria-hidden="true">?</span>
</template>

<style scoped lang="scss">
.app-icon {
  display: inline-block;
  width: 1em;
  height: 1em;
  vertical-align: -0.125em;
  color: currentColor;

  :deep(svg) {
    display: block;
    width: 100%;
    height: 100%;
  }

  // 线性图标自带 fill="none" stroke="currentColor"，不覆盖；面性图标无 fill 属性，跟随文字色
  :deep(svg:not([fill])) {
    fill: currentColor;
  }
}

.app-icon--missing {
  border: 1px dashed var(--el-border-color, #dcdfe6);
  border-radius: 2px;
  text-align: center;
  line-height: 1;
  font-size: 0.8em;
  color: var(--el-text-color-placeholder, #a8abb2);
}
</style>
