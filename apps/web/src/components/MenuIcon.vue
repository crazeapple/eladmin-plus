<script setup lang="ts">
import { ElIcon } from 'element-plus'
import { computed } from 'vue'

import AppIcon from '@/components/AppIcon.vue'
import { hasAppIcon } from '@/components/app-icons'
import { EP_ICONS, hasEpIcon } from '@/components/ep-icons'
import { NV_ICONS, hasNvIcon } from '@/components/nv-icons'
import { EP_TO_NV, NV_TO_EP } from '@/components/icon-synonyms'
import { usePreferencesStore } from '@/stores/preferences'

/** 菜单 icon 库值（nv:name / ep:name / svg:name 前缀形态） */
const props = defineProps<{ value?: string | null }>()

// 图标风格偏好（P-022 框架）：切换即经同义映射换边渲染，侧栏响应式重渲
const prefs = usePreferencesStore()

/**
 * 统一分派：`nv:` 走 ionicons 注册集、`ep:` 走 EP 具名清单动态组件（el-icon 包裹）、
 * `svg:` 走本地 svg 集（存量待 V17 迁移，直渲不换边）；无前缀裸名（历史脏数据防御）
 * 与缺失值不渲染；前缀在册但名未知的渲染缺省占位，不崩。
 * 风格开关（iconSet）叠加同义映射换边：目标名不在册保持原库形态，不强行错换。
 */
const resolved = computed<{ kind: 'nv' | 'ep' | 'svg'; name: string } | 'missing' | null>(() => {
  const value = props.value ?? ''
  if (value.startsWith('nv:')) {
    const name = value.slice('nv:'.length)
    if (!name) {
      return null
    }
    if (!hasNvIcon(name)) {
      return 'missing'
    }
    if (prefs.iconSet === 'ep') {
      const mapped = NV_TO_EP[name]
      if (mapped && hasEpIcon(mapped)) {
        return { kind: 'ep', name: mapped }
      }
    }
    return { kind: 'nv', name }
  }
  if (value.startsWith('ep:')) {
    const name = value.slice('ep:'.length)
    if (!name) {
      return null
    }
    if (!hasEpIcon(name)) {
      return 'missing'
    }
    if (prefs.iconSet === 'svg') {
      const mapped = EP_TO_NV[name]
      if (mapped && hasNvIcon(mapped)) {
        return { kind: 'nv', name: mapped }
      }
    }
    return { kind: 'ep', name }
  }
  if (value.startsWith('svg:')) {
    const name = value.slice('svg:'.length)
    if (!name) {
      return null
    }
    if (!hasAppIcon(name)) {
      return 'missing'
    }
    return { kind: 'svg', name }
  }
  return null
})

const nvRender = computed<string | null>(() => {
  const resolvedValue = resolved.value
  return resolvedValue && resolvedValue !== 'missing' && resolvedValue.kind === 'nv'
    ? resolvedValue.name
    : null
})

const epRender = computed<string | null>(() => {
  const resolvedValue = resolved.value
  return resolvedValue && resolvedValue !== 'missing' && resolvedValue.kind === 'ep'
    ? resolvedValue.name
    : null
})

const svgRender = computed<string | null>(() => {
  const resolvedValue = resolved.value
  return resolvedValue && resolvedValue !== 'missing' && resolvedValue.kind === 'svg'
    ? resolvedValue.name
    : null
})

// 缺省占位只限双前缀值（在册但名未知）；裸名与缺失值静默不渲染
const missing = computed(() => resolved.value === 'missing')
</script>

<template>
  <span v-if="nvRender" class="app-icon app-icon--nv" aria-hidden="true">
    <component :is="NV_ICONS[nvRender]" />
  </span>
  <span v-else-if="epRender" class="app-icon app-icon--ep" aria-hidden="true">
    <el-icon><component :is="EP_ICONS[epRender]" /></el-icon>
  </span>
  <app-icon v-else-if="svgRender" :name="svgRender" />
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

// ionicons 组件形态与 svg 同 em 盒：svg 自带 viewBox 与 stroke="currentColor"（线性描边随文本色）
.app-icon--nv {
  display: inline-flex;
  align-items: center;
  justify-content: center;

  :deep(svg) {
    display: block;
    width: 1em;
    height: 1em;
  }
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
