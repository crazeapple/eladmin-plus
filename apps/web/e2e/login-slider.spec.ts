import { expect, test } from '@playwright/test'
import { nextCaptchaChallenge, solveSlider } from './helpers'

// 滑块弹层交互链（匿名组）：填表不触发、提交弹层出图、过验即登录、可关闭重开换题、失败弹层内换题重拖。

/** 填齐管理员表单（不点登录） */
async function fillAdmin(page: import('@playwright/test').Page) {
  await page.goto('/login')
  await page.locator('[data-test="login-username"]').fill('admin')
  await page.locator('[data-test="login-password"]').fill('123456')
}

test('填账号密码不出滑块，点登录弹层出图', async ({ page }) => {
  await fillAdmin(page)
  // 填表阶段：滑块弹层与轨道都不在
  await expect(page.locator('[data-test="login-captcha-dialog"]')).not.toBeVisible()
  await expect(page.locator('[data-test="slider-track"]')).not.toBeVisible()
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

test('弹层可关闭，重开重新出图', async ({ page }) => {
  await fillAdmin(page)
  const firstPromise = nextCaptchaChallenge(page)
  await page.locator('[data-test="login-submit"]').click()
  const first = await firstPromise
  const dialog = page.locator('[data-test="login-captcha-dialog"]')
  await expect(dialog).toBeVisible()
  // 弹层头部关闭按钮
  await dialog.locator('.el-dialog__headerbtn').click()
  await expect(page.locator('[data-test="slider-track"]')).not.toBeVisible()
  // 回登录表单态，重开弹层重新出图（token 换新，旧题随 TTL 自然过期）
  const secondPromise = nextCaptchaChallenge(page)
  await page.locator('[data-test="login-submit"]').click()
  const second = await secondPromise
  await expect(dialog).toBeVisible()
  expect(second.token).not.toBe(first.token)
})

test('拖动失败弹层内即时换题重拖', async ({ page }) => {
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
  // 弹层仍在、失败提示在、题目已换新
  await expect(page.locator('[data-test="login-captcha-dialog"]')).toBeVisible()
  await expect(page.locator('[data-test="slider-fail"]')).toBeVisible()
  expect(refetch.token).not.toBe(challenge.token)
})
