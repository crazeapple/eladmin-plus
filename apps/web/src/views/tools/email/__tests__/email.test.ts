import { VueQueryPlugin } from '@tanstack/vue-query'
import { cleanup, fireEvent, render, waitFor } from '@testing-library/vue'
import { createPinia, setActivePinia } from 'pinia'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'

import { buildI18n } from '@/i18n'
import { useMenusStore } from '@/stores/menus'
import EmailPage from '../index.vue'

const { sendMutate } = vi.hoisted(() => ({
  sendMutate: vi.fn().mockResolvedValue(undefined),
}))

vi.mock('@/api/email', () => ({
  useEmailSend: () => ({ mutateAsync: sendMutate }),
}))

function byTest(test: string): HTMLElement {
  const element = document.querySelector(`[data-test="${test}"]`)
  if (!element) {
    throw new Error(`element not found: [data-test="${test}"]`)
  }
  return element as HTMLElement
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
  render(EmailPage, { global: { plugins: [pinia, buildI18n('zh-CN'), VueQueryPlugin] } })
}

describe('邮件工具页（发送表单）', () => {
  it('表单渲染：收件人、主题、正文三字段与发送按钮', async () => {
    await renderPage()
    expect(byTest('email-to')).toBeTruthy()
    expect(byTest('email-subject')).toBeTruthy()
    expect(byTest('email-content')).toBeTruthy()
    expect(byTest('email-send')).toBeTruthy()
  })

  it('发送成功：HTML 源码透传（html: true）并提示已发送、正文主题复位', async () => {
    await renderPage()
    await fireEvent.update(byTest('email-to'), 'to@example.com')
    await fireEvent.update(byTest('email-subject'), '月度报表')
    await fireEvent.update(byTest('email-content'), '<p>正文 <b>HTML</b></p>')
    await fireEvent.click(byTest('email-send'))
    await waitFor(() =>
      expect(sendMutate).toHaveBeenCalledWith({
        to: 'to@example.com',
        subject: '月度报表',
        content: '<p>正文 <b>HTML</b></p>',
        html: true,
      }),
    )
    await waitFor(() => expect(document.body.textContent).toContain('邮件已发送'))
    expect((byTest('email-subject') as HTMLInputElement).value).toBe('')
    expect((byTest('email-content') as HTMLTextAreaElement).value).toBe('')
  })

  it('发送失败：后端消息页内展示（SMTP 未配置 / 收件人非法 422 通路）', async () => {
    sendMutate.mockRejectedValueOnce(new Error('邮件服务未启用'))
    await renderPage()
    await fireEvent.update(byTest('email-to'), 'to@example.com')
    await fireEvent.click(byTest('email-send'))
    await waitFor(() => expect(sendMutate).toHaveBeenCalledTimes(1))
    await waitFor(() => expect(document.body.textContent).toContain('邮件服务未启用'))

    sendMutate.mockRejectedValueOnce(new Error('收件人邮箱格式不合法'))
    await fireEvent.click(byTest('email-send'))
    await waitFor(() => expect(sendMutate).toHaveBeenCalledTimes(2))
    await waitFor(() => expect(document.body.textContent).toContain('收件人邮箱格式不合法'))
  })

  it('hasAuth 藏显：无 email:send 无发送按钮', async () => {
    await renderPage([])
    expect(document.querySelectorAll('[data-test="email-send"]').length).toBe(0)

    await renderPage(['email:send'])
    expect(byTest('email-send')).toBeTruthy()
  })
})
