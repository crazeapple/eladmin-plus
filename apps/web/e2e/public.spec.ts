import { expect, test } from '@playwright/test'

// 公开可达性冒烟（匿名组）：登录守卫上线后，静态资产分发仍是免会话的公开面。

test('静态资产经 gzip_static 提供压缩', async ({ request }) => {
  const home = await request.get('/')
  const asset = (await home.text()).match(/\/assets\/[^"']+\.js/)?.[0]
  expect(asset).toBeTruthy()

  const compressed = await request.get(asset!, { headers: { 'Accept-Encoding': 'gzip' } })
  expect(compressed.status()).toBe(200)
  expect(compressed.headers()['content-encoding']).toBe('gzip')
})
