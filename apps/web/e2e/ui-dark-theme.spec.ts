import { expect, test } from '@playwright/test'

import { gotoWithSession } from './helpers'

// 暗色三态全站走查（system 组）：登录页、主布局（侧栏 / 顶栏 / 页签）、列表页、
// 滑块内联区块的容器背景在 dark 态取暗色 token 值，无亮色残留。

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

/** 容器边框色读取（rgb 计算样式，取上缘） */
function borderOf(page: import('@playwright/test').Page, selector: string) {
  return page
    .locator(selector)
    .first()
    .evaluate((el) => getComputedStyle(el).borderTopColor)
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

test('dark 态登录页与滑块内联区块取暗色 token 值', async ({ page }) => {
  await gotoWithSession(page, '/system/users')
  await switchDark(page)

  // 清会话后直奔登录页：偏好（dark）在 localStorage，守卫不再回跳
  await page.context().clearCookies()
  await page.goto('/login')
  await expect(page.locator('[data-test="login-page"]')).toBeVisible()

  // 登录页容器与表单卡（暗色值表 0d0f14 / 1d1e1f，整页粒子背景基调），卡片层次随 token 双态
  expect(await backgroundOf(page, '.login')).toBe('rgb(13, 15, 20)')
  expect(await backgroundOf(page, '.login__card')).toBe('rgb(29, 30, 31)')
  expect(await borderOf(page, '.login__card')).toBe('rgb(65, 66, 67)')

  // 整页粒子面料背景图（明暗双版伪元素铺图）：暗态下暗版可见、亮版隐去
  const fabricState = await page.locator('.login').evaluate((el) => ({
    lightImage: getComputedStyle(el, '::before').backgroundImage,
    lightOpacity: getComputedStyle(el, '::before').opacity,
    darkImage: getComputedStyle(el, '::after').backgroundImage,
    darkOpacity: getComputedStyle(el, '::after').opacity,
  }))
  // 产物带内容 hash：暗版为无 light 后缀素材、亮版为 light 素材
  expect(fabricState.darkImage).toContain('login-fabric-')
  expect(fabricState.darkImage).not.toContain('login-fabric-light')
  expect(fabricState.darkOpacity).toBe('1')
  expect(fabricState.lightImage).toContain('login-fabric-light')
  expect(fabricState.lightOpacity).toBe('0')

  // 滑块内联区块：填表点登录卡内展开，轨道走 EP 暗色 --el-fill-color（随 token 自适应）
  await page.locator('[data-test="login-username"]').fill('admin')
  await page.locator('[data-test="login-password"]').fill('123456')
  await page.locator('[data-test="login-submit"]').click()
  await expect(page.locator('[data-test="login-captcha-dialog"]')).toBeVisible()
  expect(await backgroundOf(page, '[data-test="slider-track"]')).toBe('rgb(48, 48, 48)')
})
