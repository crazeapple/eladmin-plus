import { test as setup } from '@playwright/test'
import { loginAsAdmin } from './helpers'

/**
 * 全局登录 setup：管理员登录一次，会话以 refresh cookie 形态存 storageState；
 * 后续 system project 各用例带 cookie 进内页，守卫静默恢复会话直接落地。
 */
setup('管理员登录并落会话态', async ({ page }) => {
  await loginAsAdmin(page)
  await page.context().storageState({ path: '.auth/state.json' })
})
