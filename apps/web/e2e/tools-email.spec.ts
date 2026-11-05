import { expect, test } from '@playwright/test'

import { gotoWithSession } from './helpers'

// 邮件工具页（A8 负路径）：dev 环境 SMTP 未配置是稳定态——提交即报「邮件服务未启用」，
// 负路径即全链验证；收件人非法走后端 422 页内提示。正路径真发已由后端 GreenMail 测试覆盖。

test('邮件页：非法收件人 422 页内提示、SMTP 未配置报邮件服务未启用', async ({ page }) => {
  await gotoWithSession(page, '/tools/email')
  await expect(page.locator('[data-test="email-page"]')).toBeVisible()

  const to = page.locator('[data-test="email-to"]')
  const subject = page.locator('[data-test="email-subject"]')
  const content = page.locator('[data-test="email-content"]')
  const send = page.locator('[data-test="email-send"]')

  // 收件人非法（后端 422 权威校验）：页内错误提示
  const invalidPromise = page.waitForResponse((r) => {
    return r.url().includes('/tools/email/send') && r.request().method() === 'POST'
  })
  await to.fill('bad-email')
  await subject.fill('E2E 冒烟')
  await content.fill('<p>e2e</p>')
  await send.click()
  expect((await invalidPromise).status()).toBe(422)
  await expect(
    page.locator('.el-message').filter({ hasText: '收件人邮箱格式不合法' }),
  ).toBeVisible()

  // 合法收件人：SMTP 未配置（dev 稳定态）→ 页内报「邮件服务未启用」
  const sendPromise = page.waitForResponse((r) => {
    return r.url().includes('/tools/email/send') && r.request().method() === 'POST'
  })
  await to.fill('eladmin-plus@noreply.local')
  await send.click()
  expect((await sendPromise).status()).toBe(400)
  await expect(page.locator('.el-message').filter({ hasText: '邮件服务未启用' })).toBeVisible()
})
