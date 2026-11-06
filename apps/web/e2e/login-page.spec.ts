import { expect, test } from '@playwright/test'
import { nextCaptchaChallenge } from './helpers'

// 登录页左右分栏形态（匿名组）：宽屏两区对称等宽、右区图案底、表单卡层次，
// 点登录后滑块区块内联在登录卡内与输入框对齐（弹层形态退役）；375 窄屏折叠单栏不破版。

test.describe('宽屏分栏', () => {
  test.use({ viewport: { width: 1440, height: 900 } })

  test('1440 宽下品牌视觉区与表单卡并立', async ({ page }) => {
    await page.goto('/login')
    const brand = page.locator('[data-test="login-brand"]')
    const card = page.locator('.login__card')
    await expect(brand).toBeVisible()
    await expect(card).toBeVisible()
    const brandBox = await brand.boundingBox()
    const cardBox = await card.boundingBox()
    // 并立：品牌区整体在表单卡左侧
    expect(brandBox!.x + brandBox!.width).toBeLessThanOrEqual(cardBox!.x)
  })

  test('品牌区与表单区各占约一半（48%~52%）', async ({ page }) => {
    await page.goto('/login')
    const brandBox = await page.locator('[data-test="login-brand"]').boundingBox()
    const panelBox = await page.locator('.login__panel').boundingBox()
    // 对称基数 50:50：两区宽度占比同落 48%~52% 区间
    expect(brandBox!.width / 1440).toBeGreaterThanOrEqual(0.48)
    expect(brandBox!.width / 1440).toBeLessThanOrEqual(0.52)
    expect(panelBox!.width / 1440).toBeGreaterThanOrEqual(0.48)
    expect(panelBox!.width / 1440).toBeLessThanOrEqual(0.52)
  })

  test('品牌区承载产品文案与渐变底', async ({ page }) => {
    await page.goto('/login')
    const brand = page.locator('[data-test="login-brand"]')
    await expect(brand).toContainText('eladmin-plus')
    // 渐变底在册：背景图（gradient）非 none
    const backgroundImage = await brand.evaluate((el) => getComputedStyle(el).backgroundImage)
    expect(backgroundImage).toContain('gradient')
  })

  test('右区几何图案底在册（程序化渐变平铺）', async ({ page }) => {
    await page.goto('/login')
    const backgroundImage = await page
      .locator('.login__panel')
      .evaluate((el) => getComputedStyle(el).backgroundImage)
    // 点阵（radial）与网格（linear）双层平铺，非 none
    expect(backgroundImage).toContain('radial-gradient')
    expect(backgroundImage).toContain('linear-gradient')
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

test.describe('窄屏折叠', () => {
  test.use({ viewport: { width: 375, height: 667 } })

  test('375 宽折叠单栏且表单核心元素可见不破版', async ({ page }) => {
    await page.goto('/login')
    await expect(page.locator('[data-test="login-brand"]')).toBeHidden()
    await expect(page.locator('[data-test="login-username"]')).toBeVisible()
    await expect(page.locator('[data-test="login-password"]')).toBeVisible()
    await expect(page.locator('[data-test="login-submit"]')).toBeVisible()
    // 不破版：视口内无横向滚动溢出
    const scrollWidth = await page.evaluate(() => document.documentElement.scrollWidth)
    expect(scrollWidth).toBeLessThanOrEqual(375)
  })
})
