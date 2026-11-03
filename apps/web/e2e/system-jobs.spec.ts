import { expect, test } from '@playwright/test'

import { gotoWithSession } from './helpers'

// 岗位管理页主链（对真实后端）：种子数据、筛选、新增、编辑、删除闭环。

test('岗位页主链：种子数据、筛选、新增、编辑与删除', async ({ page }) => {
  await gotoWithSession(page, '/system/jobs')

  // 种子岗位在列
  const table = page.locator('[data-test="jobs-table"]')
  await expect(table.getByText('开发工程师', { exact: true })).toBeVisible()

  // 名称筛选：只剩匹配行
  await page.locator('[data-test="jobs-filter-name"]').fill('市场')
  await page.locator('[data-test="jobs-search"]').click()
  await expect(table.getByText('开发工程师', { exact: true })).toBeHidden()

  // 重置后新增岗位（唯一名），列表出现
  await page.locator('[data-test="jobs-reset"]').click()
  await page.locator('[data-test="jobs-create"]').click()
  const dialog = page.locator('[data-test="jobs-dialog"]')
  await expect(dialog).toBeVisible()
  const jobName = `E2E岗位${Date.now()}`
  await dialog.locator('[data-field="name"] input').fill(jobName)
  await dialog.getByRole('button', { name: '保存' }).click()
  await expect(dialog).toBeHidden()
  await expect(table.getByText(jobName, { exact: true })).toBeVisible()

  // 编辑改排序：对话框保存后行内容更新
  const rowOf = (name: string) =>
    page.locator('[data-test="jobs-table"] tbody tr', { hasText: name })
  await rowOf(jobName).locator('[data-test="jobs-edit"]').click()
  const editDialog = page.locator('[data-test="jobs-dialog"]')
  await expect(editDialog).toBeVisible()
  await editDialog.locator('[data-field="sort"] input').fill('9')
  await editDialog.getByRole('button', { name: '保存' }).click()
  await expect(editDialog).toBeHidden()
  await expect(rowOf(jobName)).toContainText('9')

  // 删除新建岗位：确认后行消失
  await rowOf(jobName).locator('[data-test="jobs-delete"]').click()
  await page.getByRole('button', { name: '确定' }).click()
  await expect(table.getByText(jobName, { exact: true })).toBeHidden()
})
