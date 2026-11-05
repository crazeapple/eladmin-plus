import { expect, test } from '@playwright/test'

import { gotoWithSession } from './helpers'

// 支付宝壳页（A8）：未启用提示卡渲染，零表单零假交互（与后端零实现现实一致）。

test('支付宝壳页：提示卡渲染且无交互面', async ({ page }) => {
  await gotoWithSession(page, '/tools/ali-pay')
  await expect(page.locator('[data-test="ali-pay-page"]')).toBeVisible()
  await expect(page.locator('[data-test="ali-pay-card"]')).toBeVisible()
  await expect(page.locator('[data-test="ali-pay-card"]')).toContainText('尚未接入')

  // 零假交互：卡内无输入、无按钮
  await expect(page.locator('[data-test="ali-pay-card"] input')).toHaveCount(0)
  await expect(page.locator('[data-test="ali-pay-card"] button')).toHaveCount(0)
})
