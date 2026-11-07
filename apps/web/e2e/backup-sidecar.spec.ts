import { gunzipSync } from 'node:zlib'

import { expect, test } from '@playwright/test'

import { apiLogin, bearer } from './helpers'

// 备份 sidecar 化全链：server 镜像无 docker CLI、compose 无 docker.sock 挂载，备份链路改为
// HTTP 调 compose 内网备份 sidecar（自带 dump 客户端直连 mysql 容器）。触发备份 → sidecar 转储
// → storage_file 落册 → 下载，内容为合法 gzip SQL（解压即建库建表语句）；正规出口导出同内容。

test('备份 sidecar 全链：触发备份落册，下载与正规导出均为合法 gzip SQL', async ({ request }) => {
  const admin = await apiLogin(request, 'admin', '123456')

  // 造连接配置（指向栈内 mysql）并触发一次真实备份
  const configRes = await request.post('/api/database/configs', {
    headers: bearer(admin),
    data: {
      host: 'eladmin-plus-prod-mysql',
      port: 3306,
      name: 'eladmin_plus',
      userName: 'root',
      password: 'root',
      remark: `e2e-sidecar-${Date.now()}`,
    },
  })
  expect(configRes.ok()).toBeTruthy()
  const configId = ((await configRes.json()) as { data: number }).data
  const backupRes = await request.post(`/api/database/configs/${configId}/backup`, {
    headers: bearer(admin),
  })
  expect(backupRes.ok()).toBeTruthy()
  const backup = (await backupRes.json()).data as { id: number; fileName: string; dbName: string }
  expect(backup.dbName).toBe('eladmin_plus')
  expect(backup.fileName).toMatch(/^eladmin_plus_\d{14}\.sql$/)

  // 通用下载口：gzip 魔数 + 解压即 SQL（含建库与建表语句）
  const download = await request.get(`/api/storage/files/${backup.id}/download`, {
    headers: bearer(admin),
  })
  expect(download.status()).toBe(200)
  const gz = await download.body()
  expect(gz[0]).toBe(0x1f)
  expect(gz[1]).toBe(0x8b)
  const sql = gunzipSync(gz).toString('utf8')
  expect(sql).toContain('CREATE DATABASE')
  expect(sql).toContain('CREATE TABLE')

  // 正规出口（备份记录导出）：同样为合法 gzip SQL
  const exportRes = await request.get(`/api/database/records/${backup.id}/export`, {
    headers: bearer(admin),
  })
  expect(exportRes.status()).toBe(200)
  expect(gunzipSync(await exportRes.body()).toString('utf8')).toContain('CREATE TABLE')

  // 自清：备份记录与连接配置（造数不留场）
  expect(
    (await request.delete(`/api/database/records/${backup.id}`, { headers: bearer(admin) })).ok(),
  ).toBeTruthy()
  expect(
    (await request.delete(`/api/database/configs/${configId}`, { headers: bearer(admin) })).ok(),
  ).toBeTruthy()
})
