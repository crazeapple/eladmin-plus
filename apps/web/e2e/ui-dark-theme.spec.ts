import { expect, test } from '@playwright/test'

import { gotoWithSession } from './helpers'

// 暗色三态全站走查（system 组）：登录页、主布局（侧栏 / 顶栏 / 页签）、列表页、
// 滑块弹层的容器背景在 dark 态取暗色 token 值，无亮色残留。

/** 打开设置抽屉并切到深色（真实 UI 链路驱动三态偏好） */
async function switchDark(page: import('@playwright/test').Page) {
  await page.locator('[data-test="settings-trigger"]').click()
  const drawer = page.locator('[data-test="settings-drawer"]')
  await expect(drawer).toBeVisible()
  await page.locator('[data-test="settings-theme"] .el-radio-button', { hasText: '深色' }).click()
  await expect(page.locator('html')).toHaveClass(/dark/)
}

/** 容器背景读取（rgb 计算样式） */
function backgroundOf(page: import('@playwright/test').Page, selector: string) {
  return page
    .locator(selector)
    .first()
    .evaluate((el) => getComputedStyle(el).backgroundColor)
}

test('dark 态内页容器取暗色 token 值', async ({ page }) => {
  await gotoWithSession(page, '/system/users')
  await switchDark(page)

  // 主布局：侧栏 / 顶栏 / 页签 / 内容区（暗色值表 141414 / 0a0a0a）
  expect(await backgroundOf(page, '.layout__aside')).toBe('rgb(20, 20, 20)')
  expect(await backgroundOf(page, '.layout__header')).toBe('rgb(20, 20, 20)')
  expect(await backgroundOf(page, '.tags-view')).toBe('rgb(20, 20, 20)')
  expect(await backgroundOf(page, '.layout__main')).toBe('rgb(10, 10, 10)')
  // 列表页卡片框（EP 暗色 --el-bg-color）
  expect(await backgroundOf(page, '[data-test="users-table"] .data-table__frame')).toBe(
    'rgb(20, 20, 20)',
  )
})

test('dark 态登录页与滑块弹层容器取暗色 token 值', async ({ page }) => {
  await gotoWithSession(page, '/system/users')
  await switchDark(page)

  // 清会话后直奔登录页：偏好（dark）在 localStorage，守卫不再回跳
  await page.context().clearCookies()
  await page.goto('/login')
  await expect(page.locator('[data-test="login-page"]')).toBeVisible()

  // 登录页容器与表单卡（暗色值表 0a0a0a / 1d1e1f）
  expect(await backgroundOf(page, '.login')).toBe('rgb(10, 10, 10)')
  expect(await backgroundOf(page, '.login__card')).toBe('rgb(29, 30, 31)')

  // 滑块弹层：填表点登录弹层出图，弹层容器走 EP 暗色 --el-bg-color（data-test 落在 el-dialog 根元素）
  await page.locator('[data-test="login-username"]').fill('admin')
  await page.locator('[data-test="login-password"]').fill('123456')
  await page.locator('[data-test="login-submit"]').click()
  await expect(page.locator('[data-test="login-captcha-dialog"]')).toBeVisible()
  expect(await backgroundOf(page, '[data-test="login-captcha-dialog"]')).toBe('rgb(20, 20, 20)')
})
