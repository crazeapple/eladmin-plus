import { expect, test } from '@playwright/test'

import { gotoWithSession } from './helpers'

// 演示菜单目录化（侧栏消费）：V16 种子生效——「演示」一级目录殿后（侧栏末位、缺省收起），
// 两子菜单挂其下（缓存 / 不缓存），目录图标 V17 迁移后为 nv:code-slash-outline 渲染
// ionicons 形态；
// 页面路由与页内词条不动（/demo/cached 原路径可达，页内标题保持「演示·缓存」语境）。

test('演示目录侧栏殿后收起态，展开两子项且目录图标渲染', async ({ page }) => {
  await gotoWithSession(page, '/home')
  const menu = page.locator('.layout__menu')
  await expect(menu).toBeVisible()

  // 殿后：一级节点末位为演示目录（el-sub-menu 形态，缺省收起子项不展开）
  const topLevel = menu.locator('> li')
  await expect(topLevel).toHaveCount(6)
  const demoNode = topLevel.last()
  await expect(demoNode).toHaveClass(/el-sub-menu/)
  const demoTitle = demoNode.locator('.el-sub-menu__title')
  await expect(demoTitle).toContainText('演示')
  const items = demoNode.locator('.el-menu-item')
  await expect(items.nth(0)).not.toBeVisible()

  // 目录图标：nv:code-slash-outline 渲染 ionicons 形态
  await expect(demoTitle.locator('.app-icon--nv > svg')).toBeVisible()

  // 展开两子项：缓存（sort 1）在前、不缓存（sort 2）殿后
  await demoTitle.click()
  await expect(items).toHaveCount(2)
  await expect(items.nth(0)).toHaveText('缓存')
  await expect(items.nth(1)).toHaveText('不缓存')

  // 子项可达且面包屑目录两级：点「缓存」进 /demo/cached
  await items.nth(0).click()
  await expect(page).toHaveURL(/\/demo\/cached$/)
  const breadcrumb = page.locator('.layout__breadcrumb')
  await expect(breadcrumb).toContainText('演示')
  await expect(breadcrumb).toContainText('缓存')
  // 页内标题词条保持页面语境（不随目录化去前缀）
  await expect(page.getByRole('heading', { name: '演示·缓存' })).toBeVisible()
})
