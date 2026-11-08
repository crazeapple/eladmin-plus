import { expect, test } from '@playwright/test'

import { gotoWithSession } from './helpers'

// 数据库管理页：V12 种子生效——侧栏「运维」目录 + 数据库管理子菜单在册、路由可达；
// 页面两区块 tabs 渲染且切换正常。

test('运维菜单组：数据库管理入口可达且两区块 tabs 可切换', async ({ page }) => {
  // 侧栏入口导航：运维目录点开 → 子菜单点入
  await gotoWithSession(page, '/home')
  const menu = page.locator('.layout__menu')
  await expect(menu).toBeVisible()
  const opsTitle = menu.locator('.el-sub-menu__title').filter({ hasText: '运维' })
  await expect(opsTitle).toBeVisible()
  await opsTitle.click()
  const entry = menu.locator('.el-menu-item').filter({ hasText: '数据库管理' })
  await expect(entry).toBeVisible()
  await entry.click()
  await expect(page).toHaveURL(/\/database$/)
  await expect(page.locator('[data-test="database-page"]')).toBeVisible()
  // 面包屑含运维目录链
  await expect(page.locator('.layout__breadcrumb')).toContainText('运维')
  // 权限点全可见兜底（root）：新增按钮在册（行级四类按钮随配置 / 记录链用例逐一消费）
  await expect(page.locator('[data-test="database-config-create"]')).toBeVisible()

  // 两区块 tabs：连接配置 / 备份记录，切换换选区（页签头无属性透传按角色定位；
  // 面板经 v-show 常驻，骨架期空占位零面积，以挂载与选区判定）
  const configTab = page.getByRole('tab', { name: '连接配置' })
  const recordTab = page.getByRole('tab', { name: '备份记录' })
  await expect(configTab).toBeVisible()
  await expect(recordTab).toBeVisible()
  const configPanel = page.locator('[data-test="database-config-panel"]')
  await expect(configPanel).toBeAttached()
  await expect(configTab).toHaveAttribute('aria-selected', 'true')

  await recordTab.click()
  await expect(page.locator('[data-test="database-record-panel"]')).toBeAttached()
  await expect(recordTab).toHaveAttribute('aria-selected', 'true')
  await expect(configTab).toHaveAttribute('aria-selected', 'false')
  await configTab.click()
  await expect(configPanel).toBeAttached()
  await expect(configTab).toHaveAttribute('aria-selected', 'true')

  // 深链直达同页可达
  await gotoWithSession(page, '/database')
  await expect(page.locator('[data-test="database-tabs"]')).toBeVisible()
})

test('连接配置链：必填拦截、新增、编辑密码留空保持、删除确认', async ({ page }) => {
  await gotoWithSession(page, '/database')
  const table = page.locator('[data-test="database-config-table"]')
  // 数据行或空态其一可见再分支（消加载竞态）
  await expect(table.or(page.locator('[data-test="admin-table-empty"]')).first()).toBeVisible()

  // 必填校验拦截：空表单提交出字段级错误，不落任何行
  await page.locator('[data-test="database-config-create"]').click()
  const dialog = page.locator('[data-test="database-config-dialog"]')
  await expect(dialog).toBeVisible()
  await dialog.getByRole('button', { name: '保存' }).click()
  await expect(dialog.locator('[data-test="auto-form-field-error"]').first()).toBeVisible()
  await dialog.locator('.el-dialog__headerbtn').click()
  await expect(dialog).toBeHidden()

  // 新增：全链落库成功后行渲染（备注为唯一定位标记）
  const remark = `e2e-db-config-${Date.now()}`
  await page.locator('[data-test="database-config-create"]').click()
  await expect(dialog).toBeVisible()
  await dialog.locator('[data-field="host"] input').fill('eladmin-plus-prod-mysql')
  await dialog.locator('[data-field="port"] input').fill('3306')
  await dialog.locator('[data-field="name"] input').fill('eladmin_plus')
  await dialog.locator('[data-field="userName"] input').fill('root')
  await dialog.locator('[data-field="password"] input').fill('root')
  await dialog.locator('[data-field="remark"] input').fill(remark)
  await dialog.getByRole('button', { name: '保存' }).click()
  await expect(page.locator('.el-message').filter({ hasText: '保存成功' })).toBeVisible()
  const row = table.locator('tbody tr').filter({ hasText: remark })
  await expect(row).toBeVisible()

  // 编辑：密码不回显且占位明示留空保持；改备注保存生效
  await row.locator('[data-test="database-config-edit"]').click()
  await expect(dialog).toBeVisible()
  const passwordInput = dialog.locator('[data-field="password"] input')
  await expect(passwordInput).toHaveValue('')
  await expect(passwordInput).toHaveAttribute('placeholder', '留空保持原密码')
  const editedRemark = `${remark}-edited`
  await dialog.locator('[data-field="remark"] input').fill(editedRemark)
  await dialog.getByRole('button', { name: '保存' }).click()
  await expect(page.locator('.el-message').filter({ hasText: '保存成功' })).toBeVisible()
  const editedRow = table.locator('tbody tr').filter({ hasText: editedRemark })
  await expect(editedRow).toBeVisible()

  // 删除确认：行消失（造数自清，不留测试数据）
  await editedRow.locator('[data-test="database-config-delete"]').click()
  const confirmDialog = page.getByRole('dialog', { name: '删除确认' })
  await expect(confirmDialog).toBeVisible()
  await confirmDialog.getByRole('button', { name: '确定' }).click()
  await expect(page.locator('.el-message').filter({ hasText: '删除成功' })).toBeVisible()
  await expect(editedRow).toBeHidden()
})

test('备份触发：真实备份成功提示落册文件名、错误凭据页内报「备份执行失败」', async ({ page }) => {
  await gotoWithSession(page, '/database')
  const table = page.locator('[data-test="database-config-table"]')
  // 数据行或空态其一可见再分支（消加载竞态）
  await expect(table.or(page.locator('[data-test="admin-table-empty"]')).first()).toBeVisible()

  // 造两行配置：正确凭据指向栈内 mysql；错误凭据构造稳定失败场景
  const dialog = page.locator('[data-test="database-config-dialog"]')
  const createConfig = async (remark: string, password: string) => {
    await page.locator('[data-test="database-config-create"]').click()
    await expect(dialog).toBeVisible()
    await dialog.locator('[data-field="host"] input').fill('eladmin-plus-prod-mysql')
    await dialog.locator('[data-field="port"] input').fill('3306')
    await dialog.locator('[data-field="name"] input').fill('eladmin_plus')
    await dialog.locator('[data-field="userName"] input').fill('root')
    await dialog.locator('[data-field="password"] input').fill(password)
    await dialog.locator('[data-field="remark"] input').fill(remark)
    await dialog.getByRole('button', { name: '保存' }).click()
    await expect(page.locator('.el-message').filter({ hasText: '保存成功' })).toBeVisible()
    return table.locator('tbody tr').filter({ hasText: remark })
  }
  const okRemark = `e2e-db-bak-ok-${Date.now()}`
  const badRemark = `e2e-db-bak-bad-${Date.now()}`
  const okRow = await createConfig(okRemark, 'root')
  await expect(okRow).toBeVisible()
  const badRow = await createConfig(badRemark, 'wrong-pass')
  await expect(badRow).toBeVisible()

  const backupDialog = page.getByRole('dialog', { name: '备份确认' })

  // 正路径：确认后真实 mysqldump（数秒），成功提示带落册文件名
  await okRow.locator('[data-test="database-config-backup"]').click()
  await expect(backupDialog).toBeVisible()
  await backupDialog.getByRole('button', { name: '确定' }).click()
  const successToast = page.locator('.el-message').filter({ hasText: '备份完成' })
  await expect(successToast).toBeVisible({ timeout: 60_000 })
  const toastText = (await successToast.first().textContent()) ?? ''
  const fileName = toastText.replace(/^.*备份完成：/, '').trim()
  expect(fileName).toMatch(/^eladmin_plus_\d{14}\.sql\.gz$/)

  // 负路径：错误凭据 5xx 页内提示（含后端原因），无成功提示
  await badRow.locator('[data-test="database-config-backup"]').click()
  await expect(backupDialog).toBeVisible()
  await backupDialog.getByRole('button', { name: '确定' }).click()
  const backupError = page.locator('[data-test="database-backup-error"]')
  await expect(backupError).toBeVisible({ timeout: 60_000 })
  await expect(backupError).toContainText('备份执行失败')

  // 清理一：配置两行删除（自清，不留测试数据）
  for (const row of [okRow, badRow]) {
    await row.locator('[data-test="database-config-delete"]').click()
    const confirmDialog = page.getByRole('dialog', { name: '删除确认' })
    await expect(confirmDialog).toBeVisible()
    await confirmDialog.getByRole('button', { name: '确定' }).click()
    await expect(row).toBeHidden()
  }

  // 清理二：备份文件经存储页删除（记录视图随源删，记录区不留残行）
  await gotoWithSession(page, '/tools/storage')
  const storageTable = page.locator('[data-test="storage-table"]')
  await expect(
    storageTable.or(page.locator('[data-test="admin-table-empty"]')).first(),
  ).toBeVisible()
  const nameFilter = page.locator('[data-test="storage-filter-name"]')
  await nameFilter.fill(fileName)
  await nameFilter.press('Enter')
  const fileRow = storageTable.locator('tbody tr').filter({ hasText: fileName })
  await expect(fileRow).toBeVisible()
  await fileRow.locator('[data-test="storage-remove"]').click()
  const fileDialog = page.getByRole('dialog', { name: '删除确认' })
  await expect(fileDialog).toBeVisible()
  await fileDialog.getByRole('button', { name: '确定' }).click()
  await expect(fileRow).toBeHidden()
})

test('备份记录链：备份出新行、文件名筛选命中与无命中、行级导出原名落地、删除后行消失', async ({
  page,
}) => {
  await gotoWithSession(page, '/database')
  const configTable = page.locator('[data-test="database-config-table"]')
  await expect(
    configTable.or(page.locator('[data-test="admin-table-empty"]')).first(),
  ).toBeVisible()

  // 造配置并触发一次真实备份（唯一记录行由此产生，库名前缀 + 时间戳文件名）
  const dialog = page.locator('[data-test="database-config-dialog"]')
  const remark = `e2e-db-rec-${Date.now()}`
  await page.locator('[data-test="database-config-create"]').click()
  await expect(dialog).toBeVisible()
  await dialog.locator('[data-field="host"] input').fill('eladmin-plus-prod-mysql')
  await dialog.locator('[data-field="port"] input').fill('3306')
  await dialog.locator('[data-field="name"] input').fill('eladmin_plus')
  await dialog.locator('[data-field="userName"] input').fill('root')
  await dialog.locator('[data-field="password"] input').fill('root')
  await dialog.locator('[data-field="remark"] input').fill(remark)
  await dialog.getByRole('button', { name: '保存' }).click()
  await expect(page.locator('.el-message').filter({ hasText: '保存成功' })).toBeVisible()
  const configRow = configTable.locator('tbody tr').filter({ hasText: remark })
  await expect(configRow).toBeVisible()

  await configRow.locator('[data-test="database-config-backup"]').click()
  const backupDialog = page.getByRole('dialog', { name: '备份确认' })
  await expect(backupDialog).toBeVisible()
  await backupDialog.getByRole('button', { name: '确定' }).click()
  const successToast = page.locator('.el-message').filter({ hasText: '备份完成' })
  await expect(successToast).toBeVisible({ timeout: 60_000 })
  const toastText = (await successToast.first().textContent()) ?? ''
  const fileName = toastText.replace(/^.*备份完成：/, '').trim()
  expect(fileName).toMatch(/^eladmin_plus_\d{14}\.sql\.gz$/)

  // 切备份记录区块：新行在册（文件名唯一定位）
  await page.getByRole('tab', { name: '备份记录' }).click()
  const recordTable = page.locator('[data-test="database-record-table"]')
  await expect(
    recordTable.or(page.locator('[data-test="admin-table-empty"]')).first(),
  ).toBeVisible()
  const recordRow = recordTable.locator('tbody tr').filter({ hasText: fileName })
  await expect(recordRow).toBeVisible()

  // 文件名模糊筛选：命中该行；无命中走空态占位
  const nameFilter = page.locator('[data-test="database-record-filter"]')
  await nameFilter.fill(fileName)
  await nameFilter.press('Enter')
  await expect(recordTable.locator('tbody tr').filter({ hasText: fileName })).toBeVisible()
  await nameFilter.fill('e2e-no-such-backup')
  await nameFilter.press('Enter')
  await expect(page.locator('[data-test="admin-table-empty"]')).toBeVisible()

  // 行级导出：blob 落盘原名 sql 文件
  await nameFilter.fill(fileName)
  await nameFilter.press('Enter')
  await expect(recordRow).toBeVisible()
  const downloadPromise = page.waitForEvent('download')
  await recordRow.locator('[data-test="database-record-export"]').click()
  const download = await downloadPromise
  expect(download.suggestedFilename()).toBe(fileName)
  await expect(page.locator('.el-message').filter({ hasText: '已下载' })).toBeVisible()

  // 行级删除：确认后行消失（存储对象与记录行同删，后端语义）
  await recordRow.locator('[data-test="database-record-delete"]').click()
  const deleteDialog = page.getByRole('dialog', { name: '删除确认' })
  await expect(deleteDialog).toBeVisible()
  await deleteDialog.getByRole('button', { name: '确定' }).click()
  await expect(page.locator('.el-message').filter({ hasText: '删除成功' })).toBeVisible()
  await expect(recordRow).toBeHidden()

  // 清理：连接配置行删除（自清，不留测试数据）
  await page.getByRole('tab', { name: '连接配置' }).click()
  await configRow.locator('[data-test="database-config-delete"]').click()
  const confirmDialog = page.getByRole('dialog', { name: '删除确认' })
  await expect(confirmDialog).toBeVisible()
  await confirmDialog.getByRole('button', { name: '确定' }).click()
  await expect(configRow).toBeHidden()
})
