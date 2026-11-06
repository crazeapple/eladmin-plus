import { expect, test } from '@playwright/test'
import { nextCaptchaChallenge } from './helpers'

// 登录页整页背景图 + 单栏居中形态（匿名组）：粒子面料图铺满整页、品牌标识左上、
// 标语与登录卡居中，滑块区块内联在登录卡内与输入框对齐；窄屏卡满幅不破版。

test.describe('宽屏单栏居中', () => {
  test.use({ viewport: { width: 1440, height: 900 } })

  test('整页背景图在册，品牌标识在卡内顶部', async ({ page }) => {
    await page.goto('/login')
    // 整页容器挂粒子面料背景图（cover 居中）
    const pageBg = await page
      .locator('.login')
      .evaluate((el) => getComputedStyle(el).backgroundImage)
    expect(pageBg).toContain('login-fabric')
    // 品牌标识在登录卡内（页面无独立品牌角标）
    const mark = page.locator('.login__card [data-test="login-brand"]')
    await expect(mark).toBeVisible()
    await expect(mark).toContainText('eladmin-plus')
    // 登录标题退役（与登录按钮文案重复）
    await expect(page.locator('.login__title')).toHaveCount(0)
  })

  test('标语在册且登录卡整页居中（中心偏差 ≤60px）', async ({ page }) => {
    await page.goto('/login')
    await expect(page.locator('.login__slogan')).toContainText('开箱即用')
    const cardBox = await page.locator('.login__card').boundingBox()
    expect(Math.abs(cardBox!.x + cardBox!.width / 2 - 720)).toBeLessThanOrEqual(60)
    expect(Math.abs(cardBox!.y + cardBox!.height / 2 - 450)).toBeLessThanOrEqual(90)
  })

  test('表单卡核心元素齐备（账号 / 密码 / 登录按钮）', async ({ page }) => {
    await page.goto('/login')
    await expect(page.locator('[data-test="login-username"]')).toBeVisible()
    await expect(page.locator('[data-test="login-password"]')).toBeVisible()
    await expect(page.locator('[data-test="login-submit"]')).toBeVisible()
  })

  test('点登录后滑块区块内联在登录卡内且与输入框左右缘对齐，无视口弹层', async ({ page }) => {
    await page.goto('/login')
    await page.locator('[data-test="login-username"]').fill('admin')
    await page.locator('[data-test="login-password"]').fill('123456')
    const challengePromise = nextCaptchaChallenge(page)
    await page.locator('[data-test="login-submit"]').click()
    await challengePromise

    // 内联承载：滑块区块渲染在登录卡 DOM 内部
    const region = page.locator('.login__card [data-test="login-captcha-dialog"]')
    await expect(region).toBeVisible()
    // 与输入框水平范围一致（左右缘偏差 ≤4px）：输入框取 el-input 外壳（原生输入框含内衬不等宽）
    const regionBox = await region.boundingBox()
    const inputBox = await page.locator('.login__card .el-input').first().boundingBox()
    expect(Math.abs(regionBox!.x - inputBox!.x)).toBeLessThanOrEqual(4)
    expect(
      Math.abs(regionBox!.x + regionBox!.width - (inputBox!.x + inputBox!.width)),
    ).toBeLessThanOrEqual(4)
    // 弹层形态退役：body 直挂的 el-dialog 覆盖层不存在
    await expect(page.locator('body > .el-overlay')).toHaveCount(0)
  })
})

test.describe('窄屏', () => {
  test.use({ viewport: { width: 375, height: 667 } })

  test('375 宽单栏卡满幅且表单核心元素可见不破版', async ({ page }) => {
    await page.goto('/login')
    // 品牌标识仍在（左上角缩距形态）
    await expect(page.locator('[data-test="login-brand"]')).toBeVisible()
    await expect(page.locator('[data-test="login-username"]')).toBeVisible()
    await expect(page.locator('[data-test="login-password"]')).toBeVisible()
    await expect(page.locator('[data-test="login-submit"]')).toBeVisible()
    // 不破版：视口内无横向滚动溢出
    const scrollWidth = await page.evaluate(() => document.documentElement.scrollWidth)
    expect(scrollWidth).toBeLessThanOrEqual(375)
  })
})
