<script setup lang="ts">
import {
  ElButton,
  ElDialog,
  ElForm,
  ElFormItem,
  ElInput,
  ElInputNumber,
  ElMessage,
  ElMessageBox,
  ElOption,
  ElRadioButton,
  ElRadioGroup,
  ElSelect,
  ElSwitch,
  ElTag,
  ElTree,
  ElTreeSelect,
} from 'element-plus'
import type { FormInstance, FormRules } from 'element-plus'
import { computed, ref, watch } from 'vue'
import { useI18n } from 'vue-i18n'

import { useMenuRemove, useMenuSave, useMenuTree } from '@/api/menus'
import type { MenuNode, MenuSaveRequest } from '@/api/generated'
import AppIcon from '@/components/AppIcon.vue'
import { useAuth } from '@/composables/use-auth'
import { loadView } from '@/router/views'
import { componentPathSelectOptions, i18nKeySelectOptions } from './form-options'
import IconPicker from './icon-picker.vue'
import { typeFields } from './type-fields'
import type { MenuFormField } from './type-fields'

defineOptions({ name: 'SystemMenus' })

const { t, locale } = useI18n()
const { hasAuth } = useAuth()

const { data: tree, isLoading } = useMenuTree()
const saveMenu = useMenuSave()
const removeMenu = useMenuRemove()

/** 树节点标题：按钮节点权限文案（缺失回退权限标识），其余取标题 key */
function nodeTitle(node: MenuNode): string {
  if (node.type === 2 && !node.i18nKey) {
    return node.permission ?? ''
  }
  return node.i18nKey ? t(node.i18nKey) : ''
}

/** 上级菜单候选：目录与页面节点（按钮不可为父级），映射出树选器 label */
const parentOptions = computed<MenuNode[]>(() => {
  const walk = (list: MenuNode[]): MenuNode[] =>
    list
      .filter((node) => node.type !== 2)
      .map((node) => ({
        ...node,
        label: nodeTitle(node),
        children: node.children?.length ? walk(node.children) : undefined,
      }))
  return walk(tree.value ?? [])
})

/** —— 表单：手写 el-form + typeFields 联动（三型字段集差异大，联动重于身份域角色页） —— */

interface MenuFormModel {
  id: number | null
  parentId: number | null
  type: number
  permission: string
  i18nKey: string
  componentPathKey: string
  icon: string
  hidden: boolean
  noCache: boolean
  sort: number
}

function emptyModel(): MenuFormModel {
  return {
    id: null,
    parentId: null,
    type: 1,
    permission: '',
    i18nKey: '',
    componentPathKey: '',
    icon: '',
    hidden: false,
    noCache: false,
    sort: 0,
  }
}

const dialogVisible = ref(false)
const submitting = ref(false)
const formRef = ref<FormInstance | null>(null)
const model = ref<MenuFormModel>(emptyModel())

/** 字段显隐联动：typeFields 纯函数输出当前型字段集 */
function visible(field: MenuFormField): boolean {
  return typeFields(model.value.type).includes(field)
}

const editing = computed(() => model.value.id !== null)

const rules = computed<FormRules>(() => ({
  i18nKey: visible('i18nKey') ? [{ required: true, message: t('menus.i18nKeyRequired') }] : [],
  componentPathKey: visible('componentPathKey')
    ? [{ required: true, message: t('menus.componentPathRequired') }]
    : [],
  permission: visible('permission')
    ? [{ required: true, message: t('menus.permissionRequired') }]
    : [],
}))

/** 双下拉数据源：语言包 key 枚举（当前语言值预览）与组件路径清单（同一事实源纯函数） */
const i18nOptions = computed(() => i18nKeySelectOptions(locale.value as 'zh-CN' | 'en'))
const pathOptions = computed(() => componentPathSelectOptions())

/** 推导展示：路由 path 即组件路径前缀斜杠；name 由视图模块 defineOptions 异步解析 */
const derivedName = ref('')

watch(
  () => [model.value.type, model.value.componentPathKey] as const,
  async ([type, pathKey]) => {
    if (type !== 1 || !pathKey) {
      derivedName.value = ''
      return
    }
    const view = await loadView(pathKey)
    // 异步回填前表单可能已切换，只在 pathKey 仍一致时落值
    if (model.value.componentPathKey === pathKey) {
      derivedName.value = view?.name ?? ''
    }
  },
  { immediate: true },
)

function openCreate() {
  model.value = emptyModel()
  dialogVisible.value = true
}

function openEdit(row: MenuNode) {
  model.value = {
    id: row.id ?? null,
    parentId: row.parentId ?? null,
    type: row.type ?? 1,
    permission: row.permission ?? '',
    i18nKey: row.i18nKey ?? '',
    componentPathKey: row.componentPathKey ?? '',
    icon: row.icon ?? '',
    hidden: Boolean(row.hidden),
    noCache: Boolean(row.noCache),
    sort: row.sort ?? 0,
  }
  dialogVisible.value = true
}

async function onSubmit() {
  const valid = await formRef.value?.validate().catch(() => false)
  if (!valid) {
    return
  }
  submitting.value = true
  const payload: MenuSaveRequest = {
    id: model.value.id ?? undefined,
    parentId: model.value.parentId ?? undefined,
    type: model.value.type,
    permission: model.value.permission.trim() || undefined,
    i18nKey: model.value.i18nKey.trim() || undefined,
    componentPathKey: model.value.componentPathKey.trim() || undefined,
    icon: model.value.icon.trim() || undefined,
    hidden: model.value.hidden,
    noCache: model.value.noCache,
    sort: model.value.sort,
  }
  try {
    await saveMenu.mutateAsync(payload)
    ElMessage.success(t('menus.saveSuccess'))
    dialogVisible.value = false
  } catch (error) {
    // 结构冲突（权限标识重复、挂载非法等）由后端权威拒绝，toast 直显
    ElMessage.error(error instanceof Error ? error.message : t('menus.saveFailed'))
  } finally {
    submitting.value = false
  }
}

async function onRemove(row: MenuNode) {
  try {
    await ElMessageBox.confirm(
      t('menus.deleteConfirm', { name: nodeTitle(row) }),
      t('menus.deleteTitle'),
      {
        type: 'warning',
      },
    )
  } catch {
    return
  }
  try {
    await removeMenu.mutateAsync(row.id ?? 0)
    ElMessage.success(t('menus.deleteSuccess'))
  } catch (error) {
    // 删除保护（有子级、被角色引用）由后端权威拒绝，消息直显
    ElMessage.error(error instanceof Error ? error.message : t('menus.deleteFailed'))
  }
}
</script>

<template>
  <div class="menus" data-test="menus-page">
    <div class="menus__toolbar">
      <el-button
        v-if="hasAuth('system:menu:add')"
        type="primary"
        data-test="menus-create"
        @click="openCreate"
      >
        {{ t('menus.create') }}
      </el-button>
    </div>

    <el-tree
      v-loading="isLoading"
      :data="tree ?? []"
      node-key="id"
      default-expand-all
      :expand-on-click-node="false"
      empty-text=""
      data-test="menus-tree"
    >
      <template #default="{ data }">
        <div class="menus__node">
          <app-icon v-if="data.icon?.startsWith('svg:')" :name="data.icon.slice('svg:'.length)" />
          <el-tag v-if="data.type === 0" size="small" type="info">{{
            t('menus.typeDirectory')
          }}</el-tag>
          <el-tag v-else-if="data.type === 1" size="small">{{ t('menus.typeMenu') }}</el-tag>
          <el-tag v-else size="small" type="warning">{{ t('menus.typeButton') }}</el-tag>
          <span
            class="menus__label"
            :data-test="data.type === 2 ? 'menus-perm-node' : 'menus-menu-node'"
          >
            {{ nodeTitle(data) }}
          </span>
          <span v-if="data.type === 2 && data.permission" class="menus__meta">
            {{ data.permission }}
          </span>
          <span v-else-if="data.componentPathKey" class="menus__meta">
            {{ data.componentPathKey }}
          </span>
          <span v-if="data.hidden" class="menus__meta">{{ t('menus.hidden') }}</span>
          <span class="menus__spacer" />
          <el-button
            v-if="hasAuth('system:menu:edit')"
            link
            type="primary"
            data-test="menus-edit"
            @click="openEdit(data)"
          >
            {{ t('menus.edit') }}
          </el-button>
          <el-button
            v-if="hasAuth('system:menu:del')"
            link
            type="danger"
            data-test="menus-delete"
            @click="onRemove(data)"
          >
            {{ t('menus.delete') }}
          </el-button>
        </div>
      </template>
    </el-tree>

    <el-dialog
      v-model="dialogVisible"
      :title="editing ? t('menus.edit') : t('menus.create')"
      width="640px"
      destroy-on-close
      data-test="menus-dialog"
    >
      <el-form ref="formRef" :model="model" :rules="rules" label-width="110px">
        <el-form-item :label="t('menus.type')" data-field="type">
          <el-radio-group v-model="model.type" :disabled="editing">
            <el-radio-button :value="0">{{ t('menus.typeDirectory') }}</el-radio-button>
            <el-radio-button :value="1">{{ t('menus.typeMenu') }}</el-radio-button>
            <el-radio-button :value="2">{{ t('menus.typeButton') }}</el-radio-button>
          </el-radio-group>
          <span v-if="editing" class="menus__hint">{{ t('menus.typeLockHint') }}</span>
        </el-form-item>

        <el-form-item v-if="visible('parentId')" :label="t('menus.parent')" data-field="parentId">
          <span data-test="menus-parent">
            <el-tree-select
              v-model="model.parentId"
              :data="parentOptions"
              node-key="id"
              check-strictly
              clearable
              :props="{ label: 'label', children: 'children' }"
            />
          </span>
        </el-form-item>

        <el-form-item
          v-if="visible('i18nKey')"
          :label="t('menus.i18nKey')"
          prop="i18nKey"
          data-field="i18nKey"
        >
          <span data-test="menus-i18nkey">
            <el-select
              v-model="model.i18nKey"
              filterable
              :placeholder="t('menus.i18nKeyPlaceholder')"
            >
              <el-option
                v-for="option in i18nOptions"
                :key="option.value"
                :value="option.value"
                :label="option.label"
              />
            </el-select>
          </span>
        </el-form-item>

        <el-form-item
          v-if="visible('componentPathKey')"
          :label="t('menus.componentPath')"
          prop="componentPathKey"
          data-field="componentPathKey"
        >
          <span data-test="menus-component-path">
            <el-select
              v-model="model.componentPathKey"
              filterable
              :placeholder="t('menus.componentPathPlaceholder')"
            >
              <el-option
                v-for="option in pathOptions"
                :key="option.value"
                :value="option.value"
                :label="option.label"
              />
            </el-select>
          </span>
        </el-form-item>

        <template v-if="model.type === 1">
          <el-form-item :label="t('menus.derivedPath')" data-field="derivedPath">
            <el-input
              :model-value="`/${model.componentPathKey}`"
              disabled
              data-test="menus-derived-path"
            />
          </el-form-item>
          <el-form-item :label="t('menus.derivedName')" data-field="derivedName">
            <el-input :model-value="derivedName" disabled data-test="menus-derived-name" />
          </el-form-item>
        </template>

        <el-form-item
          v-if="visible('permission')"
          :label="t('menus.permission')"
          prop="permission"
          data-field="permission"
        >
          <el-input v-model="model.permission" data-test="menus-permission" />
        </el-form-item>

        <el-form-item v-if="visible('icon')" :label="t('menus.icon')" data-field="icon">
          <icon-picker v-model="model.icon" />
        </el-form-item>

        <el-form-item v-if="visible('hidden')" :label="t('menus.hidden')" data-field="hidden">
          <el-switch v-model="model.hidden" data-test="menus-hidden" />
        </el-form-item>

        <el-form-item v-if="visible('noCache')" :label="t('menus.noCache')" data-field="noCache">
          <el-switch v-model="model.noCache" data-test="menus-nocache" />
        </el-form-item>

        <el-form-item v-if="visible('sort')" :label="t('menus.sort')" data-field="sort">
          <el-input-number v-model="model.sort" data-test="menus-sort" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button data-test="menus-cancel" @click="dialogVisible = false">
          {{ t('menus.cancel') }}
        </el-button>
        <el-button type="primary" :loading="submitting" data-test="menus-submit" @click="onSubmit">
          {{ t('menus.submit') }}
        </el-button>
      </template>
    </el-dialog>
  </div>
</template>

<style scoped lang="scss">
.menus__toolbar {
  display: flex;
  gap: 12px;
  align-items: center;
  margin-bottom: 12px;
}

.menus__node {
  display: flex;
  flex: 1;
  gap: 8px;
  align-items: center;
  min-width: 0;
  padding-right: 8px;
  font-size: 14px;
}

.menus__label {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.menus__meta {
  font-size: 12px;
  color: var(--el-text-color-secondary, #909399);
}

.menus__spacer {
  flex: 1;
}

.menus__hint {
  margin-left: 8px;
  font-size: 12px;
  color: var(--el-text-color-secondary, #909399);
}

:deep(.el-select),
:deep(.el-tree-select) {
  width: 100%;
}
</style>
