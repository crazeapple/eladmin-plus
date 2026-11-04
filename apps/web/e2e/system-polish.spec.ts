import { expect, test } from '@playwright/test'

import { gotoWithSession } from './helpers'

// 打磨随行冒烟：身份域四页、配置域三页与监控域四页在移动端视口与深色主题下渲染正常、无控制台错误。

const PAGES: Array<[string, string]> = [
  ['/system/users', 'users-page'],
  ['/system/roles', 'roles-page'],
  ['/system/depts', 'depts-page'],
  ['/system/jobs', 'jobs-page'],
  ['/system/menus', 'menus-page'],
  ['/system/dicts', 'dicts-page'],
  ['/system/tasks', 'tasks-page'],
  ['/monitor/operation-logs', 'op-logs-page'],
  ['/monitor/exception-logs', 'ex-logs-page'],
  ['/monitor/online-users', 'online-users-page'],
  ['/monitor/server-info', 'server-info-page'],
]

test('移动端视口：十一页渲染无控制台错误', async ({ page }) => {
  const errors: string[] = []
  page.on('console', (msg) => {
    if (msg.type() === 'error') {
      errors.push(msg.text())
    }
  })
  page.on('pageerror', (error) => errors.push(String(error)))

  await page.setViewportSize({ width: 375, height: 720 })
  for (const [path, pageTest] of PAGES) {
    await gotoWithSession(page, path)
    await expect(page.locator(`[data-test="${pageTest}"]`)).toBeVisible()
  }
  expect(errors).toEqual([])
})

test('深色主题：十一页渲染无控制台错误', async ({ page }) => {
  const errors: string[] = []
  page.on('console', (msg) => {
    if (msg.type() === 'error') {
      errors.push(msg.text())
    }
  })
  page.on('pageerror', (error) => errors.push(String(error)))

  await page.setViewportSize({ width: 1280, height: 800 })
  await gotoWithSession(page, '/home')
  await page.locator('[data-test="settings-trigger"]').click()
  await page.locator('[data-test="settings-theme"]').getByText('深色').click()
  await expect(page.locator('html')).toHaveClass(/dark/)

  for (const [path, pageTest] of PAGES) {
    await gotoWithSession(page, path)
    await expect(page.locator(`[data-test="${pageTest}"]`)).toBeVisible()
  }
  // 还原浅色，避免污染后续用例的偏好
  await page.locator('[data-test="settings-trigger"]').click()
  await page.locator('[data-test="settings-theme"]').getByText('浅色').click()
  await expect(page.locator('html')).not.toHaveClass(/dark/)
  expect(errors).toEqual([])
})
