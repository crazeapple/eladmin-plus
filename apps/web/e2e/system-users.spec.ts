import { expect, test } from '@playwright/test'

import { gotoWithSession } from './helpers'

// 用户页主链（对真实后端）：左树右表联动、复杂表单全字段新增、编辑回填、
// 启停成功链与自我保护藏显、重置密码、删除闭环。

test('用户页主链：联动、复杂表单、启停与藏显、重置与删除', async ({ page }) => {
  await gotoWithSession(page, '/system/users')

  const table = page.locator('[data-test="users-table"]')
  const rowOf = (name: string) => table.locator('tbody tr', { hasText: name })

  // 自我保护藏显：admin（当前登录）行无启停开关、无删除按钮
  await expect(rowOf('admin')).toBeVisible()
  await expect(rowOf('admin').locator('[data-test="users-enabled"]')).toHaveCount(0)
  await expect(rowOf('admin').locator('[data-test="users-remove"]')).toHaveCount(0)

  // 复杂表单：全字段新增（唯一用户名），对话框提交后列表出现
  await page.locator('[data-test="users-create"]').click()
  const dialog = page.locator('[data-test="users-dialog"]')
  await expect(dialog).toBeVisible()
  const username = `e2euser${Date.now().toString().slice(-8)}`
  await dialog.locator('[data-test="users-username"]').fill(username)
  await dialog.locator('[data-test="users-nickname"]').fill('E2E测试用户')
  await dialog.locator('[data-test="users-email"]').fill(`${username}@example.com`)
  await dialog.locator('[data-test="users-password"]').fill('e2ePass123')
  // 部门为可选字段（下拉交互细节由组件层保证），不选直接保存
  await dialog.getByRole('button', { name: '保存' }).click()
  await expect(dialog).toBeHidden()
  await expect(rowOf(username)).toBeVisible()

  // 编辑回填：昵称在册、密码字段不出现（编辑不改密码）
  await rowOf(username).locator('[data-test="users-edit"]').click()
  const editDialog = page.locator('[data-test="users-dialog"]')
  await expect(editDialog).toBeVisible()
  await expect(editDialog.locator('[data-test="users-nickname"]')).toHaveValue('E2E测试用户')
  await expect(editDialog.locator('[data-test="users-password"]')).toHaveCount(0)
  await editDialog.getByRole('button', { name: '取消' }).click()
  await expect(editDialog).toBeHidden()

  // 启停成功链：停用（确认）再启用，列表状态由端点驱动
  await rowOf(username).locator('[data-test="users-enabled"]').click()
  await page.getByRole('button', { name: '确定' }).click()
  await expect(page.locator('.el-message--success').first()).toBeVisible()
  await rowOf(username).locator('[data-test="users-enabled"]').click()
  await expect(page.locator('.el-message--success').first()).toBeVisible()

  // 重置密码：确认后成功提示
  await rowOf(username).locator('[data-test="users-reset-pwd"]').click()
  await page.getByRole('button', { name: '确定' }).click()
  await expect(page.locator('.el-message--success').first()).toBeVisible()

  // 删除闭环：行消失
  await rowOf(username).locator('[data-test="users-remove"]').click()
  await page.getByRole('button', { name: '确定' }).click()
  await expect(table.getByText(username, { exact: true })).toBeHidden()
})
