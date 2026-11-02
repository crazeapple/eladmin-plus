import { expect, test } from '@playwright/test'

// 首版生产栈冒烟：首页可达渲染、主题 / 语言切换、深链刷新（history 回退）、
// 静态资产 gzip_static、无控制台错误。登录与各模块主链路随对应提案扩充进清单。

test('首页可达且渲染', async ({ page }) => {
  await page.goto('/')
  await expect(page.locator('[data-test="home-dashboard"]')).toBeVisible()
  await expect(page.locator('[data-test="chart-line"]')).toBeVisible()
})

test('主题与语言切换生效', async ({ page }) => {
  await page.goto('/')
  await page.locator('[data-test="settings-trigger"]').click()
  await expect(page.locator('[data-test="settings-drawer"]')).toBeVisible()

  // 深色：html 挂 .dark
  await page.locator('[data-test="settings-theme"]').getByText('深色').click()
  await expect(page.locator('html')).toHaveClass(/dark/)

  // 英文：偏好文案随语言切换（「随系统」段位变 System）
  await page.locator('[data-test="settings-locale"]').getByText('English').click()
  await expect(page.locator('[data-test="settings-drawer"]')).toContainText('System')

  // 还原：当前语言为英文，主题段位文案是 Light
  await page.locator('[data-test="settings-theme"]').getByText('Light').click()
  await expect(page.locator('html')).not.toHaveClass(/dark/)
  await page.locator('[data-test="settings-locale"]').getByText('简体中文').click()
})

test('深链刷新不 404（history 回退）', async ({ page }) => {
  await page.goto('/demo/cached')
  await expect(page.locator('.layout')).toBeVisible()
  await page.reload()
  await expect(page.locator('[data-test="settings-trigger"]')).toBeVisible()
})

test('静态资产经 gzip_static 提供压缩', async ({ request }) => {
  const home = await request.get('/')
  const asset = (await home.text()).match(/\/assets\/[^"']+\.js/)?.[0]
  expect(asset).toBeTruthy()

  const compressed = await request.get(asset!, { headers: { 'Accept-Encoding': 'gzip' } })
  expect(compressed.status()).toBe(200)
  expect(compressed.headers()['content-encoding']).toBe('gzip')
})

test('无控制台错误', async ({ page }) => {
  const errors: string[] = []
  page.on('console', (msg) => {
    if (msg.type() === 'error') errors.push(msg.text())
  })
  page.on('pageerror', (error) => errors.push(String(error)))

  await page.goto('/')
  await expect(page.locator('[data-test="chart-line"]')).toBeVisible()
  // 图表动画与查询稳态后仍无错误
  await page.waitForTimeout(1000)
  expect(errors).toEqual([])
})
