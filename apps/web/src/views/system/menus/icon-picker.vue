<script setup lang="ts">
import { ElButton, ElDialog, ElInput, ElIcon, ElTabPane, ElTabs } from 'element-plus'
import { computed, ref } from 'vue'
import { useI18n } from 'vue-i18n'

import { EP_ICONS } from '@/components/ep-icons'
import { NV_ICONS } from '@/components/nv-icons'

defineOptions({ name: 'MenusIconPicker' })

const { t } = useI18n()

/** nv tab 收录即 nv 注册集（在用 25 语义），与分派渲染同一事实源 */
const NV_PICKER_ICONS = Object.keys(NV_ICONS)

/** EP tab 收录即 EP 具名清单（挑适配后台菜单的子集在册），与分派渲染同一事实源 */
const EP_PICKER_ICONS = Object.keys(EP_ICONS)

const props = defineProps<{ modelValue: string }>()
const emit = defineEmits<{ 'update:modelValue': [value: string] }>()

const visible = ref(false)
const keyword = ref('')

/** 双 tab：nv ionicons 集 / EP 集；落库值形态分别为 nv:name / ep:name */
const activeTab = ref<'nv' | 'ep'>('nv')

// 打开时按当前值形态落 tab：ep: 高亮 EP tab，nv: 与存量 svg:（待迁移）落 nv
function open() {
  activeTab.value = props.modelValue.startsWith('ep:') ? 'ep' : 'nv'
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

const filteredNv = computed(() => filterBy(NV_PICKER_ICONS))
const filteredEp = computed(() => filterBy(EP_PICKER_ICONS))

/** 当前选中格：icon 值带前缀时按形态取名段高亮（无前缀与存量 svg: 形态视为未选） */
const selected = computed(() => {
  if (props.modelValue.startsWith('nv:')) {
    return { tab: 'nv', name: props.modelValue.slice('nv:'.length) }
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
        <el-tab-pane :label="t('menus.iconTabNv')" name="nv">
          <div class="icon-picker__grid" data-test="menus-icon-grid-nv">
            <button
              v-for="name in filteredNv"
              :key="name"
              type="button"
              class="icon-picker__cell"
              :class="{ 'is-active': selected?.tab === 'nv' && name === selected.name }"
              :title="name"
              :data-test="`icon-option-nv-${name}`"
              @click="pick(name)"
            >
              <component :is="NV_ICONS[name]" class="icon-picker__nv" />
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
        v-if="activeTab === 'nv' ? !filteredNv.length : !filteredEp.length"
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

/* nv 格挂具名 ionicons 组件（裸 svg 无宽高属性），与 EP 格同 18px 视觉体量 */
.icon-picker__nv {
  width: 18px;
  height: 18px;
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
