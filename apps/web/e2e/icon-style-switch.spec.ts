import { expect, test, type Page } from '@playwright/test'

import { resetAdminPreferences } from './helpers'

// 风格开关（A1）：偏好抽屉「图标风格」控件写 iconSet 偏好（P-022 链路：
// 本地即时 + PUT 持久），侧栏经统一分派组件响应式换边——在册同义项换 EP 形态、
// 映射缺失项保持原库、切回恢复原渲染；刷新后 iconSet 账户值权威保持。
// 「映射缺失」载体选支付宝工具：nv:wallet-outline 在注册集但无 EP 同义项
// （ionicons 无品牌图形，映射表不收），与在册换边项同屏对照。

/** 登录态进首页（storageState 静默恢复会话，落地 /home） */
async function gotoHome(page: Page): Promise<void> {
  await page.goto('/')
  await expect(page.locator('[data-test="home-dashboard"]')).toBeVisible()
}

/** 等待偏好 PUT 落定并返回状态码（不等即刷新会中断在途写） */
async function nextPreferencesPut(page: Page): Promise<number> {
  const response = await page.waitForResponse(
    (res) => res.url().includes('/profile/preferences') && res.request().method() === 'PUT',
  )
  return response.status()
}

/** 展开侧栏两个目录并取关键图标定位器 */
async function sidebarLocators(page: Page) {
  const sidebar = page.locator('.layout__menu')
  await sidebar.getByText('系统管理', { exact: true }).click()
  await sidebar.getByText('工具', { exact: true }).click()
  const systemIcon = sidebar
    .locator('.el-sub-menu__title', { hasText: '系统管理' })
    .locator('.app-icon')
  const alipayIcon = sidebar
    .locator('.el-menu-item', { hasText: '支付宝工具' })
    .locator('.app-icon')
  return { sidebar, systemIcon, alipayIcon }
}

test('切 EP 风格换边渲染，映射缺失保持原库；刷新保持；切回恢复', async ({ page }) => {
  resetAdminPreferences()
  await gotoHome(page)
  const { sidebar, systemIcon, alipayIcon } = await sidebarLocators(page)

  // 基线（缺省 nv 风格）：目录与映射缺失项同为 ionicons 直包 svg 形态
  await expect(systemIcon).toHaveClass(/app-icon--nv/)
  await expect(systemIcon.locator('> svg')).toBeVisible()
  await expect(alipayIcon.locator('> svg')).toBeVisible()

  // 开设置抽屉切「EP 风格」：本地即时生效 + PUT 持久
  await page.locator('[data-test="settings-trigger"]').click()
  await expect(page.locator('[data-test="settings-drawer"]')).toBeVisible()
  const putPromise = nextPreferencesPut(page)
  await page.locator('[data-test="settings-icon-set"] .el-radio-button').nth(1).click()
  expect(await putPromise).toBe(200)

  // 侧栏响应式换边：在册同义项（cog-outline → setting）换 el-icon 形态；wallet-outline 缺映射保持 nv
  await expect(systemIcon.locator('.el-icon svg')).toBeVisible()
  await expect(alipayIcon.locator('> svg')).toBeVisible()

  // 刷新保持：me 账户值 iconSet=ep 权威下发，侧栏仍 EP 形态
  await gotoHome(page)
  const reloaded = await sidebarLocators(page)
  await expect(reloaded.systemIcon.locator('.el-icon svg')).toBeVisible()
  await expect(reloaded.alipayIcon.locator('> svg')).toBeVisible()

  // 切回 nv 风格：恢复原库渲染（PUT 落定收尾，账户值还原缺省）
  await page.locator('[data-test="settings-trigger"]').click()
  await expect(page.locator('[data-test="settings-drawer"]')).toBeVisible()
  const restorePromise = nextPreferencesPut(page)
  await page.locator('[data-test="settings-icon-set"] .el-radio-button').nth(0).click()
  expect(await restorePromise).toBe(200)
  await expect(reloaded.systemIcon.locator('> svg')).toBeVisible()
  await expect(sidebar.locator('.app-icon--missing')).toHaveCount(0)
})
