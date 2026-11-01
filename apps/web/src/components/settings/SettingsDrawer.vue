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

const localeOptions = computed(() => [
  { value: 'system', label: t('settings.localeSystem') },
  { value: 'zh-CN', label: '简体中文' },
  { value: 'en', label: 'English' },
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
        <h3>{{ t('settings.theme') }}</h3>
        <el-radio-group v-model="theme" data-test="settings-theme">
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
      <section class="settings__section">
        <h3>{{ t('settings.language') }}</h3>
        <el-radio-group v-model="locale" data-test="settings-locale">
          <el-radio-button
            v-for="option in localeOptions"
            :key="option.value"
            :value="option.value"
          >
            {{ option.label }}
          </el-radio-button>
        </el-radio-group>
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
