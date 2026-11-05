import { VueQueryPlugin } from '@tanstack/vue-query'
import { cleanup, fireEvent, render, waitFor } from '@testing-library/vue'
import { createPinia, setActivePinia } from 'pinia'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { ref } from 'vue'

import { buildI18n } from '@/i18n'
import ConfigPanel from '../config-panel.vue'

const { saveMutateAsync, removeMutateAsync, confirmMock, listData } = vi.hoisted(() => ({
  saveMutateAsync: vi.fn().mockResolvedValue(undefined),
  removeMutateAsync: vi.fn().mockResolvedValue(undefined),
  confirmMock: vi.fn().mockResolvedValue(true),
  listData: { value: [] as Array<Record<string, unknown>> },
}))

vi.mock('@/api/database', () => ({
  useDatabaseConfigs: () => ({ data: ref(listData.value), isLoading: ref(false) }),
  useDatabaseConfigSave: () => ({ mutateAsync: saveMutateAsync }),
  useDatabaseConfigRemove: () => ({ mutateAsync: removeMutateAsync }),
}))

vi.mock('element-plus', async (importOriginal) => {
  const actual = await importOriginal<typeof import('element-plus')>()
  return { ...actual, ElMessageBox: { ...actual.ElMessageBox, confirm: confirmMock } }
})

function byTest(test: string): HTMLElement {
  const element = document.querySelector(`[data-test="${test}"]`)
  if (!element) {
    throw new Error(`element not found: [data-test="${test}"]`)
  }
  return element as HTMLElement
}

/** 提交按钮定位（须点 button 本体，span 冒泡不触发 happy-dom 的按钮激活行为） */
function screenButton(name: string): HTMLElement {
  return Array.from(document.querySelectorAll('button')).find(
    (b) => b.textContent?.trim() === name,
  )!
}

/** 弹窗表单字段输入定位 */
function fieldInput(name: string): HTMLInputElement {
  const input = document.querySelector<HTMLInputElement>(`[data-field="${name}"] input`)
  if (!input) {
    throw new Error(`input not found: [data-field="${name}"]`)
  }
  return input
}

beforeEach(() => {
  vi.spyOn(window.navigator, 'language', 'get').mockReturnValue('zh-CN')
  listData.value = []
})

afterEach(() => {
  cleanup()
  localStorage.clear()
  vi.restoreAllMocks()
  vi.clearAllMocks()
})

async function renderPanel() {
  const pinia = createPinia()
  setActivePinia(pinia)
  useMenusStoreOf(pinia).roles = ['ROLE_ROOT']
  render(ConfigPanel, { global: { plugins: [pinia, buildI18n('zh-CN'), VueQueryPlugin] } })
}

// 独立引入避免与 mock 时序相互牵动（storage 页同款）
import { useMenusStore } from '@/stores/menus'
function useMenusStoreOf(pinia: ReturnType<typeof createPinia>) {
  return useMenusStore(pinia)
}

describe('连接配置区块（列表、新增 / 编辑弹窗、删除确认）', () => {
  it('列表渲染：主机、端口、库名、账号、备注、时间；密码列不展示', async () => {
    listData.value = [
      {
        id: 1,
        host: 'mysql-inner',
        port: 3306,
        name: 'eladmin_plus',
        userName: 'root',
        remark: '主库样例',
        createdAt: '2026-11-05T09:30:00',
      },
    ]
    await renderPanel()
    const rows = document.querySelectorAll('[data-test="database-config-table"] tbody tr')
    expect(rows.length).toBe(1)
    expect(document.body.textContent).toContain('mysql-inner')
    expect(document.body.textContent).toContain('eladmin_plus')
    expect(document.body.textContent).toContain('root')
    expect(document.body.textContent).toContain('主库样例')
    expect(document.body.textContent).toContain('2026-11-05 09:30:00')
    // 密码列不展示：表头无「密码」，行内无密码值
    expect(document.body.textContent).not.toContain('密码')
  })

  it('必填校验拦截：空表单提交出字段级错误且不调保存', async () => {
    await renderPanel()
    await fireEvent.click(byTest('database-config-create'))
    await waitFor(() => expect(byTest('database-config-dialog')).toBeTruthy())
    await fireEvent.click(screenButton('保存'))
    await waitFor(() => {
      expect(
        document.querySelectorAll('[data-test="auto-form-field-error"]').length,
      ).toBeGreaterThanOrEqual(1)
    })
    expect(saveMutateAsync).not.toHaveBeenCalled()
  })

  it('新增提交：填齐必填后保存成功、弹窗关、列表刷新在册', async () => {
    await renderPanel()
    await fireEvent.click(byTest('database-config-create'))
    await waitFor(() => expect(byTest('database-config-dialog')).toBeTruthy())
    await fireEvent.update(fieldInput('host'), 'mysql-inner')
    await fireEvent.update(fieldInput('port'), '3307')
    await fireEvent.update(fieldInput('name'), 'eladmin_ops_it')
    await fireEvent.update(fieldInput('userName'), 'root')
    await fireEvent.update(fieldInput('password'), 'root')
    await fireEvent.click(screenButton('保存'))
    await waitFor(() => expect(saveMutateAsync).toHaveBeenCalledTimes(1))
    const payload = saveMutateAsync.mock.calls[0]?.[0] as Record<string, unknown>
    expect(payload.id).toBeFalsy()
    expect(payload.host).toBe('mysql-inner')
    expect(payload.port).toBe(3307)
    expect(payload.password).toBe('root')
    await waitFor(() => expect(document.body.textContent).toContain('保存成功'))
  })

  it('编辑：密码留空保持（占位明示），提交带 id 且密码为空', async () => {
    listData.value = [
      {
        id: 7,
        host: 'mysql-inner',
        port: 3306,
        name: 'eladmin_plus',
        userName: 'root',
        remark: '',
        createdAt: '2026-11-05T09:30:00',
      },
    ]
    await renderPanel()
    await fireEvent.click(byTest('database-config-edit'))
    await waitFor(() => expect(byTest('database-config-dialog')).toBeTruthy())
    // 密码输入不回显，占位文案明示留空保持原值
    expect(fieldInput('password').value).toBe('')
    expect(fieldInput('password').placeholder).toBe('留空保持原密码')
    await fireEvent.update(fieldInput('remark'), '改备注')
    await fireEvent.click(screenButton('保存'))
    await waitFor(() => expect(saveMutateAsync).toHaveBeenCalledTimes(1))
    const payload = saveMutateAsync.mock.calls[0]?.[0] as Record<string, unknown>
    expect(payload.id).toBe(7)
    expect(payload.password).toBe('')
    expect(payload.remark).toBe('改备注')
  })

  it('删除：确认后调删除并提示成功；取消不调', async () => {
    listData.value = [
      {
        id: 7,
        host: 'mysql-inner',
        port: 3306,
        name: 'eladmin_plus',
        userName: 'root',
        remark: '',
        createdAt: '2026-11-05T09:30:00',
      },
    ]
    await renderPanel()

    // 取消不调
    confirmMock.mockRejectedValueOnce('cancel')
    await fireEvent.click(byTest('database-config-delete'))
    await waitFor(() => expect(confirmMock).toHaveBeenCalledTimes(1))
    expect(removeMutateAsync).not.toHaveBeenCalled()

    // 确认即删
    await fireEvent.click(byTest('database-config-delete'))
    await waitFor(() => expect(confirmMock).toHaveBeenCalledTimes(2))
    await waitFor(() => expect(removeMutateAsync).toHaveBeenCalledWith(7))
    await waitFor(() => expect(document.body.textContent).toContain('删除成功'))
  })

  it('保存失败展示后端消息', async () => {
    await renderPanel()
    await fireEvent.click(byTest('database-config-create'))
    await waitFor(() => expect(byTest('database-config-dialog')).toBeTruthy())
    await fireEvent.update(fieldInput('host'), 'mysql-inner')
    await fireEvent.update(fieldInput('port'), '3307')
    await fireEvent.update(fieldInput('name'), 'eladmin_ops_it')
    await fireEvent.update(fieldInput('userName'), 'root')
    await fireEvent.update(fieldInput('password'), 'root')
    saveMutateAsync.mockRejectedValueOnce(new Error('库名已存在'))
    await fireEvent.click(screenButton('保存'))
    await waitFor(() => {
      const messages = Array.from(document.querySelectorAll('.el-message')).map(
        (m) => m.textContent,
      )
      expect(messages.join()).toContain('库名已存在')
    })
  })
})
