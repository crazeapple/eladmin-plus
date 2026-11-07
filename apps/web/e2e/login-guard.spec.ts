import { expect, test } from '@playwright/test'

import { apiLogin, apiLoginAttempt, bearer } from './helpers'

// 登录防护 E2E（部署栈全链）：账号失败锁定（缺省阈值 5，锁定期内正确密码也拒且与密码错同文案）
// 与 IP 限流（超窗 429 统一错误结构）；限流打点逐请求换伪造 X-Forwarded-For——429 出现即证
// nginx 收口 + 后端可信代理口径生效（伪造头不换桶，全部请求落同一真实对端桶）。

test('账号失败锁定：达阈值后锁定期内正确密码也拒且与密码错同文案', async ({ request }) => {
  const admin = await apiLogin(request, 'admin', '123456')

  // 造无角色测试账号（锁定面隔离，不碰 admin 以免污染后续用例）
  const username = `e2eguard${Date.now().toString().slice(-8)}`
  const userRes = await request.post('/api/users', {
    headers: bearer(admin),
    data: {
      username,
      nickName: 'E2E锁定测试',
      email: `${username}@example.com`,
      enabled: 1,
      password: 'e2ePass123',
      roleIds: [],
      jobIds: [],
    },
  })
  expect(userRes.ok()).toBeTruthy()
  const userId = ((await userRes.json()) as { data: number }).data

  // 阈值内失败：常规密码错文案；第 5 次失败达阈值写入锁定
  let wrongMessage = ''
  for (let i = 0; i < 5; i++) {
    const attempt = await apiLoginAttempt(request, username, `wrong-${i}`)
    expect(attempt.status).toBe(401)
    wrongMessage = attempt.body.message
  }
  expect(wrongMessage).not.toBe('')

  // 锁定期内正确密码同样 401，响应文案与密码错一致（不区分锁定与密码错，防枚举）
  const locked = await apiLoginAttempt(request, username, 'e2ePass123')
  expect(locked.status).toBe(401)
  expect(locked.body.message).toBe(wrongMessage)

  // 清理测试账号（锁定键随 TTL 自灭，唯一用户名不留跨场残留）
  expect(
    (await request.delete('/api/users', { headers: bearer(admin), data: [userId] })).ok(),
  ).toBeTruthy()
})

test('IP 限流与伪造头收口：超窗 429 统一结构、窗口滑动恢复', async ({ request }) => {
  // 打满窗口 + 等窗口滑动恢复，远超默认用例超时
  test.setTimeout(180_000)
  // 逐请求换伪造源地址打满窗口：若服务端采信伪造头，各地址各桶永不超窗；
  // 429 在窗口上限内出现即证伪造头未生效 + 限流语义在线（缺省 60 次 / 分钟）
  let limited = false
  for (let i = 0; i < 70; i++) {
    const res = await request.get('/api/auth/captcha', {
      headers: { 'X-Forwarded-For': `10.0.0.${i + 1}` },
    })
    if (res.status() === 429) {
      const body = (await res.json()) as { code: number; message: string }
      expect(body.code).toBe(429)
      expect(body.message).not.toBe('')
      limited = true
      break
    }
    expect(res.status()).toBe(200)
  }
  expect(limited).toBe(true)

  // 窗口滑动恢复：轮询至重新放行（不把限流残留带给同 IP 的后续用例）
  const deadline = Date.now() + 90_000
  let recovered = false
  while (Date.now() < deadline) {
    if ((await request.get('/api/auth/captcha')).status() === 200) {
      recovered = true
      break
    }
    await new Promise((resolve) => setTimeout(resolve, 2_000))
  }
  expect(recovered).toBe(true)
})
