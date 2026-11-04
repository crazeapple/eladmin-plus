import { expect, test } from '@playwright/test'

import { gotoWithSession } from './helpers'

// 操作日志页冒烟（生产服务暂无 @Log 标注端点，E2E 无数据源，数据操作链由组件测试覆盖）：
// 可达、空态渲染、筛选表单交互；删除 / 清空 / 导出为破坏性操作，冒烟不触发。

test('操作日志页冒烟：可达、空态与筛选交互', async ({ page }) => {
  await gotoWithSession(page, '/monitor/operation-logs')
  await expect(page.locator('[data-test="op-logs-page"]')).toBeVisible()

  // 无 @Log 数据源为空态；万一存在历史行也不炸（两态皆可）——先等其一可见再分支，消加载竞态
  const rows = page.locator('[data-test="op-logs-table"] tbody tr')
  const empty = page.locator('[data-test="admin-table-empty"]')
  await expect(rows.or(empty).first()).toBeVisible()
  if ((await rows.count()) === 0) {
    await expect(empty).toBeVisible()
  }

  // 筛选表单交互：操作人回车应用、重置回全量，全程列表在册
  await page.locator('[data-test="op-logs-filter-username"]').fill('admin')
  await page.locator('[data-test="op-logs-filter-username"]').press('Enter')
  await expect(page.locator('[data-test="op-logs-table"]')).toBeVisible()
  await page.locator('[data-test="op-logs-reset"]').click()
  await expect(page.locator('[data-test="op-logs-table"]')).toBeVisible()
})
