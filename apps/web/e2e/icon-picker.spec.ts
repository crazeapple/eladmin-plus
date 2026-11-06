import { expect, test, type Page } from '@playwright/test'

import { gotoWithSession } from './helpers'

// icon-picker 双 tab（A3）：svg 本地集与 EP 具名集两 tab 选图，EP 侧落库 ep: 前缀值，
// 菜单树与侧栏经统一分派组件回显。编辑既有种子菜单且收尾还原原值，栈数据零残留
// （载体选字典管理：svg:dictionary 在选择器白名单内，还原可全程走 UI）。

/** 打开指定页菜单的编辑弹窗（树默认全展开） */
async function openEdit(page: Page, title: string) {
  await page
    .locator('[data-test="menus-tree"] .el-tree-node__content', { hasText: title })
    .first()
    .locator('[data-test="menus-edit"]')
    .click()
  const dialog = page.locator('[data-test="menus-dialog"]')
  await expect(dialog).toBeVisible()
  return dialog
}

/** 选图弹层内切 EP tab 并按名选格，回填 ep: 前缀值 */
async function pickEpIcon(page: Page, name: string) {
  await page.locator('[data-test="menus-icon-pick"]').click()
  const picker = page.locator('[data-test="menus-icon-dialog"]')
  await expect(picker).toBeVisible()
  await picker.locator('#tab-ep').click()
  await expect(picker.locator(`[data-test="icon-option-ep-${name}"]`)).toBeVisible()
  await picker.locator(`[data-test="icon-option-ep-${name}"]`).click()
  await expect(picker).toBeHidden()
}

test('选 EP 图标落库 ep: 值，菜单树与侧栏回显组件形态', async ({ page }) => {
  await gotoWithSession(page, '/system/menus')
  const tree = page.locator('[data-test="menus-tree"]')

  // 种子树双形态在册：svg: 库值渲染本地 svg 形态，V14 补的 ep: 库值渲染 el-icon 形态
  await expect(tree.getByText('定时任务', { exact: true })).toBeVisible()
  const timingNode = tree.locator('.el-tree-node__content', { hasText: '定时任务' }).first()
  await expect(timingNode.locator('.app-icon > svg')).toBeVisible()
  const noCacheNode = tree.locator('.el-tree-node__content', { hasText: '演示·不缓存' }).first()
  await expect(noCacheNode.locator('.app-icon--ep .el-icon svg')).toBeVisible()

  // 字典管理编辑：svg tab 高亮当前值，切 EP tab 选 alarm-clock 落库 ep:alarm-clock（无 svg 同义项，缺省 svg 风格下保持 EP 形态）
  const dialog = await openEdit(page, '字典管理')
  await expect(dialog.locator('[data-test="menus-icon-input"]')).toHaveValue('svg:dictionary')
  await pickEpIcon(page, 'alarm-clock')
  await expect(dialog.locator('[data-test="menus-icon-input"]')).toHaveValue('ep:alarm-clock')
  await dialog.getByRole('button', { name: '保存' }).click()
  await expect(dialog).toBeHidden()

  // 菜单树即时回显 EP 组件形态（保存即失效树查询重取）
  await expect(
    tree
      .locator('.el-tree-node__content', { hasText: '字典管理' })
      .first()
      .locator('.app-icon--ep .el-icon svg'),
  ).toBeVisible()

  // 侧栏回显（reload 取最新库值）：系统管理展开后字典管理为 el-icon 形态，且无缺省占位
  await gotoWithSession(page, '/system/menus')
  const sidebar = page.locator('.layout__menu')
  await sidebar.getByText('系统管理', { exact: true }).click()
  const dictsItem = sidebar.locator('.el-menu-item', { hasText: '字典管理' }).first()
  await expect(dictsItem.locator('.app-icon--ep .el-icon svg')).toBeVisible()
  // V14 补齐后侧栏不应再有缺省占位
  await expect(sidebar.locator('.app-icon--missing')).toHaveCount(0)

  // 还原原库值（svg:dictionary）：ep 值打开落 EP tab，切回 svg tab 按名过滤选格
  const restoreDialog = await openEdit(page, '字典管理')
  await expect(restoreDialog.locator('[data-test="menus-icon-input"]')).toHaveValue(
    'ep:alarm-clock',
  )
  await restoreDialog.locator('[data-test="menus-icon-pick"]').click()
  const picker = page.locator('[data-test="menus-icon-dialog"]')
  await expect(picker).toBeVisible()
  await picker.locator('#tab-svg').click()
  await picker.locator('[data-test="menus-icon-filter"]').fill('dictionary')
  await picker.locator('[data-test="icon-option-dictionary"]').click()
  await expect(picker).toBeHidden()
  await expect(restoreDialog.locator('[data-test="menus-icon-input"]')).toHaveValue(
    'svg:dictionary',
  )
  await restoreDialog.getByRole('button', { name: '保存' }).click()
  await expect(restoreDialog).toBeHidden()
  await expect(
    tree
      .locator('.el-tree-node__content', { hasText: '字典管理' })
      .first()
      .locator('.app-icon > svg'),
  ).toBeVisible()
})
