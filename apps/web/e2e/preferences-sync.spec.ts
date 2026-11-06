import { expect, test, type Page } from '@playwright/test'

import { loginAs, resetAdminPreferences } from './helpers'

// 偏好同步（system 组）：后端为偏好真相源——切换即 PUT 持久（刷新后会话级保持）、
// 同浏览器多 Tab 经 storage 事件本地即时同步、跨 context 登录读到同一账户值。
// 分段控件按位点选（位序 = 选项数组序，文案随语言变，位序不变）；各用例收尾还原缺省。

/** 登录态进首页（storageState 静默恢复会话，落地 /home） */
async function gotoHome(page: Page): Promise<void> {
  await page.goto('/')
  await expect(page.locator('[data-test="home-dashboard"]')).toBeVisible()
}

/** 打开设置抽屉 */
async function openDrawer(page: Page): Promise<void> {
  await page.locator('[data-test="settings-trigger"]').click()
  await expect(page.locator('[data-test="settings-drawer"]')).toBeVisible()
}

/** 收起设置抽屉（遮罩在册会拦截后续点击） */
async function closeDrawer(page: Page): Promise<void> {
  await page.locator('.el-drawer__close-btn').click()
  await expect(page.locator('[data-test="settings-drawer"]')).toBeHidden()
}

/** 等待偏好 PUT 落定并返回状态码（不等即关页会中断在途写，还原必须等它） */
async function nextPreferencesPut(page: Page): Promise<number> {
  const response = await page.waitForResponse(
    (res) => res.url().includes('/profile/preferences') && res.request().method() === 'PUT',
  )
  return response.status()
}

/** 按位点选分段控件：theme [浅色, 深色, 随系统] / locale [简体中文, English, 跟随系统] */
async function setSegment(page: Page, segment: 'theme' | 'locale', index: number): Promise<void> {
  await page.locator(`[data-test="settings-${segment}"] .el-radio-button`).nth(index).click()
}

test('改主题发出 PUT 持久，刷新后主题保持', async ({ page }) => {
  resetAdminPreferences()
  await gotoHome(page)
  await openDrawer(page)

  // 切深色：本地即时生效，PUT 200 落后端
  const putPromise = nextPreferencesPut(page)
  await setSegment(page, 'theme', 1)
  expect(await putPromise).toBe(200)
  await expect(page.locator('html')).toHaveClass(/dark/)

  // 刷新保持：me 响应带出 dark（账户值在库），首帧缓存先行无回跳
  const mePromise = page.waitForResponse((res) => res.url().includes('/auth/me'))
  await page.reload()
  const me = (await (await mePromise).json()) as { data: { preferences: { theme: string } } }
  expect(me.data.preferences.theme).toBe('dark')
  await expect(page.locator('html')).toHaveClass(/dark/)

  // 还原缺省（随系统），PUT 落定再收尾
  await openDrawer(page)
  const restorePromise = nextPreferencesPut(page)
  await setSegment(page, 'theme', 2)
  expect(await restorePromise).toBe(200)
  await expect(page.locator('html')).not.toHaveClass(/dark/)
})

test('同浏览器多 Tab：一处切语言其他 Tab 实时同步', async ({ page }) => {
  resetAdminPreferences()
  await gotoHome(page)
  const pageB = await page.context().newPage()
  await gotoHome(pageB)

  await openDrawer(page)
  const putPromise = nextPreferencesPut(page)
  await setSegment(page, 'locale', 1)
  expect(await putPromise).toBe(200)

  // 本 Tab 立即生效，另一 Tab 经 storage 事件随动（html lang 实时翻转）
  await expect(page.locator('html')).toHaveAttribute('lang', 'en')
  await expect(pageB.locator('html')).toHaveAttribute('lang', 'en')

  // A 端还原缺省（跟随系统），B 端同链路随动；PUT 落定再收尾
  const restorePromise = nextPreferencesPut(page)
  await setSegment(page, 'locale', 2)
  expect(await restorePromise).toBe(200)
  await expect(page.locator('html')).toHaveAttribute('lang', 'zh-CN')
  await expect(pageB.locator('html')).toHaveAttribute('lang', 'zh-CN')
  await pageB.close()
})

test('跨 context 会话一致：A 改语言退出，B 登录读到同语言', async ({ browser }) => {
  resetAdminPreferences()
  // 全新空态 context（system 组默认 context 自带 storageState 会话，须显式清掉才走真实登录链）
  const emptyState = { cookies: [], origins: [] }
  const contextA = await browser.newContext({ storageState: emptyState })
  const pageA = await contextA.newPage()
  expect(await loginAs(pageA, 'admin', '123456')).toBe(true)

  // A 改语言为英文（PUT 持久）后收抽屉再退出
  await openDrawer(pageA)
  const putPromise = nextPreferencesPut(pageA)
  await setSegment(pageA, 'locale', 1)
  expect(await putPromise).toBe(200)
  await closeDrawer(pageA)
  await pageA.locator('[data-test="user-avatar"]').click()
  await pageA.locator('[data-test="user-logout"]').click()
  await pageA.waitForURL((url) => url.pathname.startsWith('/login'))

  // B 独立空态 context 登录同账户：me 带出英文账户值（B 本地从未设过偏好）
  const contextB = await browser.newContext({ storageState: emptyState })
  const pageB = await contextB.newPage()
  expect(await loginAs(pageB, 'admin', '123456')).toBe(true)
  await expect(pageB.locator('[data-test="home-dashboard"]')).toBeVisible()
  await expect(pageB.locator('html')).toHaveAttribute('lang', 'en')

  // B 端还原缺省（跟随系统），等 PUT 落定
  await openDrawer(pageB)
  const restorePromise = nextPreferencesPut(pageB)
  await setSegment(pageB, 'locale', 2)
  expect(await restorePromise).toBe(200)
  await expect(pageB.locator('html')).toHaveAttribute('lang', 'zh-CN')

  await contextA.close()
  await contextB.close()
})
