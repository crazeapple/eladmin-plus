<script setup lang="ts">
import { ElButton, ElDialog, ElInput } from 'element-plus'
import { computed, ref } from 'vue'
import { useI18n } from 'vue-i18n'

import AppIcon from '@/components/AppIcon.vue'

defineOptions({ name: 'MenusIconPicker' })

const { t } = useI18n()

/**
 * 收录白名单：81 个 svg 资产中挑适配后台菜单的子集，
 * 人像（user / people 等）与社交、消费类（wechat / alipay / shopping 等）明显不适配，不进选择器。
 * 选择器可见集与资产全集解耦：剔除不改资产，增补只动这份常量。
 */
const PICKER_ICONS = [
  'anq',
  'app',
  'backup',
  'chain',
  'chart',
  'codeConsole',
  'dashboard',
  'database',
  'date',
  'deploy',
  'dept',
  'dev',
  'develop',
  'dictionary',
  'doc',
  'download',
  'edit',
  'error',
  'exit-fullscreen',
  'fullscreen',
  'fwb',
  'gonggao',
  'icon',
  'image',
  'index',
  'international',
  'ipvisits',
  'java',
  'link',
  'list',
  'lock',
  'log',
  'login',
  'markdown',
  'menu',
  'message',
  'mnt',
  'monitor',
  'nested',
  'password',
  'permission',
  'qiniu',
  'redis',
  'role',
  'search',
  'server',
  'settings',
  'size',
  'skill',
  'source',
  'sqlMonitor',
  'swagger',
  'sys-tools',
  'system',
  'system1',
  'tab',
  'theme',
  'timing',
  'tools',
  'tree',
  'tree-table',
  'unlock',
  'validCode',
  'visits',
  'web',
  'zujian',
]

const props = defineProps<{ modelValue: string }>()
const emit = defineEmits<{ 'update:modelValue': [value: string] }>()

const visible = ref(false)
const keyword = ref('')

const filtered = computed(() => {
  const query = keyword.value.trim().toLowerCase()
  if (!query) {
    return PICKER_ICONS
  }
  return PICKER_ICONS.filter((name) => name.toLowerCase().includes(query))
})

/** 当前选中名：icon 值统一 svg:name 形态，取 name 段高亮（无前缀视为未选） */
const selected = computed(() =>
  props.modelValue.startsWith('svg:') ? props.modelValue.slice('svg:'.length) : '',
)

function pick(name: string) {
  emit('update:modelValue', `svg:${name}`)
  visible.value = false
}
</script>

<template>
  <span class="icon-picker">
    <el-input
      :model-value="props.modelValue"
      readonly
      :placeholder="t('menus.iconEmpty')"
      data-test="menus-icon-input"
    />
    <el-button type="primary" plain data-test="menus-icon-pick" @click="visible = true">
      {{ t('menus.iconPick') }}
    </el-button>
    <el-button
      v-if="props.modelValue"
      text
      data-test="menus-icon-clear"
      @click="emit('update:modelValue', '')"
    >
      {{ t('menus.iconClear') }}
    </el-button>

    <el-dialog
      v-model="visible"
      :title="t('menus.iconTitle')"
      width="560px"
      destroy-on-close
      data-test="menus-icon-dialog"
      append-to-body
    >
      <el-input
        v-model="keyword"
        clearable
        :placeholder="t('menus.iconFilter')"
        data-test="menus-icon-filter"
      />
      <div class="icon-picker__grid" data-test="menus-icon-grid">
        <button
          v-for="name in filtered"
          :key="name"
          type="button"
          class="icon-picker__cell"
          :class="{ 'is-active': name === selected }"
          :title="name"
          :data-test="`icon-option-${name}`"
          @click="pick(name)"
        >
          <app-icon :name="name" />
          <span class="icon-picker__name">{{ name }}</span>
        </button>
      </div>
      <p v-if="!filtered.length" class="icon-picker__empty">{{ t('menus.iconEmpty') }}</p>
    </el-dialog>
  </span>
</template>

<style scoped lang="scss">
.icon-picker {
  display: inline-flex;
  gap: 8px;
  align-items: center;
  width: 100%;

  .el-input {
    flex: 1;
    width: auto;
  }
}

.icon-picker__grid {
  display: grid;
  grid-template-columns: repeat(6, 1fr);
  gap: 8px;
  max-height: 320px;
  margin-top: 12px;
  overflow-y: auto;
}

.icon-picker__cell {
  display: flex;
  flex-direction: column;
  gap: 4px;
  align-items: center;
  padding: 8px 4px;
  font-size: 18px;
  color: var(--el-text-color-regular, #606266);
  cursor: pointer;
  background: none;
  border: 1px solid var(--el-border-color-lighter, #ebeef5);
  border-radius: 6px;

  &:hover {
    border-color: var(--el-color-primary, #409eff);
  }

  &.is-active {
    color: var(--el-color-primary, #409eff);
    border-color: var(--el-color-primary, #409eff);
  }
}

.icon-picker__name {
  max-width: 100%;
  overflow: hidden;
  font-size: 11px;
  color: var(--el-text-color-secondary, #909399);
  text-overflow: ellipsis;
  white-space: nowrap;
}

.icon-picker__empty {
  margin: 16px 0;
  font-size: 13px;
  color: var(--el-text-color-secondary, #909399);
  text-align: center;
}
</style>
