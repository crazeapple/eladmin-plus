import { expect, test } from '@playwright/test'

import { gotoWithSession } from './helpers'

// 角色页主链（对真实后端）：种子内置角色、新增、编辑、内置删除被拒、删除闭环。

test('角色页主链：种子数据、新增、编辑、内置保护与删除', async ({ page }) => {
  await gotoWithSession(page, '/system/roles')

  const table = page.locator('[data-test="roles-table"]')
  const rowOf = (name: string) => table.locator('tbody tr', { hasText: name })

  // 内置超管角色在列
  await expect(rowOf('ROLE_ROOT')).toBeVisible()

  // 新增角色（默认 ALL 档，唯一编码），列表出现
  await page.locator('[data-test="roles-create"]').click()
  const dialog = page.locator('[data-test="roles-dialog"]')
  await expect(dialog).toBeVisible()
  const code = `ROLE_E2E_${Date.now().toString().slice(-8)}`
  await dialog.locator('[data-field="code"] input').fill(code)
  await dialog.locator('[data-field="name"] input').fill('E2E测试角色')
  await dialog.getByRole('button', { name: '保存' }).click()
  await expect(dialog).toBeHidden()
  await expect(rowOf(code)).toBeVisible()

  // 编辑改级别：保存后行内容更新
  await rowOf(code).locator('[data-test="roles-edit"]').click()
  const editDialog = page.locator('[data-test="roles-dialog"]')
  await expect(editDialog).toBeVisible()
  await expect(editDialog.locator('[data-field="code"] input')).not.toBeDisabled()
  await editDialog.locator('[data-field="level"] input').fill('5')
  await editDialog.getByRole('button', { name: '保存' }).click()
  await expect(editDialog).toBeHidden()
  await expect(rowOf(code)).toContainText('5')

  // 删除内置角色被拒：后端保护消息 toast 直显
  await rowOf('ROLE_ROOT').locator('[data-test="roles-delete"]').click()
  await page.getByRole('button', { name: '确定' }).click()
  await expect(page.locator('.el-message--error')).toBeVisible()
  await expect(rowOf('ROLE_ROOT')).toBeVisible()

  // 删除新建角色：确认后行消失
  await rowOf(code).locator('[data-test="roles-delete"]').click()
  await page.getByRole('button', { name: '确定' }).click()
  await expect(table.getByText(code, { exact: true })).toBeHidden()
})
