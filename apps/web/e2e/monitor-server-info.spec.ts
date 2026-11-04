import { expect, test } from '@playwright/test'

import { gotoWithSession } from './helpers'

// 服务监控页主链：指标实时可取（oshi 系统与 JVM、Micrometer 连接池），三仪表与卡片渲染、手动刷新生效。

test('服务监控指标渲染与手动刷新', async ({ page }) => {
  const errors: string[] = []
  page.on('console', (msg) => {
    if (msg.type() === 'error') {
      errors.push(msg.text())
    }
  })
  page.on('pageerror', (error) => errors.push(String(error)))

  await gotoWithSession(page, '/monitor/server-info')
  await expect(page.locator('[data-test="server-info-page"]')).toBeVisible()

  // 三环形仪表：CPU / 内存 / JVM 堆值在册
  await expect(page.locator('[data-test="server-info-cpu-value"]')).toBeVisible()
  await expect(page.locator('[data-test="server-info-memory-value"]')).toBeVisible()
  await expect(page.locator('[data-test="server-info-jvm-value"]')).toBeVisible()

  // 卡片：Swap 与磁盘进度条、连接池统计在册
  await expect(page.locator('[data-test="server-info-swap-progress"]')).toBeVisible()
  await expect(page.locator('[data-test="server-info-pool-active"]')).toBeVisible()

  // 手动刷新取数后页面照常渲染
  await page.locator('[data-test="server-info-refresh"]').click()
  await expect(page.locator('[data-test="server-info-page"]')).toBeVisible()
  await expect(page.locator('[data-test="server-info-cpu-value"]')).toBeVisible()

  expect(errors).toEqual([])
})
