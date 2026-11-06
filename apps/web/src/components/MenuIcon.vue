<script setup lang="ts">
import { ElIcon } from 'element-plus'
import { computed } from 'vue'

import AppIcon from '@/components/AppIcon.vue'
import { hasAppIcon } from '@/components/app-icons'
import { EP_ICONS, hasEpIcon } from '@/components/ep-icons'

/** 菜单 icon 库值（svg:name / ep:name 双前缀形态） */
const props = defineProps<{ value?: string | null }>()

/**
 * 统一分派：`svg:` 走本地 svg 集（AppIcon 同款形态）、`ep:` 走 EP 具名清单动态组件
 * （el-icon 包裹）；无前缀裸名（历史脏数据防御）与缺失值不渲染；
 * 前缀在册但名未知的渲染缺省占位，不崩。
 */
const svgName = computed<string | null>(() => {
  const value = props.value ?? ''
  if (!value.startsWith('svg:')) {
    return null
  }
  const name = value.slice('svg:'.length)
  return name && hasAppIcon(name) ? name : null
})

const epName = computed<string | null>(() => {
  const value = props.value ?? ''
  if (!value.startsWith('ep:')) {
    return null
  }
  const name = value.slice('ep:'.length)
  return name && hasEpIcon(name) ? name : null
})

// 缺省占位只限双前缀值（在册但名未知）；裸名与缺失值静默不渲染
const missing = computed(() => {
  const value = props.value ?? ''
  const prefixed = value.startsWith('svg:') || value.startsWith('ep:')
  return prefixed && !svgName.value && !epName.value
})
</script>

<template>
  <app-icon v-if="svgName" :name="svgName" />
  <span v-else-if="epName" class="app-icon app-icon--ep" aria-hidden="true">
    <el-icon><component :is="EP_ICONS[epName]" /></el-icon>
  </span>
  <!-- 缺省占位与 AppIcon missing 同形：库值在册但名未知的防御形态 -->
  <span v-else-if="missing" class="app-icon app-icon--missing" aria-hidden="true">?</span>
</template>

<style scoped lang="scss">
.app-icon {
  display: inline-block;
  width: 1em;
  height: 1em;
  vertical-align: -0.125em;
  color: currentColor;
}

// EP 组件形态与 svg 同 em 盒：el-icon 撑满 1em 盒，fill 绑 currentColor 随文本色（暗色自适应）
.app-icon--ep {
  display: inline-flex;
  align-items: center;
  justify-content: center;

  :deep(.el-icon) {
    width: 1em;
    height: 1em;
    font-size: inherit;
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
