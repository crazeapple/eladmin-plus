import { expect, test } from '@playwright/test'

import { gotoWithSession } from './helpers'

// 设计 token 基座核验（system 组）：亮色下主色 = 品牌靛蓝基值（--el-color-primary 映射生效），
// 圆角与阴影品牌 token（--ea-*）在册可解析；admin-ui 组件样式对品牌 token 的引用由包内组件测试断言。

test('亮色下主色映射与品牌 token 在册可解析', async ({ page }) => {
  await gotoWithSession(page, '/home')

  // 主色：偏好缺省品牌靛蓝经行内样式写入 --el-color-primary（EP 换肤链路入口）
  const primary = await page.evaluate(() =>
    getComputedStyle(document.documentElement).getPropertyValue('--el-color-primary').trim(),
  )
  expect(primary).toBe('#5d87ff')

  // 品牌层：圆角三档与阴影三档在册（值非空即值表生效）
  for (const token of [
    '--ea-radius-sm',
    '--ea-radius-md',
    '--ea-radius-lg',
    '--ea-shadow-1',
    '--ea-shadow-2',
    '--ea-shadow-3',
  ]) {
    const value = await page.evaluate(
      (name) => getComputedStyle(document.documentElement).getPropertyValue(name).trim(),
      token,
    )
    expect(value, `${token} 应在册可解析`).not.toBe('')
  }

  // 覆盖层：EP 基础圆角与阴影解析后与品牌档位同值（computed 层 var 引用已代入）
  const computedVar = (name: string) =>
    page.evaluate(
      (token) => getComputedStyle(document.documentElement).getPropertyValue(token).trim(),
      name,
    )
  expect(await computedVar('--el-border-radius-base')).toBe(await computedVar('--ea-radius-sm'))
  expect(await computedVar('--el-box-shadow-light')).toBe(await computedVar('--ea-shadow-2'))
})
