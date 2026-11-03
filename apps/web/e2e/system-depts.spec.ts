import { expect, test } from '@playwright/test'

import { gotoWithSession } from './helpers'

// 部门管理页主链（对真实后端）：侧栏目录与面包屑、种子树、新增子部门、删除保护、叶子删除。

test('部门页主链：面包屑两级、种子树、新增与删除', async ({ page }) => {
  await gotoWithSession(page, '/system/depts')

  // A2：侧栏「系统管理」目录 + 面包屑两级（目录纯文本、页面可点）
  const submenu = page.locator('.el-sub-menu__title', { hasText: '系统管理' })
  await expect(submenu).toBeVisible()
  const breadcrumb = page.locator('.layout__breadcrumb')
  await expect(breadcrumb).toContainText('系统管理')
  await expect(breadcrumb).toContainText('部门管理')

  // A3：种子树在册
  const tree = page.locator('[data-test="depts-tree"]')
  await expect(tree).toBeVisible()
  for (const name of ['总部', '研发部', '市场部']) {
    await expect(tree.getByText(name, { exact: true })).toBeVisible()
  }

  // 新增子部门（研发部下）：对话框填名称保存，树刷新出现
  const rowOf = (name: string) => page.locator('.el-tree-node__content', { hasText: name })
  await rowOf('研发部').locator('[data-test="depts-create-child"]').click()
  const dialog = page.locator('[data-test="depts-dialog"]')
  await expect(dialog).toBeVisible()
  const childName = `E2E子部门${Date.now()}`
  await dialog.locator('[data-field="name"] input').fill(childName)
  await dialog.getByRole('button', { name: '保存' }).click()
  await expect(dialog).toBeHidden()
  await expect(tree.getByText(childName, { exact: true })).toBeVisible()

  // 删除保护：总部有子部门被拒，后端消息 toast 直显
  await rowOf('总部').locator('[data-test="depts-delete"]').click()
  await page.getByRole('button', { name: '确定' }).click()
  await expect(page.locator('.el-message--error')).toContainText('子部门')
  await expect(tree.getByText('总部', { exact: true })).toBeVisible()

  // 叶子删除成功：新建部门移除
  await rowOf(childName).locator('[data-test="depts-delete"]').click()
  await page.getByRole('button', { name: '确定' }).click()
  await expect(tree.getByText(childName, { exact: true })).toBeHidden()
})
