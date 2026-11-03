import { expect, test } from '@playwright/test'
import { loginAsAdmin } from './helpers'

// 登录链冒烟：从登录页开始的真实滑块校验 + 提交 + 进系统（匿名组，不持会话态）。

test('登录链：滑块校验进系统', async ({ page }) => {
  await loginAsAdmin(page)
  await expect(page.locator('[data-test="home-dashboard"]')).toBeVisible()
  await expect(page.locator('[data-test="user-area"]')).toContainText('admin')
})
