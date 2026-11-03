import { expect, test } from '@playwright/test'

import { gotoWithSession } from './helpers'

// 用户页主链（对真实后端）：左树右表联动、筛选重置、行内启停（自我保护由后端权威拒绝）。

test('用户页主链：左树过滤、名称筛选、启停自我保护', async ({ page }) => {
  await gotoWithSession(page, '/system/users')

  const tree = page.locator('[data-test="users-dept-tree"]')
  const table = page.locator('[data-test="users-table"]')
  const rowOf = (name: string) => table.locator('tbody tr', { hasText: name })

  // 种子用户在列
  await expect(rowOf('admin')).toBeVisible()

  // 左树点研发部：admin（挂总部）被过滤出列；再点取消过滤恢复
  await tree.locator('.el-tree-node__content', { hasText: '研发部' }).click()
  await expect(rowOf('admin')).toBeHidden()
  await tree.locator('.el-tree-node__content', { hasText: '研发部' }).click()
  await expect(rowOf('admin')).toBeVisible()

  // 名称筛选：命中 admin；重置恢复
  await page.locator('[data-test="users-filter-name"]').fill('adm')
  await page.locator('[data-test="users-search"]').click()
  await expect(rowOf('admin')).toBeVisible()
  await page.locator('[data-test="users-reset"]').click()

  // 启停：当前登录用户（admin）停用被后端自我保护拒绝，toast 直显且行仍在列
  await rowOf('admin').locator('[data-test="users-enabled"]').click()
  await page.getByRole('button', { name: '确定' }).click()
  await expect(page.locator('.el-message--error')).toContainText('自己')
  await expect(rowOf('admin')).toBeVisible()
})
