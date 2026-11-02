<script setup lang="ts">
import { storeToRefs } from 'pinia'
import { computed } from 'vue'
import { useI18n } from 'vue-i18n'

import { usePreferencesStore } from '@/stores/preferences'
import { PRESET_COLORS } from '@/theme/colors'

defineOptions({ name: 'SettingsDrawer' })

const modelValue = defineModel<boolean>({ required: true })

const { t } = useI18n()
const prefs = usePreferencesStore()
const { theme, primaryColor, locale } = storeToRefs(prefs)

// 标签随语言即时重算
const themeOptions = computed(() => [
  { value: 'light', label: t('settings.themeLight') },
  { value: 'dark', label: t('settings.themeDark') },
  { value: 'auto', label: t('settings.themeAuto') },
])

// 与主题行同构：显式值在前、「随系统」殿后——两行分段顺序对齐
const localeOptions = computed(() => [
  { value: 'zh-CN', label: '简体中文' },
  { value: 'en', label: 'English' },
  { value: 'system', label: t('settings.localeSystem') },
])

const presetColors = PRESET_COLORS
</script>

<template>
  <el-drawer
    :model-value="modelValue"
    :title="t('settings.title')"
    size="300px"
    data-test="settings-drawer"
    @update:model-value="modelValue = $event"
  >
    <div class="settings">
      <section class="settings__section">
        <h3>{{ t('settings.language') }}</h3>
        <el-radio-group v-model="locale" class="settings__segment" data-test="settings-locale">
          <el-radio-button
            v-for="option in localeOptions"
            :key="option.value"
            :value="option.value"
          >
            {{ option.label }}
          </el-radio-button>
        </el-radio-group>
      </section>
      <section class="settings__section">
        <h3>{{ t('settings.theme') }}</h3>
        <el-radio-group v-model="theme" class="settings__segment" data-test="settings-theme">
          <el-radio-button v-for="option in themeOptions" :key="option.value" :value="option.value">
            {{ option.label }}
          </el-radio-button>
        </el-radio-group>
      </section>
      <section class="settings__section">
        <h3>{{ t('settings.primaryColor') }}</h3>
        <div class="settings__colors" data-test="settings-colors">
          <button
            v-for="color in presetColors"
            :key="color"
            type="button"
            class="settings__swatch"
            :class="{ 'is-active': primaryColor.toLowerCase() === color }"
            :style="{ backgroundColor: color }"
            :aria-label="`primary color ${color}`"
            @click="primaryColor = color"
          />
          <el-color-picker v-model="primaryColor" data-test="settings-color-picker" />
        </div>
      </section>
    </div>
  </el-drawer>
</template>

<style scoped lang="scss">
.settings__section {
  margin-bottom: 24px;

  h3 {
    margin: 0 0 12px;
    font-size: 14px;
    font-weight: 600;
    color: var(--el-text-color-primary, #303133);
  }

  // 分段控件占满内容宽且各段等宽——宽度不随语言切换变化，两行分段天然对齐
  .settings__segment {
    display: flex;
    width: 100%;

    :deep(.el-radio-button) {
      // min-width: 0 拆掉 flex 的最小内容地板——分段严格等宽，不随文字长度偏移
      flex: 1;
      min-width: 0;

      .el-radio-button__inner {
        width: 100%;
        padding-left: 0;
        padding-right: 0;
      }
    }
  }
}

.settings__colors {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 8px;
}

.settings__swatch {
  width: 24px;
  height: 24px;
  padding: 0;
  cursor: pointer;
  border: 1px solid var(--el-border-color-light, #e4e7ed);
  border-radius: 4px;

  &.is-active {
    outline: 2px solid var(--el-color-primary);
    outline-offset: 2px;
  }
}
</style>
