import { expect, test } from '@playwright/test'

import { gotoWithSession } from './helpers'

// 在线用户页主链：登录会话本身即一条在线记录；强退确认弹窗对任意目标一致弹出，
// 目标是自己文案含登出提示——确认即踢自己，冒烟取消不强退保会话。

test('在线用户主链：列表含登录记录、强退确认弹窗含登出提示', async ({ page }) => {
  await gotoWithSession(page, '/monitor/online-users')
  await expect(page.locator('[data-test="online-users-page"]')).toBeVisible()

  // 持会话访问者（admin）必在册：五字段行渲染，UA 三列解析（桌面表格形态）
  const table = page.locator('[data-test="online-users-table"]')
  await expect(table).toBeVisible()
  await expect(table).toContainText('admin')
  await expect(table.locator('[data-test="ua-browser"]').first()).toBeVisible()
  await expect(table.locator('[data-test="ua-os"]').first()).toBeVisible()

  // 强退确认弹窗：目标是自己，文案明示将登出当前会话；取消不强退
  await table.locator('[data-test="online-users-kick"]').first().click()
  const dialog = page.getByRole('dialog')
  await expect(dialog).toBeVisible()
  await expect(dialog).toContainText('将登出当前会话')
  await dialog.getByRole('button', { name: '取消' }).click()
  await expect(dialog).toBeHidden()

  // 取消后会话仍在：列表照常可交互
  await expect(table).toBeVisible()
})
