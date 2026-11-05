import { expect, test, type Page } from '@playwright/test'

import { gotoWithSession, loginAs } from './helpers'

// 改密全链（A7）：旧密码错误页内提示；改密成功自动登出跳登录；旧密码再登录被拒、新密码可登录。
// 改密踢该用户全部会话（storageState 的 refresh cookie 一并失效），本用例殿后跑（project 依赖 system），
// 末尾把密码还原为种子值，保证后续套件与 auth-setup 依赖不变。

/** 改密三连填 + 提交，返回端点响应状态码 */
async function changePassword(
  page: Page,
  oldPassword: string,
  newPassword: string,
): Promise<number> {
  const responsePromise = page.waitForResponse((r) => {
    return r.url().includes('/auth/me/password') && r.request().method() === 'PUT'
  })
  await page.locator('[data-test="account-password-old"]').fill(oldPassword)
  await page.locator('[data-test="account-password-new"]').fill(newPassword)
  await page.locator('[data-test="account-password-confirm"]').fill(newPassword)
  await page.locator('[data-test="account-password-save"]').click()
  return (await responsePromise).status()
}

async function openPasswordTab(page: Page): Promise<void> {
  await gotoWithSession(page, '/account')
  await expect(page.locator('[data-test="account-profile-nickname"]')).toHaveValue('系统管理员')
  await page.getByRole('tab', { name: '改密' }).click()
}

test('改密全链：错误提示、成功登出、新旧密码语义、还原种子密码', async ({ page }) => {
  await openPasswordTab(page)

  // 旧密码错误：400 页内提示，仍在系统内页
  expect(await changePassword(page, 'wrong-old', 'e2e-temp-789')).toBe(400)
  await expect(page.locator('.el-message').filter({ hasText: '旧密码不正确' })).toBeVisible()
  await expect(page).toHaveURL(/\/account$/)

  // 正确旧密码改密：200 且自动登出跳登录页（后端已踢全部会话）
  expect(await changePassword(page, '123456', 'e2e-temp-789')).toBe(200)
  await page.waitForURL((url) => url.pathname === '/login')

  // 旧密码登录被拒、新密码可登录
  expect(await loginAs(page, 'admin', '123456')).toBe(false)
  await expect(page.locator('[data-test="login-page"]')).toBeVisible()
  expect(await loginAs(page, 'admin', 'e2e-temp-789')).toBe(true)

  // 还原：改回种子密码（改密即自动登出），再以种子密码登录还原
  await openPasswordTab(page)
  expect(await changePassword(page, 'e2e-temp-789', '123456')).toBe(200)
  await page.waitForURL((url) => url.pathname === '/login')
  expect(await loginAs(page, 'admin', '123456')).toBe(true)
})
