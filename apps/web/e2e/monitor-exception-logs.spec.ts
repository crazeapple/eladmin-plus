import { expect, test } from '@playwright/test'

import { gotoWithSession } from './helpers'

// 异常日志页冒烟（无 5xx 即空态，堆栈抽屉的数据操作链由组件测试覆盖）：
// 可达、空态渲染、筛选表单交互。

test('异常日志页冒烟：可达、空态与筛选交互', async ({ page }) => {
  await gotoWithSession(page, '/monitor/exception-logs')
  await expect(page.locator('[data-test="ex-logs-page"]')).toBeVisible()

  const rows = page.locator('[data-test="ex-logs-table"] tbody tr')
  if ((await rows.count()) === 0) {
    await expect(page.locator('[data-test="admin-table-empty"]')).toBeVisible()
  }

  // 筛选表单交互：消息模糊回车应用、重置回全量，全程列表在册
  await page.locator('[data-test="ex-logs-filter-message"]').fill('NullPointer')
  await page.locator('[data-test="ex-logs-filter-message"]').press('Enter')
  await expect(page.locator('[data-test="ex-logs-table"]')).toBeVisible()
  await page.locator('[data-test="ex-logs-reset"]').click()
  await expect(page.locator('[data-test="ex-logs-table"]')).toBeVisible()
})
