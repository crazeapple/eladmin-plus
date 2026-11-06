<script setup lang="ts">
import { ElButton, ElDialog, ElInput, ElIcon, ElTabPane, ElTabs } from 'element-plus'
import { computed, ref } from 'vue'
import { useI18n } from 'vue-i18n'

import AppIcon from '@/components/AppIcon.vue'
import { EP_ICONS } from '@/components/ep-icons'

defineOptions({ name: 'MenusIconPicker' })

const { t } = useI18n()

/**
 * svg tab 收录白名单：81 个 svg 资产中挑适配后台菜单的子集，
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

/** EP tab 收录即 EP 具名清单（挑适配后台菜单的子集在册），与分派渲染同一事实源 */
const EP_PICKER_ICONS = Object.keys(EP_ICONS)

const props = defineProps<{ modelValue: string }>()
const emit = defineEmits<{ 'update:modelValue': [value: string] }>()

const visible = ref(false)
const keyword = ref('')

/** 双 tab：svg 本地集 / EP 集；落库值形态分别为 svg:name / ep:name */
const activeTab = ref<'svg' | 'ep'>('svg')

// 打开时按当前值形态落 tab：svg: 高亮 svg tab，ep: 高亮 EP tab，其余落 svg
function open() {
  activeTab.value = props.modelValue.startsWith('ep:') ? 'ep' : 'svg'
  visible.value = true
}

/** 两 tab 各自过滤：pane 同时在册渲染（隐藏 pane 不卸载），清单不能共用 */
function filterBy(names: string[]): string[] {
  const query = keyword.value.trim().toLowerCase()
  if (!query) {
    return names
  }
  return names.filter((name) => name.toLowerCase().includes(query))
}

const filteredSvg = computed(() => filterBy(PICKER_ICONS))
const filteredEp = computed(() => filterBy(EP_PICKER_ICONS))

/** 当前选中格：icon 值带前缀时按形态取名段高亮（无前缀视为未选） */
const selected = computed(() => {
  if (props.modelValue.startsWith('svg:')) {
    return { tab: 'svg', name: props.modelValue.slice('svg:'.length) }
  }
  if (props.modelValue.startsWith('ep:')) {
    return { tab: 'ep', name: props.modelValue.slice('ep:'.length) }
  }
  return null
})

function pick(name: string) {
  emit('update:modelValue', `${activeTab.value}:${name}`)
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
    <el-button type="primary" plain data-test="menus-icon-pick" @click="open">
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
      <el-tabs v-model="activeTab" class="icon-picker__tabs">
        <el-tab-pane :label="t('menus.iconTabSvg')" name="svg">
          <div class="icon-picker__grid" data-test="menus-icon-grid-svg">
            <button
              v-for="name in filteredSvg"
              :key="name"
              type="button"
              class="icon-picker__cell"
              :class="{ 'is-active': selected?.tab === 'svg' && name === selected.name }"
              :title="name"
              :data-test="`icon-option-${name}`"
              @click="pick(name)"
            >
              <app-icon :name="name" />
              <span class="icon-picker__name">{{ name }}</span>
            </button>
          </div>
        </el-tab-pane>
        <el-tab-pane :label="t('menus.iconTabEp')" name="ep">
          <div class="icon-picker__grid" data-test="menus-icon-grid-ep">
            <button
              v-for="name in filteredEp"
              :key="name"
              type="button"
              class="icon-picker__cell"
              :class="{ 'is-active': selected?.tab === 'ep' && name === selected.name }"
              :title="name"
              :data-test="`icon-option-ep-${name}`"
              @click="pick(name)"
            >
              <el-icon class="icon-picker__ep"><component :is="EP_ICONS[name]" /></el-icon>
              <span class="icon-picker__name">{{ name }}</span>
            </button>
          </div>
        </el-tab-pane>
      </el-tabs>
      <p
        v-if="activeTab === 'svg' ? !filteredSvg.length : !filteredEp.length"
        class="icon-picker__empty"
      >
        {{ t('menus.iconEmpty') }}
      </p>
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

.icon-picker__tabs {
  margin-top: 4px;
}

.icon-picker__grid {
  display: grid;
  grid-template-columns: repeat(6, 1fr);
  gap: 8px;
  max-height: 320px;
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

.icon-picker__ep {
  font-size: 18px;
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
