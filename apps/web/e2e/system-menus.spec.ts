import { expect, test } from '@playwright/test'

import { gotoWithSession } from './helpers'

// 菜单管理页主链（对真实后端）：V6 种子三型树渲染、新增按钮权限点入库、删除保护与删除闭环。

test('菜单页主链：种子树三型、新增按钮节点入库、删除保护与删除', async ({ page }) => {
  await gotoWithSession(page, '/system/menus')

  const tree = page.locator('[data-test="menus-tree"]')

  // V6 种子在册：配置域三页与身份域四页同树，按钮权限点节点带权限标识
  await expect(tree.getByText('菜单管理', { exact: true })).toBeVisible()
  await expect(tree.getByText('字典管理', { exact: true })).toBeVisible()
  await expect(tree.getByText('定时任务', { exact: true })).toBeVisible()
  await expect(tree.getByText('用户管理', { exact: true })).toBeVisible()
  await expect(tree.getByText('system:menu:list', { exact: true })).toBeVisible()

  // 新增按钮权限点：类型切按钮、权限标识必填（不选上级，按钮根级入库，末尾删除还原）
  await page.locator('[data-test="menus-create"]').click()
  const dialog = page.locator('[data-test="menus-dialog"]')
  await expect(dialog).toBeVisible()
  await dialog.locator('[data-field="type"]').getByText('按钮').click()
  await expect(dialog.locator('[data-field="permission"]')).toBeVisible()
  await expect(dialog.locator('[data-field="componentPathKey"]')).toHaveCount(0)
  await expect(dialog.locator('[data-field="i18nKey"]')).toHaveCount(0)

  const permission = `system:demo:e2e${Date.now().toString().slice(-8)}`
  await dialog.locator('[data-test="menus-permission"]').fill(permission)
  await dialog.getByRole('button', { name: '保存' }).click()
  await expect(dialog).toBeHidden()
  // 无 i18nKey 的按钮节点标题回退权限标识（标题与元信息同文本，按节点定位防 strict 撞车）
  await expect(tree.locator('[data-test="menus-perm-node"]', { hasText: permission })).toBeVisible()

  // 编辑锁定：type 单选组禁用（按钮节点编辑）
  await page
    .locator('.el-tree-node__content', { hasText: permission })
    .locator('[data-test="menus-edit"]')
    .click()
  const editDialog = page.locator('[data-test="menus-dialog"]')
  await expect(editDialog).toBeVisible()
  const radios = editDialog.locator('[data-field="type"] input[type="radio"]')
  await expect(radios.first()).toBeDisabled()
  await editDialog.getByRole('button', { name: '取消' }).click()
  await expect(editDialog).toBeHidden()

  // 删除保护：用户管理页含按钮子节点被拒，后端消息 toast 直显
  const userRow = page.locator('.el-tree-node__content', { hasText: '用户管理' }).first()
  await userRow.locator('[data-test="menus-delete"]').click()
  await page.getByRole('button', { name: '确定' }).click()
  await expect(page.locator('.el-message--error')).toContainText('子菜单')
  await expect(tree.getByText('用户管理', { exact: true })).toBeVisible()

  // 删除新建按钮节点成功
  const demoRow = page.locator('.el-tree-node__content', { hasText: permission })
  await demoRow.locator('[data-test="menus-delete"]').click()
  await page.getByRole('button', { name: '确定' }).click()
  await expect(tree.locator('[data-test="menus-perm-node"]', { hasText: permission })).toBeHidden()
})
