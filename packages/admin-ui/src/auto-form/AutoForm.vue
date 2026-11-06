<script setup lang="ts">
import { nextTick, ref, toRaw, useSlots } from 'vue'
import {
  ElButton,
  ElCheckbox,
  ElCheckboxGroup,
  ElDatePicker,
  ElInput,
  ElOption,
  ElRadio,
  ElRadioGroup,
  ElSelect,
  ElTreeSelect,
} from 'element-plus'
import type { ZodType } from 'zod'
import type { AutoFormField, AutoFormModel } from './types'

const props = withDefaults(
  defineProps<{
    /** zod schema：字段约束与必填语义由此承载 */
    schema: ZodType
    fields: AutoFormField[]
    modelValue: AutoFormModel
    /** 提交中：按钮禁用并带加载态（提交处理归页面） */
    submitting?: boolean
    submitText?: string
  }>(),
  {
    submitting: false,
    submitText: '提交',
  },
)

const emit = defineEmits<{
  'update:modelValue': [model: AutoFormModel]
  /** 整表校验通过后上抛 zod parse 产物 */
  submit: [data: unknown]
}>()

const slots = useSlots()

const errors = ref<Record<string, string>>({})
const formError = ref('')
const rootEl = ref<HTMLFormElement | null>(null)

function fieldValue(name: string): unknown {
  return props.modelValue[name]
}

// EP 各控件模型类型不一；模板表达式不写 as 断言（竖线会被当成过滤器），统一在此收窄
function inputModel(value: unknown): string | number | null {
  return (value ?? null) as string | number | null
}

function selectModel(value: unknown): string | number | boolean | null {
  return (value ?? null) as string | number | boolean | null
}

function dateModel(value: unknown): string | null {
  return (value ?? null) as string | null
}

function radioModel(value: unknown): string | number | boolean | undefined {
  return (value ?? undefined) as string | number | boolean | undefined
}

function checkboxModel(value: unknown): Array<string | number> {
  return (value ?? []) as Array<string | number>
}

function setFieldValue(name: string, value: unknown) {
  emit('update:modelValue', { ...props.modelValue, [name]: value })
  // 该字段已有错误时，修改即清错
  if (errors.value[name]) {
    const next = { ...errors.value }
    delete next[name]
    errors.value = next
  }
}

// 必填标记：schema 顶层同名属性非 optional 即视为必填
function isRequired(field: AutoFormField): boolean {
  // zod schema 内部属性只读，经响应式代理访问会冲突，统一 toRaw 解包
  const shape = (
    toRaw(props.schema) as unknown as {
      shape?: Record<string, { isOptional?: () => boolean }>
    }
  ).shape
  const fieldSchema = shape?.[field.name]
  return fieldSchema ? !fieldSchema.isOptional?.() : false
}

async function onSubmit() {
  formError.value = ''
  const result = toRaw(props.schema).safeParse(props.modelValue)
  if (result.success) {
    errors.value = {}
    emit('submit', result.data)
    return
  }
  // issue path 精确映射字段错误；未映射 issue 归表单级提示
  const fieldErrors: Record<string, string> = {}
  for (const issue of result.error.issues) {
    const key = issue.path.length ? String(issue.path[0]) : ''
    if (key && props.fields.some((field) => field.name === key)) {
      fieldErrors[key] ??= issue.message
    } else {
      formError.value ||= issue.message
    }
  }
  errors.value = fieldErrors
  await nextTick()
  // 聚焦首个错误字段
  const first = Object.keys(fieldErrors)[0]
  if (first) {
    rootEl.value
      ?.querySelector<HTMLElement>(
        `[data-field="${first}"] input, [data-field="${first}"] textarea, [data-field="${first}"] .el-select__wrapper`,
      )
      ?.focus()
  }
}
</script>

<template>
  <form ref="rootEl" class="auto-form" data-test="auto-form" novalidate @submit.prevent="onSubmit">
    <div v-if="formError" class="auto-form__form-error" data-test="auto-form-form-error">
      {{ formError }}
    </div>
    <div v-for="field in fields" :key="field.name" class="auto-form__row" :data-field="field.name">
      <label class="auto-form__label">
        <span v-if="isRequired(field)" class="auto-form__required" aria-hidden="true">*</span>
        {{ field.label }}
      </label>
      <div class="auto-form__control">
        <!-- 具名插槽 field-<name>：手写清单外字段与任意字段接管 -->
        <slot
          v-if="slots[`field-${field.name}`]"
          :name="`field-${field.name}`"
          :field="field"
          :value="fieldValue(field.name)"
        />
        <ElInput
          v-else-if="field.type === 'input'"
          v-bind="field.props"
          :model-value="inputModel(fieldValue(field.name))"
          :placeholder="field.placeholder"
          @update:model-value="(value) => setFieldValue(field.name, value)"
        />
        <ElSelect
          v-else-if="field.type === 'select'"
          :model-value="selectModel(fieldValue(field.name))"
          :placeholder="field.placeholder"
          @update:model-value="(value) => setFieldValue(field.name, value)"
        >
          <ElOption
            v-for="option in field.options"
            :key="String(option.value)"
            :label="option.label"
            :value="option.value"
          />
        </ElSelect>
        <ElDatePicker
          v-else-if="field.type === 'date'"
          :model-value="dateModel(fieldValue(field.name))"
          type="datetime"
          value-format="YYYY-MM-DD HH:mm:ss"
          :placeholder="field.placeholder"
          v-bind="field.props"
          @update:model-value="(value) => setFieldValue(field.name, value)"
        />
        <ElRadioGroup
          v-else-if="field.type === 'radio'"
          :model-value="radioModel(fieldValue(field.name))"
          @update:model-value="(value) => setFieldValue(field.name, value ?? null)"
        >
          <ElRadio
            v-for="option in field.options"
            :key="String(option.value)"
            :value="option.value"
          >
            {{ option.label }}
          </ElRadio>
        </ElRadioGroup>
        <ElCheckboxGroup
          v-else-if="field.type === 'checkbox'"
          :model-value="checkboxModel(fieldValue(field.name))"
          @update:model-value="(value) => setFieldValue(field.name, value)"
        >
          <ElCheckbox
            v-for="option in field.options"
            :key="String(option.value)"
            :value="option.value"
          >
            {{ option.label }}
          </ElCheckbox>
        </ElCheckboxGroup>
        <ElTreeSelect
          v-else-if="field.type === 'tree-select'"
          v-bind="{
            ...field.props,
            modelValue: fieldValue(field.name) ?? null,
            'onUpdate:modelValue': (value: unknown) => setFieldValue(field.name, value),
          }"
        />
        <div v-else-if="field.type === 'upload'" class="auto-form__upload-hint">
          上传控件经 field-{{ field.name }} 插槽接入（存储后端就绪后提供）
        </div>
        <div v-if="errors[field.name]" class="auto-form__error" data-test="auto-form-field-error">
          {{ errors[field.name] }}
        </div>
      </div>
    </div>
    <div class="auto-form__actions">
      <ElButton native-type="submit" type="primary" :loading="submitting" :disabled="submitting">
        {{ submitText }}
      </ElButton>
    </div>
  </form>
</template>

<style scoped>
.auto-form {
  font-size: var(--el-font-size-base);
  color: var(--el-text-color-regular);
}

.auto-form__row {
  display: flex;
  align-items: flex-start;
  gap: 12px;
  margin-bottom: 16px;
}

.auto-form__label {
  flex-shrink: 0;
  width: 90px;
  padding-top: 6px;
  text-align: right;
}

.auto-form__required {
  margin-right: 4px;
  color: var(--el-color-danger);
}

.auto-form__control {
  flex: 1;
  min-width: 0;
}

.auto-form__error {
  margin-top: 4px;
  font-size: 12px;
  color: var(--el-color-danger);
}

.auto-form__form-error {
  margin-bottom: 12px;
  padding: 8px 12px;
  font-size: 13px;
  color: var(--el-color-danger);
  background: var(--el-color-danger-light-9);
  /* 品牌 token 直引（带 fallback）：圆角档位走设计基座 */
  border-radius: var(--ea-radius-sm, 6px);
}

.auto-form__actions {
  display: flex;
  justify-content: flex-end;
}

.auto-form__upload-hint {
  padding: 8px 12px;
  font-size: 13px;
  color: var(--el-text-color-secondary);
  border: 1px dashed var(--el-border-color);
  border-radius: var(--ea-radius-sm, 6px);
}
</style>
