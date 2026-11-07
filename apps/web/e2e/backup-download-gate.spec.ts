import { expect, test } from '@playwright/test'

import { apiLogin, bearer } from './helpers'

// 备份下载门禁：source=backup 行经通用下载口要求 database:backup 权限点——root（直通）200，
// 无权限登录账号 403（同一备份 id 同一口径）；普通文件维持「登录即可下载」语义（无权限账号同样 200）。

test('备份下载门禁：无权限账号 403、root 200、普通文件登录即可', async ({ request }) => {
  const admin = await apiLogin(request, 'admin', '123456')

  // 造连接配置（指向栈内 mysql）并触发一次真实备份，取落册 storage_file id
  const configRes = await request.post('/api/database/configs', {
    headers: bearer(admin),
    data: {
      host: 'eladmin-plus-prod-mysql',
      port: 3306,
      name: 'eladmin_plus',
      userName: 'root',
      password: 'root',
      remark: `e2e-gate-${Date.now()}`,
    },
  })
  expect(configRes.ok()).toBeTruthy()
  const configId = ((await configRes.json()) as { data: number }).data
  const backupRes = await request.post(`/api/database/configs/${configId}/backup`, {
    headers: bearer(admin),
  })
  expect(backupRes.ok()).toBeTruthy()
  const backup = (await backupRes.json()).data as { id: number; fileName: string }
  expect(backup.fileName).toMatch(/^eladmin_plus_\d{14}\.sql$/)

  // 无权限登录账号：无角色用户（权限点空集）
  const username = `e2egate${Date.now().toString().slice(-8)}`
  const userRes = await request.post('/api/users', {
    headers: bearer(admin),
    data: {
      username,
      nickName: 'E2E门禁用户',
      email: `${username}@example.com`,
      enabled: 1,
      password: 'e2ePass123',
      roleIds: [],
      jobIds: [],
    },
  })
  expect(userRes.ok()).toBeTruthy()
  const userId = ((await userRes.json()) as { data: number }).data
  const limited = await apiLogin(request, username, 'e2ePass123')

  // 门禁本体：同一备份 id，无权限 403（统一错误结构）、root 200 且下载头带原名
  const denied = await request.get(`/api/storage/files/${backup.id}/download`, {
    headers: bearer(limited),
  })
  expect(denied.status()).toBe(403)
  expect(((await denied.json()) as { code: number }).code).toBe(403)
  const allowed = await request.get(`/api/storage/files/${backup.id}/download`, {
    headers: bearer(admin),
  })
  expect(allowed.status()).toBe(200)
  const bytes = await allowed.body()
  expect(bytes.length).toBeGreaterThan(0)
  expect(allowed.headers()['content-disposition']).toContain(backup.fileName)

  // 普通文件回归：无权限账号下载同样 200（登录即可语义不受门禁波及）
  const normalRes = await request.post('/api/storage/files', {
    headers: bearer(admin),
    multipart: {
      file: {
        name: `e2e-gate-normal-${Date.now()}.txt`,
        mimeType: 'text/plain',
        buffer: Buffer.from('plain-e2e-bytes'),
      },
    },
  })
  expect(normalRes.ok()).toBeTruthy()
  const normalId = ((await normalRes.json()) as { data: { id: number } }).data.id
  const normal = await request.get(`/api/storage/files/${normalId}/download`, {
    headers: bearer(limited),
  })
  expect(normal.status()).toBe(200)
  expect(await normal.text()).toBe('plain-e2e-bytes')

  // 自清：备份记录、普通文件、连接配置、用户（造数不留场）
  expect(
    (await request.delete(`/api/database/records/${backup.id}`, { headers: bearer(admin) })).ok(),
  ).toBeTruthy()
  expect(
    (await request.delete(`/api/storage/files/${normalId}`, { headers: bearer(admin) })).ok(),
  ).toBeTruthy()
  expect(
    (await request.delete(`/api/database/configs/${configId}`, { headers: bearer(admin) })).ok(),
  ).toBeTruthy()
  expect(
    (await request.delete('/api/users', { headers: bearer(admin), data: [userId] })).ok(),
  ).toBeTruthy()
})
