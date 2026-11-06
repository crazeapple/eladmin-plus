import { expect, test } from '@playwright/test'

import { loginAs, resetAdminPreferences } from './helpers'

// 匿名期偏好（anonymous 组）：改语言只落本地临时使用（不上推后端），
// 登录后 me 账户值权威覆盖——界面语言回到账户值而非匿名值。

const STORAGE_KEY = 'eladmin-plus:preferences'

test('匿名期改语言登录后账户值覆盖', async ({ page }) => {
  // 账户偏好归零（缺省）：登录后的覆盖语义对照恒为「缺省 system → 中文」
  resetAdminPreferences()
  // 匿名期本地改语言：等价于匿名用户在本地把语言切到英文
  await page.addInitScript(
    ([key]) =>
      localStorage.setItem(
        key!,
        JSON.stringify({ theme: 'auto', primaryColor: '#5d87ff', locale: 'en', iconSet: 'svg' }),
      ),
    [STORAGE_KEY],
  )

  // 匿名值不上推：全程监听偏好写口，出现 PUT 即违例
  const putUrls: string[] = []
  page.on('request', (req) => {
    if (req.url().includes('/profile/preferences') && req.method() === 'PUT') {
      putUrls.push(req.url())
    }
  })

  await page.goto('/login')
  await expect(page.locator('html')).toHaveAttribute('lang', 'en')

  // 登录：管理员账户语言为缺省 system，随浏览器语言解析为 zh-CN
  expect(await loginAs(page, 'admin', '123456')).toBe(true)
  await expect(page.locator('[data-test="home-dashboard"]')).toBeVisible()

  // 账户值权威：匿名英文被覆盖，界面回到中文
  await expect(page.locator('html')).toHaveAttribute('lang', 'zh-CN')
  expect(putUrls).toEqual([])
})
