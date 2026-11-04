import { VueQueryPlugin } from '@tanstack/vue-query'
import { cleanup, fireEvent, render, waitFor } from '@testing-library/vue'
import { createPinia, setActivePinia } from 'pinia'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'

import { buildI18n } from '@/i18n'
import { useMenusStore } from '@/stores/menus'
import OnlineUsers from '../index.vue'

const CHROME_UA =
  'Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36'

function onlineData() {
  return [
    {
      userId: 1,
      nickName: '系统管理员',
      username: 'admin',
      ip: '10.0.0.2',
      loginAt: '2026-11-04T09:30:00',
      browser: CHROME_UA,
    },
    {
      userId: 2,
      nickName: '王五',
      username: 'wangwu',
      ip: '192.168.1.8',
      loginAt: '2026-11-04T10:00:00',
      browser: 'curl/8.4.0',
    },
  ]
}

const { kickMutate, confirmMock } = vi.hoisted(() => ({
  kickMutate: vi.fn().mockResolvedValue(undefined),
  confirmMock: vi.fn().mockResolvedValue(true),
}))

vi.mock('@/api/monitor', async () => {
  const { ref } = await import('vue')
  return {
    useOnlineUsers: () => ({ data: ref(onlineData()), isLoading: ref(false) }),
    useOnlineKick: () => ({ mutateAsync: kickMutate, isPending: ref(false) }),
  }
})

vi.mock('element-plus', async (importOriginal) => {
  const actual = await importOriginal<typeof import('element-plus')>()
  return { ...actual, ElMessageBox: { ...actual.ElMessageBox, confirm: confirmMock } }
})

function byTest(test: string): HTMLElement[] {
  return Array.from(document.querySelectorAll(`[data-test="${test}"]`)) as HTMLElement[]
}

beforeEach(() => {
  vi.spyOn(window.navigator, 'language', 'get').mockReturnValue('zh-CN')
})

afterEach(() => {
  cleanup()
  localStorage.clear()
  vi.restoreAllMocks()
  vi.clearAllMocks()
})

async function renderPage(permissions: string[] | 'root' = 'root') {
  cleanup()
  const pinia = createPinia()
  setActivePinia(pinia)
  const menus = useMenusStore(pinia)
  menus.user = { id: 1, username: 'admin', nickName: '系统管理员' }
  if (permissions === 'root') {
    menus.roles = ['ROLE_ROOT']
  } else {
    menus.permissions = permissions
  }
  render(OnlineUsers, { global: { plugins: [pinia, buildI18n('zh-CN'), VueQueryPlugin] } })
}

describe('在线用户页（五字段、UA 三列与强退确认）', () => {
  it('列表渲染：五字段在册且多端登录多行并列', async () => {
    await renderPage()
    const rows = document.querySelectorAll('[data-test="online-users-table"] tbody tr')
    expect(rows.length).toBe(2)
    expect(document.body.textContent).toContain('系统管理员')
    expect(document.body.textContent).toContain('wangwu')
    expect(document.body.textContent).toContain('10.0.0.2')
    expect(document.body.textContent).toContain('2026-11-04 09:30:00')
    expect(document.body.textContent).toContain('当前在线 2 人')
  })

  it('UA 解析三列：标准 UA 出浏览器版本操作系统，非标准 UA 原样截断兜底', async () => {
    await renderPage()
    const rows = document.querySelectorAll('[data-test="online-users-table"] tbody tr')
    // 首行 Chrome：浏览器、版本、操作系统
    expect(rows[0]!.querySelector('[data-test="ua-browser"]')?.textContent).toBe('Chrome')
    expect(rows[0]!.querySelector('[data-test="ua-version"]')?.textContent).toBe('120.0.0.0')
    expect(rows[0]!.querySelector('[data-test="ua-os"]')?.textContent).toBe('macOS')
    // 次行非标准 UA：浏览器列原样截断，版本与操作系统占位
    expect(rows[1]!.querySelector('[data-test="ua-browser"]')?.textContent).toBe('curl/8.4.0')
    expect(rows[1]!.querySelector('[data-test="ua-version"]')?.textContent).toBe('—')
    expect(rows[1]!.querySelector('[data-test="ua-os"]')?.textContent).toBe('—')
  })

  it('强退他人：确认后调用端点并反馈成功', async () => {
    await renderPage()
    await fireEvent.click(byTest('online-users-kick')[1]!)
    await waitFor(() => expect(confirmMock).toHaveBeenCalledTimes(1))
    // 非自己：文案不含登出提示
    expect(confirmMock.mock.calls[0]?.[0]).not.toContain('将登出当前会话')
    await waitFor(() => expect(kickMutate).toHaveBeenCalledWith(2))
    await waitFor(() => expect(document.body.textContent).toContain('已强退'))
  })

  it('强退自己：确认文案追加登出提示，取消不调接口', async () => {
    await renderPage()

    // 取消不调
    confirmMock.mockRejectedValueOnce('cancel')
    await fireEvent.click(byTest('online-users-kick')[0]!)
    await waitFor(() => expect(confirmMock).toHaveBeenCalledTimes(1))
    expect(confirmMock.mock.calls[0]?.[0]).toContain('将登出当前会话')
    expect(kickMutate).not.toHaveBeenCalled()

    // 确认即强退
    await fireEvent.click(byTest('online-users-kick')[0]!)
    await waitFor(() => expect(confirmMock).toHaveBeenCalledTimes(2))
    await waitFor(() => expect(kickMutate).toHaveBeenCalledWith(1))
  })

  it('hasAuth 藏显：无 monitor:online:del 无强退按钮', async () => {
    await renderPage(['monitor:online:list'])
    expect(byTest('online-users-kick').length).toBe(0)

    await renderPage(['monitor:online:del'])
    expect(byTest('online-users-kick').length).toBe(2)
  })
})
