import { expect, test } from '@playwright/test'

import { gotoWithSession } from './helpers'

// 工具菜单组：V10 种子生效——侧栏「工具」目录 + 三子菜单在册，路由可达、页面渲染。

test('工具菜单组：侧栏三子菜单在册且逐页可达', async ({ page }) => {
  await gotoWithSession(page, '/home')
  const menu = page.locator('.layout__menu')
  await expect(menu).toBeVisible()

  // 工具目录在册：点开子菜单
  const toolsTitle = menu.locator('.el-sub-menu__title').filter({ hasText: '工具' })
  await expect(toolsTitle).toBeVisible()
  await toolsTitle.click()

  const pages = [
    { title: '存储管理', path: '/tools/storage', marker: '[data-test="storage-page"]' },
    { title: '邮件工具', path: '/tools/email', marker: '[data-test="email-page"]' },
    { title: '支付宝工具', path: '/tools/ali-pay', marker: '[data-test="ali-pay-page"]' },
  ]
  for (const item of pages) {
    const entry = menu.locator('.el-menu-item').filter({ hasText: item.title })
    await expect(entry).toBeVisible()
    await entry.click()
    await expect(page).toHaveURL(new RegExp(`${item.path.replace('/', '\\/')}$`))
    await expect(page.locator(item.marker)).toBeVisible()
    // 面包屑含工具目录链
    await expect(page.locator('.layout__breadcrumb')).toContainText('工具')
  }
})
