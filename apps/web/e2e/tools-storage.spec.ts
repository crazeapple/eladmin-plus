import { readFileSync } from 'node:fs'
import { fileURLToPath, URL } from 'node:url'

import { expect, test } from '@playwright/test'

import { gotoWithSession } from './helpers'

// 存储管理页主链（A3、A4）：页内真实上传 → 行渲染（原名 / 大小 / 上传人 / 时间）→
// 原名模糊 / 上传人 / 时间范围三维筛选 → 删除确认后行消失（造数自清，不留测试数据）。
// 图片预览与下载（A5）：图片行缩略（blob 经鉴权下载）→ 点开大图 → 行下载原名落地。

const AVATAR_PNG = readFileSync(fileURLToPath(new URL('./fixtures/avatar.png', import.meta.url)))

function localDate(offsetDays = 0): string {
  const now = new Date()
  now.setDate(now.getDate() + offsetDays)
  const pad = (n: number) => String(n).padStart(2, '0')
  return `${now.getFullYear()}-${pad(now.getMonth() + 1)}-${pad(now.getDate())}`
}

test('存储页主链：上传、行渲染、三维筛选与删除确认', async ({ page }) => {
  await gotoWithSession(page, '/tools/storage')
  const table = page.locator('[data-test="storage-table"]')
  // 数据行或空态其一可见再分支（消加载竞态）
  await expect(table.or(page.locator('[data-test="admin-table-empty"]')).first()).toBeVisible()

  const uniqueName = `e2e-storage-${Date.now()}.txt`
  await page.locator('[data-test="storage-upload-picker"] input[type="file"]').setInputFiles({
    name: uniqueName,
    mimeType: 'text/plain',
    buffer: Buffer.from('e2e storage upload content'),
  })
  await expect(page.locator('.el-message').filter({ hasText: '已上传' })).toBeVisible()

  // 行渲染：原名、大小（字节人性化）、上传人、时间
  const row = table.locator('tbody tr').filter({ hasText: uniqueName })
  await expect(row).toBeVisible()
  await expect(row).toContainText('admin')
  await expect(row).toContainText(' B')

  // 原名模糊筛选：命中目标行
  const nameFilter = page.locator('[data-test="storage-filter-name"]')
  await nameFilter.fill(uniqueName)
  await nameFilter.press('Enter')
  await expect(table.locator('tbody tr').filter({ hasText: uniqueName })).toBeVisible()

  // 上传人筛选（原名条件清掉）
  await nameFilter.fill('')
  await nameFilter.press('Enter')
  const uploaderFilter = page.locator('[data-test="storage-filter-uploader"]')
  await uploaderFilter.fill('admin')
  await uploaderFilter.press('Enter')
  await expect(table.locator('tbody tr').filter({ hasText: uniqueName })).toBeVisible()

  // 时间范围：今天含该行（EP 日期范围编辑器不透传 data-test，按类定位）
  const today = localDate()
  const rangeInputs = page.locator('.storage__filter .el-range-editor input')
  await rangeInputs.first().fill(today)
  await rangeInputs.nth(1).fill(today)
  await rangeInputs.nth(1).press('Enter')
  await expect(table.locator('tbody tr').filter({ hasText: uniqueName })).toBeVisible()

  // 筛选无结果空态占位
  await nameFilter.fill('e2e-no-such-file')
  await nameFilter.press('Enter')
  await expect(page.locator('[data-test="admin-table-empty"]')).toBeVisible()
  await nameFilter.fill(uniqueName)
  await nameFilter.press('Enter')
  await expect(row).toBeVisible()

  // 删除确认：行消失（对话框按名定位——日期弹层 role 也是 dialog，避免 strict 歧义）
  await row.locator('[data-test="storage-remove"]').click()
  const dialog = page.getByRole('dialog', { name: '删除确认' })
  await expect(dialog).toBeVisible()
  await expect(dialog).toContainText(uniqueName)
  await dialog.getByRole('button', { name: '确定' }).click()
  await expect(page.locator('.el-message').filter({ hasText: '删除成功' })).toBeVisible()
  await expect(row).toBeHidden()
})

test('存储页图片预览与下载：缩略点开大图、行下载原名落地', async ({ page }) => {
  await gotoWithSession(page, '/tools/storage')
  const table = page.locator('[data-test="storage-table"]')
  await expect(table.or(page.locator('[data-test="admin-table-empty"]')).first()).toBeVisible()

  // 上传真实 PNG（走完整存储链）
  const pngName = `e2e-img-${Date.now()}.png`
  await page.locator('[data-test="storage-upload-picker"] input[type="file"]').setInputFiles({
    name: pngName,
    mimeType: 'image/png',
    buffer: AVATAR_PNG,
  })
  const row = table.locator('tbody tr').filter({ hasText: pngName })
  await expect(row).toBeVisible()

  // 缩略：blob 对象 URL 经鉴权下载供给
  const thumb = row.locator('[data-test="storage-thumb"] img')
  await expect(thumb).toBeVisible()
  expect((await thumb.getAttribute('src')) ?? '').toMatch(/^blob:/)

  // 点开大图预览，Esc 关闭
  await thumb.click()
  await expect(page.locator('.el-image-viewer__wrapper')).toBeVisible()
  await page.keyboard.press('Escape')
  await expect(page.locator('.el-image-viewer__wrapper')).toBeHidden()

  // 行下载：blob 落盘原名文件
  const downloadPromise = page.waitForEvent('download')
  await row.locator('[data-test="storage-download"]').click()
  const download = await downloadPromise
  expect(download.suggestedFilename()).toBe(pngName)
  await expect(page.locator('.el-message').filter({ hasText: '已下载' })).toBeVisible()

  // 清理：删除该图片行
  await row.locator('[data-test="storage-remove"]').click()
  const dialog = page.getByRole('dialog', { name: '删除确认' })
  await expect(dialog).toBeVisible()
  await dialog.getByRole('button', { name: '确定' }).click()
  await expect(row).toBeHidden()
})
