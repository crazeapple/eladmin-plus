<script setup lang="ts">
import { ElInput } from 'element-plus'
import { computed } from 'vue'

import { registeredLocales } from '@/i18n'

defineOptions({ name: 'DictsLabelEditor' })

/**
 * 字典 label 动态语言行编辑器：按语言注册表遍历生成输入行，
 * 语言扩展只动语言包与注册表，本编辑器零改动。
 * 行值是原始文本（含空行），组装 JSON 时空行不写入（label-json.ts）。
 */
const props = defineProps<{ modelValue: Record<string, string> }>()
const emit = defineEmits<{ 'update:modelValue': [value: Record<string, string>] }>()

const locales = registeredLocales()

const rows = computed(() => props.modelValue)

function onInput(locale: string, value: string) {
  emit('update:modelValue', { ...props.modelValue, [locale]: value })
}
</script>

<template>
  <div class="label-editor" data-test="label-editor">
    <div v-for="locale in locales" :key="locale" class="label-editor__row" :data-locale="locale">
      <span class="label-editor__locale">{{ locale }}</span>
      <el-input
        :model-value="rows[locale] ?? ''"
        :data-test="`label-row-${locale}`"
        @update:model-value="onInput(locale, $event)"
      />
    </div>
  </div>
</template>

<style scoped lang="scss">
.label-editor {
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.label-editor__row {
  display: flex;
  gap: 8px;
  align-items: center;
}

.label-editor__locale {
  flex: none;
  width: 48px;
  font-size: 12px;
  color: var(--el-text-color-secondary, #909399);
  text-align: right;
}
</style>
