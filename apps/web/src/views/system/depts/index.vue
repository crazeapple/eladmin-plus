<script setup lang="ts">
import { ElMessage, ElMessageBox } from 'element-plus'
import { computed, ref } from 'vue'
import { useI18n } from 'vue-i18n'
import { z } from 'zod'

import { AutoForm } from '@eladmin-plus/admin-ui'
import type { AutoFormField, AutoFormModel } from '@eladmin-plus/admin-ui'

import { useAuth } from '@/composables/use-auth'
import { fetchDeptTreeEn, findDeptNode, useDeptRemove, useDeptSave, useDeptTree } from '@/api/depts'
import type { DeptTreeNode } from '@/api/generated'

defineOptions({ name: 'SystemDepts' })

const { t } = useI18n()
const { hasAuth } = useAuth()

const { data: tree, isLoading } = useDeptTree()
const saveDept = useDeptSave()
const removeDept = useDeptRemove()

/** 编辑表单语义（nameEn 空提交即清除英文名，字段提示已注明） */
const deptSchema = z.object({
  // id 声明进 schema 才不会被 parse strip：编辑带 id 走 PUT，新增 null 走 POST
  id: z.coerce.number().nullable().optional(),
  name: z.string().trim().min(1),
  nameEn: z.string().trim().optional(),
  parentId: z.number().nullable(),
  sort: z.coerce.number().int(),
  enabled: z.union([z.literal(1), z.literal(0)]),
})

const dialogVisible = ref(false)
const submitting = ref(false)
// 表单模型用 ref（AutoForm 整对象 emit 更新，ref 的 v-model 赋值链直通）
const model = ref<AutoFormModel>({
  id: null,
  name: '',
  nameEn: '',
  parentId: null,
  sort: 0,
  enabled: 1,
})

/** 上级树：编辑时排除自身（防自环，跨级环由后端权威校验） */
const parentTree = computed<DeptTreeNode[]>(() => {
  const all = tree.value ?? []
  const currentId = model.value.id
  if (!currentId) {
    return all
  }
  const without = (nodes: DeptTreeNode[]): DeptTreeNode[] =>
    nodes
      .filter((node) => node.id !== currentId)
      .map((node) => ({ ...node, children: node.children?.length ? without(node.children) : [] }))
  return without(all)
})

const fields = computed<AutoFormField[]>(() => [
  { name: 'name', label: t('depts.name'), type: 'input' },
  { name: 'nameEn', label: t('depts.nameEn'), type: 'input', placeholder: t('depts.nameEnHint') },
  {
    name: 'parentId',
    label: t('depts.parent'),
    type: 'tree-select',
    props: {
      data: parentTree.value,
      nodeKey: 'id',
      checkStrictly: true,
      props: { label: 'name', children: 'children' },
      clearable: true,
    },
  },
  { name: 'sort', label: t('depts.sort'), type: 'input', props: { type: 'number' } },
  {
    name: 'enabled',
    label: t('depts.status'),
    type: 'radio',
    options: [
      { label: t('depts.enabled'), value: 1 },
      { label: t('depts.disabled'), value: 0 },
    ],
  },
])

function openCreate(parentId: number | null = null) {
  model.value = { id: null, name: '', nameEn: '', parentId, sort: 0, enabled: 1 }
  dialogVisible.value = true
}

/** 编辑回填：英文名取 en 树展示名（与默认名一致即无覆盖，留空提交无害） */
async function openEdit(row: DeptTreeNode) {
  model.value = {
    id: row.id,
    name: row.name,
    nameEn: '',
    parentId: row.parentId ?? null,
    sort: row.sort,
    enabled: row.enabled,
  }
  dialogVisible.value = true
  const enTree = await fetchDeptTreeEn()
  const enName = findDeptNode(enTree, row.id ?? 0)?.name ?? ''
  if (enName && enName !== row.name) {
    model.value.nameEn = enName
  }
}

async function onSubmit(data: unknown) {
  submitting.value = true
  try {
    await saveDept.mutateAsync(data as never)
    ElMessage.success(t('depts.saveSuccess'))
    dialogVisible.value = false
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : t('depts.saveFailed'))
  } finally {
    submitting.value = false
  }
}

async function onRemove(row: DeptTreeNode) {
  try {
    await ElMessageBox.confirm(
      t('depts.deleteConfirm', { name: row.name }),
      t('depts.deleteTitle'),
      {
        type: 'warning',
      },
    )
  } catch {
    return
  }
  try {
    await removeDept.mutateAsync(row.id ?? 0)
    ElMessage.success(t('depts.deleteSuccess'))
  } catch (error) {
    // 后端保护（有子部门 / 有关联用户）消息权威，toast 直显
    ElMessage.error(error instanceof Error ? error.message : t('depts.deleteFailed'))
  }
}

const rows = computed(() => tree.value ?? [])
</script>

<template>
  <div class="depts" data-test="depts-page">
    <div class="depts__toolbar">
      <el-button
        v-if="hasAuth('system:dept:add')"
        type="primary"
        data-test="depts-create"
        @click="openCreate(null)"
      >
        {{ t('depts.create') }}
      </el-button>
    </div>
    <el-tree
      v-loading="isLoading"
      :data="rows"
      node-key="id"
      default-expand-all
      :props="{ label: 'name', children: 'children' }"
      data-test="depts-tree"
    >
      <template #default="{ data }">
        <div class="depts__row">
          <span class="depts__name">{{ data.name }}</span>
          <span class="depts__sort">{{ data.sort }}</span>
          <el-tag :type="data.enabled === 1 ? 'success' : 'info'" data-test="depts-status">
            {{ data.enabled === 1 ? t('depts.enabled') : t('depts.disabled') }}
          </el-tag>
          <span class="depts__actions">
            <el-button
              v-if="hasAuth('system:dept:add')"
              link
              type="primary"
              data-test="depts-create-child"
              @click="openCreate(data.id ?? null)"
            >
              {{ t('depts.createChild') }}
            </el-button>
            <el-button
              v-if="hasAuth('system:dept:edit')"
              link
              type="primary"
              data-test="depts-edit"
              @click="openEdit(data)"
            >
              {{ t('depts.edit') }}
            </el-button>
            <el-button
              v-if="hasAuth('system:dept:del')"
              link
              type="danger"
              data-test="depts-delete"
              @click="onRemove(data)"
            >
              {{ t('depts.delete') }}
            </el-button>
          </span>
        </div>
      </template>
    </el-tree>

    <el-dialog
      v-model="dialogVisible"
      :title="model.id ? t('depts.edit') : t('depts.create')"
      width="520px"
      destroy-on-close
      data-test="depts-dialog"
    >
      <AutoForm
        v-model="model"
        :schema="deptSchema"
        :fields="fields"
        :submitting="submitting"
        :submit-text="t('depts.submit')"
        @submit="onSubmit"
      />
    </el-dialog>
  </div>
</template>

<style scoped lang="scss">
.depts__toolbar {
  margin-bottom: 12px;
}

.depts__row {
  display: flex;
  flex: 1;
  align-items: center;
  gap: 16px;
}

.depts__name {
  min-width: 180px;
}
</style>
