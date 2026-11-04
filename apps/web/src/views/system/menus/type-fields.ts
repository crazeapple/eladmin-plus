/** 菜单表单字段名（typeFields 返回其子集） */
export type MenuFormField =
  | 'parentId'
  | 'permission'
  | 'i18nKey'
  | 'componentPathKey'
  | 'icon'
  | 'hidden'
  | 'noCache'
  | 'sort'

/** 全字段清单（表单渲染层遍历用） */
export const MENU_FORM_FIELDS: MenuFormField[] = [
  'parentId',
  'permission',
  'i18nKey',
  'componentPathKey',
  'icon',
  'hidden',
  'noCache',
  'sort',
]

/**
 * 三型字段集联动（纯函数零组件依赖，可独立测试）：
 * 目录 4 字段 / 菜单 7 字段 / 按钮 3 字段（仅权限标识 + 上级 + 排序生效）。
 * 编辑时 type 锁定由表单层处理（后端 422 为权威，前端防误操作）。
 */
export function typeFields(type: number): MenuFormField[] {
  switch (type) {
    case 0:
      return ['parentId', 'i18nKey', 'icon', 'sort']
    case 1:
      return ['parentId', 'i18nKey', 'componentPathKey', 'icon', 'hidden', 'noCache', 'sort']
    case 2:
      return ['parentId', 'permission', 'sort']
    default:
      return []
  }
}
