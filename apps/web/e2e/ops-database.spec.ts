import { expect, test } from '@playwright/test'

import { gotoWithSession } from './helpers'

// 数据库管理页（A1、A2）：V12 种子生效——侧栏「运维」目录 + 数据库管理子菜单在册、路由可达；
// 页面两区块 tabs 渲染且切换正常。

test('运维菜单组：数据库管理入口可达且两区块 tabs 可切换', async ({ page }) => {
  // 侧栏入口导航：运维目录点开 → 子菜单点入
  await gotoWithSession(page, '/home')
  const menu = page.locator('.layout__menu')
  await expect(menu).toBeVisible()
  const opsTitle = menu.locator('.el-sub-menu__title').filter({ hasText: '运维' })
  await expect(opsTitle).toBeVisible()
  await opsTitle.click()
  const entry = menu.locator('.el-menu-item').filter({ hasText: '数据库管理' })
  await expect(entry).toBeVisible()
  await entry.click()
  await expect(page).toHaveURL(/\/database$/)
  await expect(page.locator('[data-test="database-page"]')).toBeVisible()
  // 面包屑含运维目录链
  await expect(page.locator('.layout__breadcrumb')).toContainText('运维')

  // 两区块 tabs：连接配置 / 备份记录，切换换选区（页签头无属性透传按角色定位；
  // 面板经 v-show 常驻，骨架期空占位零面积，以挂载与选区判定）
  const configTab = page.getByRole('tab', { name: '连接配置' })
  const recordTab = page.getByRole('tab', { name: '备份记录' })
  await expect(configTab).toBeVisible()
  await expect(recordTab).toBeVisible()
  const configPanel = page.locator('[data-test="database-config-panel"]')
  await expect(configPanel).toBeAttached()
  await expect(configTab).toHaveAttribute('aria-selected', 'true')

  await recordTab.click()
  await expect(page.locator('[data-test="database-record-panel"]')).toBeAttached()
  await expect(recordTab).toHaveAttribute('aria-selected', 'true')
  await expect(configTab).toHaveAttribute('aria-selected', 'false')
  await configTab.click()
  await expect(configPanel).toBeAttached()
  await expect(configTab).toHaveAttribute('aria-selected', 'true')

  // 深链直达同页可达
  await gotoWithSession(page, '/database')
  await expect(page.locator('[data-test="database-tabs"]')).toBeVisible()
})
