import { expect, test } from '@playwright/test'

import { gotoWithSession } from './helpers'

// 定时任务页主链（对真实后端）：建任务即注册、暂停恢复翻转、立即执行日志落表、删除闭环。

test('任务页主链：建任务即注册、暂停恢复、立即执行日志、删除', async ({ page }) => {
  await gotoWithSession(page, '/system/tasks')

  // 新增任务：目标取白名单下拉（演示 Bean）、cron 预设选即填后手改、参数可选
  await page.locator('[data-test="tasks-create"]').click()
  const dialog = page.locator('[data-test="tasks-dialog"]')
  await expect(dialog).toBeVisible()
  const taskName = `E2E任务${Date.now().toString().slice(-8)}`
  await dialog.locator('[data-test="tasks-name"]').fill(taskName)
  await dialog.locator('[data-test="tasks-target"]').click()
  await page.locator('.el-select-dropdown__item', { hasText: 'sampleJob.run' }).first().click()
  await dialog.locator('[data-test="tasks-cron-presets"]').click()
  await page.locator('.el-select-dropdown__item', { hasText: '每分钟' }).first().click()
  await expect(dialog.locator('[data-test="tasks-cron"]')).toHaveValue('0 * * * * ?')
  await dialog.locator('[data-test="tasks-param"]').fill('e2e-run')
  await dialog.getByRole('button', { name: '保存' }).click()
  await expect(dialog).toBeHidden()

  // 创建即注册：行出现且调度态运行中
  const rowOf = (name: string) =>
    page.locator('[data-test="tasks-table"] tbody tr', { hasText: name })
  await expect(rowOf(taskName)).toBeVisible()
  await expect(rowOf(taskName)).toContainText('sampleJob.run')
  await expect(rowOf(taskName)).toContainText('运行中')

  // 暂停行内直驱：状态翻转为暂停，按钮变恢复
  await rowOf(taskName).locator('[data-test="tasks-pause"]').click()
  await expect(rowOf(taskName)).toContainText('已暂停', { timeout: 10000 })
  await rowOf(taskName).locator('[data-test="tasks-resume"]').click()
  await expect(rowOf(taskName)).toContainText('运行中', { timeout: 10000 })

  // 立即执行：确认弹窗明示异步一次，触发后日志异步落表
  await rowOf(taskName).locator('[data-test="tasks-exec"]').click()
  await expect(page.getByRole('dialog').filter({ hasText: '异步执行一次' })).toBeVisible()
  await page.getByRole('button', { name: '确定' }).click()
  await expect(page.locator('.el-message--success', { hasText: '已触发执行' })).toBeVisible()

  // 执行日志弹窗：分页在册；异步落表后出现记录（手动刷新兜底）
  await rowOf(taskName).locator('[data-test="tasks-logs"]').click()
  const logDialog = page.locator('[data-test="tasks-log-dialog"]')
  await expect(logDialog).toBeVisible()
  const logRows = logDialog.locator('[data-test="tasks-log-table"] tbody tr')
  await expect
    .poll(
      async () => {
        if ((await logRows.count()) === 0) {
          await page.locator('[data-test="tasks-log-refresh"]').click()
        }
        return logRows.count()
      },
      { timeout: 30000, intervals: [1000, 2000, 5000] },
    )
    .toBeGreaterThan(0)
  await expect(logDialog).toContainText('成功')
  await logDialog.locator('[data-test="tasks-log-refresh"]').click()

  // 非法 cron：后端 422 消息直显
  await page.keyboard.press('Escape')
  await expect(logDialog).toBeHidden()
  await rowOf(taskName).locator('[data-test="tasks-edit"]').click()
  await expect(dialog).toBeVisible()
  await dialog.locator('[data-test="tasks-cron"]').fill('not-a-cron')
  await dialog.getByRole('button', { name: '保存' }).click()
  await expect(page.locator('.el-message--error')).toContainText('cron 表达式不合法')
  await dialog.getByRole('button', { name: '取消' }).click()
  await expect(dialog).toBeHidden()

  // 删除确认后行消失
  await rowOf(taskName).locator('[data-test="tasks-delete"]').click()
  await page.getByRole('button', { name: '确定' }).click()
  await expect(rowOf(taskName)).toBeHidden()
})
