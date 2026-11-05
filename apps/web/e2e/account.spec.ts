import { readFileSync } from 'node:fs'
import { fileURLToPath, URL } from 'node:url'

import { expect, test } from '@playwright/test'

import { gotoWithSession } from './helpers'

// 个人中心入口与页骨架：顶栏用户下拉「个人中心」项启用可点，跳 /account 固定路由页渲染
// （tabs 双页签在册：资料与头像 / 改密，表单随对应任务落位）。

const AVATAR_PNG = readFileSync(fileURLToPath(new URL('./fixtures/avatar.png', import.meta.url)))

test('个人中心入口：顶栏下拉可点并跳 /account 渲染', async ({ page }) => {
  await gotoWithSession(page, '/home')
  await page.locator('[data-test="user-avatar"]').click()

  const profileItem = page.locator('[data-test="user-profile"]')
  await expect(profileItem).toBeVisible()
  await expect(profileItem).not.toHaveClass(/is-disabled/)
  await profileItem.click()

  await expect(page).toHaveURL(/\/account$/)
  await expect(page.locator('[data-test="account-page"]')).toBeVisible()
  // tabs 双页签在册（表单随对应任务落位）
  await expect(page.getByRole('tab', { name: '资料与头像' })).toBeVisible()
  await expect(page.getByRole('tab', { name: '改密' })).toBeVisible()
})

test('资料改：昵称生效且 me 刷新回填；邮箱非法 422 页内提示', async ({ page }) => {
  await gotoWithSession(page, '/account')
  // el-input 透传属性落内层 input：data-test 即输入元素本体
  const nickname = page.locator('[data-test="account-profile-nickname"]')
  // 先等 me 回填到位再改（回填 watch 会覆盖早于它的输入）
  await expect(nickname).toHaveValue('系统管理员')

  /**
   * 保存并等落定：PUT 响应是权威落定信号；成功保存后 me 缓存失效触发重取，
   * 再等 me GET 回来（回填 watch 会覆盖其后的输入，不等它后续 fill 会被吞）。
   */
  const saveAndWait = async () => {
    const responsePromise = page.waitForResponse((r) => {
      return r.url().includes('/auth/me/profile') && r.request().method() === 'PUT'
    })
    await page.locator('[data-test="account-profile-save"]').click()
    const status = (await responsePromise).status()
    if (status === 200) {
      await page.waitForResponse((r) => {
        const url = new URL(r.url())
        return url.pathname.endsWith('/auth/me') && r.request().method() === 'GET'
      })
    }
    return status
  }

  // 改昵称保存：成功提示且 me 刷新后表单回填新昵称
  await nickname.fill('系统管理员E2E')
  expect(await saveAndWait()).toBe(200)
  await expect(nickname).toHaveValue('系统管理员E2E')

  // 邮箱非法（后端 422）：页内错误提示
  await page.locator('[data-test="account-profile-email"]').fill('bad-email')
  expect(await saveAndWait()).toBe(422)
  await expect(page.locator('.el-message').filter({ hasText: '邮箱格式不合法' })).toBeVisible()

  // 还原昵称，不污染共享开发数据
  await page.locator('[data-test="account-profile-email"]').fill('')
  await nickname.fill('系统管理员')
  expect(await saveAndWait()).toBe(200)
  await expect(nickname).toHaveValue('系统管理员')
})

test('昵称必填：清空昵称保存被拦且不调端点', async ({ page }) => {
  await gotoWithSession(page, '/account')
  const nickname = page.locator('[data-test="account-profile-nickname"]')
  await expect(nickname).toHaveValue('系统管理员')

  await nickname.fill('')
  await page.locator('[data-test="account-profile-save"]').click()
  await expect(page.locator('.el-form-item__error').filter({ hasText: '昵称必填' })).toBeVisible()
  await expect(page.locator('.el-message').filter({ hasText: '资料已保存' })).toBeHidden()
})

test('头像：选图弹窗裁剪上传后头像更新；清空确认后恢复默认占位', async ({ page }) => {
  await gotoWithSession(page, '/account')
  await expect(page.locator('[data-test="account-profile-nickname"]')).toHaveValue('系统管理员')

  // 选图（页内真实文件选择）→ 裁剪框出现 → 确认上传
  await page.locator('[data-test="account-avatar-change"]').click()
  const dialog = page.locator('[data-test="account-crop-dialog"]')
  await dialog
    .locator('input[type="file"]')
    .setInputFiles({ name: 'avatar.png', mimeType: 'image/png', buffer: AVATAR_PNG })
  await expect(dialog.locator('.vue-cropper')).toBeVisible()
  await page.locator('[data-test="account-crop-confirm"]').click()
  await expect(page.locator('.el-message').filter({ hasText: '头像已更新' })).toBeVisible()

  // me 刷新：页内头像与顶栏头像都走新头像
  await expect(page.locator('[data-test="account-avatar"] img')).toBeVisible()
  await expect(page.locator('[data-test="user-avatar-img"]')).toBeVisible()

  // 清空：二次确认后恢复默认占位，顶栏头像退回图标
  await page.locator('[data-test="account-avatar-clear"]').click()
  await page.getByRole('dialog').last().getByRole('button', { name: '确定' }).click()
  await expect(page.locator('.el-message').filter({ hasText: '头像已清空' })).toBeVisible()
  await expect(page.locator('[data-test="account-avatar-default"]')).toBeVisible()
  await expect(page.locator('[data-test="user-avatar-img"]')).toBeHidden()
})
