import { expect, test } from '@playwright/test'

import { gotoWithSession } from './helpers'

// 移动端 L2 核验（system 组）：375×667 视口下内页主布局可用——侧栏抽屉可开合、
// 列表页操作可达不破版。

test.use({ viewport: { width: 375, height: 667 } })

test('侧栏抽屉可开合', async ({ page }) => {
  await gotoWithSession(page, '/system/users')
  const aside = page.locator('.layout__aside')
  const trigger = page.locator('.layout__trigger').first()

  // 收起态：侧栏移出视口左缘
  const closedBox = await aside.boundingBox()
  expect(closedBox!.x + closedBox!.width).toBeLessThanOrEqual(0)

  // 打开抽屉：侧栏贴左缘入视口（等 0.2s 过渡完成）
  await trigger.click()
  await expect.poll(async () => (await aside.boundingBox())!.x).toBe(0)

  // 点遮罩收起（点击点避开侧栏覆盖区，取视口右半）：侧栏再次移出
  await page.locator('.layout__overlay').click({ position: { x: 320, y: 333 } })
  await expect
    .poll(async () => {
      const box = await aside.boundingBox()
      return box!.x + box!.width
    })
    .toBeLessThanOrEqual(0)
})

test('列表页操作可达且页面不横向溢出', async ({ page }) => {
  await gotoWithSession(page, '/system/users')

  const table = page.locator('[data-test="users-table"]')
  await expect(table.locator('tbody tr').first()).toBeVisible()

  // 页面不横向溢出：列表容器内部消化横向滚动
  const pageScrollWidth = await page.evaluate(() => document.documentElement.scrollWidth)
  expect(pageScrollWidth).toBeLessThanOrEqual(375)

  // 表单对话框在窄屏封顶不超视口：建一个用户验证全链可用
  await page.locator('[data-test="users-create"]').click()
  const dialog = page.locator('[data-test="users-dialog"]')
  await expect(dialog).toBeVisible()
  // data-test 落在 el-dialog 根元素：直取其包围盒验窄屏封顶
  const dialogBox = await dialog.boundingBox()
  expect(dialogBox!.width).toBeLessThanOrEqual(375)
  const username = `m2e${Date.now().toString().slice(-8)}`
  await dialog.locator('[data-test="users-username"]').fill(username)
  await dialog.locator('[data-test="users-nickname"]').fill('M端E2E用户')
  await dialog.locator('[data-test="users-email"]').fill(`${username}@example.com`)
  await dialog.locator('[data-test="users-password"]').fill('m2ePass123')
  await dialog.getByRole('button', { name: '保存' }).click()
  await expect(dialog).toBeHidden()
  await expect(table.locator('tbody tr', { hasText: username })).toBeVisible()

  // 行操作可达：新行编辑按钮存在可点（横向滚动容器内）
  const editButton = table
    .locator('tbody tr', { hasText: username })
    .locator('[data-test="users-edit"]')
  await expect(editButton).toBeEnabled()
  expect(await editButton.boundingBox()).not.toBeNull()

  // 自净删除:行级删除 + 确认弹窗,行消失——不跨轮累积残留挤占分页(全量跑每轮净增 0)
  await table
    .locator('tbody tr', { hasText: username })
    .locator('[data-test="users-remove"]')
    .click()
  await page.getByRole('button', { name: '确定' }).click()
  await expect(table.getByText(username, { exact: true })).toBeHidden()
})
