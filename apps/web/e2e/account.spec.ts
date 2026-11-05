import { expect, test } from '@playwright/test'

import { gotoWithSession } from './helpers'

// 个人中心入口与页骨架：顶栏用户下拉「个人中心」项启用可点，跳 /account 固定路由页渲染
// （tabs 双页签在册：资料与头像 / 改密，表单随对应任务落位）。

test('个人中心入口：顶栏下拉可点并跳 /account 渲染', async ({ page }) => {
  await gotoWithSession(page, '/home')
  await page.locator('[data-test="user-avatar"]').click()

  const profileItem = page.locator('[data-test="user-profile"]')
  await expect(profileItem).toBeVisible()
  await expect(profileItem).not.toHaveClass(/is-disabled/)
  await profileItem.click()

  await expect(page).toHaveURL(/\/account$/)
  await expect(page.locator('[data-test="account-page"]')).toBeVisible()
  // tabs 双页签在册（表单随对应任务落位）
  await expect(page.getByRole('tab', { name: '资料与头像' })).toBeVisible()
  await expect(page.getByRole('tab', { name: '改密' })).toBeVisible()
})
