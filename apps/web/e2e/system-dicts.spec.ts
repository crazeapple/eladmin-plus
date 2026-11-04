import { expect, test } from '@playwright/test'

import { gotoWithSession } from './helpers'

// 字典管理页主链（对真实后端）：建字典、加明细（多语言标签）、回读、唯一冲突直显、级联删除闭环。

test('字典页主链：建字典、明细多语言标签、冲突直显与级联删除', async ({ page }) => {
  await gotoWithSession(page, '/system/dicts')

  // 建字典（唯一名），列表出现
  await page.locator('[data-test="dicts-create"]').click()
  const dialog = page.locator('[data-test="dicts-dialog"]')
  await expect(dialog).toBeVisible()
  const dictName = `sys_e2e_${Date.now().toString().slice(-8)}`
  await dialog.locator('[data-test="dicts-name"]').fill(dictName)
  await dialog.locator('[data-test="dicts-description"]').fill('E2E 字典')
  await dialog.getByRole('button', { name: '保存' }).click()
  await expect(dialog).toBeHidden()

  const rowOf = (name: string) =>
    page.locator('[data-test="dicts-table"] tbody tr', { hasText: name })
  await expect(rowOf(dictName)).toBeVisible()

  // 明细管理：新增明细，label 双语言行填 zh 与 en，value 回读在列
  await rowOf(dictName).locator('[data-test="dicts-details"]').click()
  const detailDialog = page.locator('[data-test="dicts-detail-dialog"]')
  await expect(detailDialog).toBeVisible()
  await page.locator('[data-test="dicts-detail-create"]').click()
  const detailForm = page.locator('[data-test="dicts-detail-form"]')
  await expect(detailForm).toBeVisible()
  // 动态语言行：当前注册表双语言两行
  await expect(detailForm.locator('[data-test="label-row-zh-CN"]')).toBeVisible()
  await expect(detailForm.locator('[data-test="label-row-en"]')).toBeVisible()
  const detailValue = `e2e_on_${Date.now().toString().slice(-8)}`
  await detailForm.locator('[data-test="label-row-zh-CN"]').fill('启用')
  await detailForm.locator('[data-test="label-row-en"]').fill('Enabled')
  await detailForm.locator('[data-test="dicts-value"]').fill(detailValue)
  await detailForm.getByRole('button', { name: '保存' }).click()
  await expect(detailForm).toBeHidden()
  await expect(
    detailDialog.locator('[data-test="dicts-detail-table"] tbody tr', { hasText: detailValue }),
  ).toBeVisible()
  // 当前浏览器语言 zh-CN：label 列取 zh 值
  await expect(
    detailDialog
      .locator('[data-test="dicts-detail-table"] tbody tr', { hasText: detailValue })
      .getByText('启用'),
  ).toBeVisible()

  // 重复 value 冲突：后端 409 消息直显
  await page.locator('[data-test="dicts-detail-create"]').click()
  await expect(detailForm).toBeVisible()
  await detailForm.locator('[data-test="dicts-value"]').fill(detailValue)
  await detailForm.getByRole('button', { name: '保存' }).click()
  await expect(page.locator('.el-message--error')).toContainText('已存在')
  await detailForm.getByRole('button', { name: '取消' }).click()

  // 关闭明细弹窗，级联删除字典：确认文案明示明细一并删除，行消失
  await detailDialog.locator('[data-test="dicts-detail-close"]').click()
  await expect(detailDialog).toBeHidden()
  await rowOf(dictName).locator('[data-test="dicts-delete"]').click()
  await expect(page.getByRole('dialog').filter({ hasText: '一并删除' })).toBeVisible()
  await page.getByRole('button', { name: '确定' }).click()
  await expect(rowOf(dictName)).toBeHidden()
})
