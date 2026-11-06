import { expect, test } from '@playwright/test'
import { nextCaptchaChallenge, solveSlider } from './helpers'

// 滑块内联展开链（匿名组）：填表不触发、点登录卡内展开出图、过验即登录、可收起重开换题、失败原区换题重拖。

/** 填齐管理员表单（不点登录） */
async function fillAdmin(page: import('@playwright/test').Page) {
  await page.goto('/login')
  await page.locator('[data-test="login-username"]').fill('admin')
  await page.locator('[data-test="login-password"]').fill('123456')
}

test('填账号密码不出滑块，点登录卡内展开出图', async ({ page }) => {
  await fillAdmin(page)
  // 填表阶段：滑块区块与轨道都不在
  await expect(page.locator('[data-test="login-captcha-dialog"]')).toHaveCount(0)
  await expect(page.locator('[data-test="slider-track"]')).toHaveCount(0)
  const challengePromise = nextCaptchaChallenge(page)
  await page.locator('[data-test="login-submit"]').click()
  const challenge = await challengePromise
  await expect(page.locator('[data-test="login-captcha-dialog"]')).toBeVisible()
  await expect(page.locator('[data-test="slider-track"]')).toBeVisible()
  await expect(page.locator('.slider-captcha__back')).toBeVisible()
  expect(challenge.token).toBeTruthy()
})

test('拖动过验无需二次操作直接登录成功', async ({ page }) => {
  await fillAdmin(page)
  const challengePromise = nextCaptchaChallenge(page)
  await page.locator('[data-test="login-submit"]').click()
  const challenge = await challengePromise
  await solveSlider(page, challenge)
  // 过验即登录：不再点提交，直接进入系统
  await page.waitForURL((url) => !url.pathname.startsWith('/login'), { timeout: 8000 })
  await expect(page.locator('[data-test="home-dashboard"]')).toBeVisible()
})

test('展开区可收起，重开重新出图', async ({ page }) => {
  await fillAdmin(page)
  const firstPromise = nextCaptchaChallenge(page)
  await page.locator('[data-test="login-submit"]').click()
  const first = await firstPromise
  const region = page.locator('[data-test="login-captcha-dialog"]')
  await expect(region).toBeVisible()
  // 头部收起按钮：收起即销毁回表单态（旧题随 TTL 自然过期）
  await region.locator('[data-test="login-captcha-close"]').click()
  await expect(page.locator('[data-test="login-captcha-dialog"]')).toHaveCount(0)
  // 回登录表单态，重开重新出图（token 换新）
  const secondPromise = nextCaptchaChallenge(page)
  await page.locator('[data-test="login-submit"]').click()
  const second = await secondPromise
  await expect(region).toBeVisible()
  expect(second.token).not.toBe(first.token)
})

test('拖动失败原区内即时换题重拖', async ({ page }) => {
  await fillAdmin(page)
  const challengePromise = nextCaptchaChallenge(page)
  await page.locator('[data-test="login-submit"]').click()
  const challenge = await challengePromise
  await expect(page.locator('[data-test="login-captcha-dialog"]')).toBeVisible()

  const track = page.locator('[data-test="slider-track"]')
  const handle = page.locator('[data-test="slider-handle"]')
  const trackBox = await track.boundingBox()
  const handleBox = await handle.boundingBox()
  if (!trackBox || !handleBox) throw new Error('滑块轨道未渲染')

  // 换题监听先挂：失败即自动拉新题
  const refetchPromise = nextCaptchaChallenge(page)
  // 故意拖到轨道末端之后（滑块封顶为全宽 → 换算距离必超容差被拒）
  await page.mouse.move(handleBox.x + handleBox.width / 2, handleBox.y + handleBox.height / 2)
  await page.mouse.down()
  await page.mouse.move(trackBox.x + trackBox.width + 30, handleBox.y + handleBox.height / 2, {
    steps: 8,
  })
  await page.mouse.up()

  await page.waitForResponse(
    (res) => res.url().includes('/auth/captcha/check') && res.status() === 400,
  )
  const refetch = await refetchPromise
  // 展开区仍在、失败提示在、题目已换新
  await expect(page.locator('[data-test="login-captcha-dialog"]')).toBeVisible()
  await expect(page.locator('[data-test="slider-fail"]')).toBeVisible()
  expect(refetch.token).not.toBe(challenge.token)
})
