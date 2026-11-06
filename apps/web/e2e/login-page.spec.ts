import { expect, test } from '@playwright/test'

// 登录页左右分栏形态（匿名组）：宽屏品牌视觉区与表单卡并立，375 窄屏折叠单栏不破版。

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

  test('品牌区承载产品文案与渐变底', async ({ page }) => {
    await page.goto('/login')
    const brand = page.locator('[data-test="login-brand"]')
    await expect(brand).toContainText('eladmin-plus')
    // 渐变底在册：背景图（gradient）非 none
    const backgroundImage = await brand.evaluate((el) => getComputedStyle(el).backgroundImage)
    expect(backgroundImage).toContain('gradient')
  })

  test('表单卡核心元素齐备（账号 / 密码 / 登录按钮）', async ({ page }) => {
    await page.goto('/login')
    await expect(page.locator('[data-test="login-username"]')).toBeVisible()
    await expect(page.locator('[data-test="login-password"]')).toBeVisible()
    await expect(page.locator('[data-test="login-submit"]')).toBeVisible()
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
