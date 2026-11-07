import { expect, test, type Page } from '@playwright/test'
import { nextCaptchaChallenge } from './helpers'

// 登录页整页背景图 + 单栏居中形态（匿名组）：粒子面料图铺满整页、品牌标识入卡、
// 标语与登录卡居中，滑块区块内联在登录卡内与输入框对齐；整页基调随系统深浅切换；
// 窄屏卡满幅不破版。

/** login 组件样式是路由懒加载 chunk：断言计算样式前先等背景图规则就绪 */
function waitForLoginCss(page: Page) {
  return expect
    .poll(() =>
      page.evaluate(() => {
        const el = document.querySelector('.login')
        return !!el && getComputedStyle(el, '::before').backgroundImage.includes('login-fabric')
      }),
    )
    .toBe(true)
}

test.describe('宽屏单栏居中', () => {
  test.use({ viewport: { width: 1440, height: 900 } })

  test('整页背景图在册，品牌标识在卡内顶部', async ({ page }) => {
    await page.goto('/login')
    await waitForLoginCss(page)
    // 粒子面料背景图铺在双伪元素上（cover 居中），当前主题态的那版可见
    const rawLayers = await page.evaluate(() =>
      ['::before', '::after'].map((pseudo) => {
        const s = getComputedStyle(document.querySelector('.login')!, pseudo)
        return { img: s.backgroundImage.slice(0, 60), op: s.opacity }
      }),
    )
    const layerOn = rawLayers.some((l) => l.img.includes('login-fabric') && Number(l.op) > 0)
    expect(layerOn, `layers=${JSON.stringify(rawLayers)}`).toBe(true)
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

  test('表单回车逐级推进：账号回车聚焦密码，密码回车展开滑块（form 语义在册）', async ({
    page,
  }) => {
    await page.goto('/login')
    // el-form 渲染的原生 form 元素在册（密码管理器识别依赖的表单语义）
    await expect(page.locator('[data-test="login-form"]')).toBeVisible()
    await page.locator('[data-test="login-username"]').fill('admin')
    await page.keyboard.press('Enter')
    // 账号回车 → 密码框聚焦
    await expect(page.locator('[data-test="login-password"]')).toBeFocused()
    await page.locator('[data-test="login-password"]').fill('123456')
    const challengePromise = nextCaptchaChallenge(page)
    await page.keyboard.press('Enter')
    // 密码回车 → 展开滑块区块（与点「登录」同路）
    await expect(page.locator('[data-test="login-captcha-dialog"]')).toBeVisible()
    await challengePromise
  })

  test('未登录整页基调随系统深浅实时切换（背景图版与标语字色双态）', async ({ page }) => {
    await page.emulateMedia({ colorScheme: 'light' })
    await page.goto('/login')
    await waitForLoginCss(page)
    const state = () =>
      page.evaluate(() => ({
        lightLayer: getComputedStyle(document.querySelector('.login')!, '::before').opacity,
        slogan: getComputedStyle(document.querySelector('.login__slogan')!).color,
      }))
    const light = await state()
    // 不重载页面：模拟系统切换，主题监听实时响应
    await page.emulateMedia({ colorScheme: 'dark' })
    await expect.poll(state).not.toEqual(light)
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
