import { cleanup, render } from '@testing-library/vue'
import { createPinia, setActivePinia } from 'pinia'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'

import { buildI18n } from '@/i18n'
import AliPayPage from '../index.vue'

beforeEach(() => {
  vi.spyOn(window.navigator, 'language', 'get').mockReturnValue('zh-CN')
})

afterEach(() => {
  cleanup()
})

describe('支付宝壳页（未启用提示卡）', () => {
  it('提示卡渲染：标题、现状说明与预留点说明，零表单零按钮', () => {
    const pinia = createPinia()
    setActivePinia(pinia)
    render(AliPayPage, { global: { plugins: [pinia, buildI18n('zh-CN')] } })

    expect(document.querySelector('[data-test="ali-pay-page"]')).toBeTruthy()
    expect(document.querySelector('[data-test="ali-pay-card"]')).toBeTruthy()
    expect(document.body.textContent).toContain('支付宝工具')
    expect(document.body.textContent).toContain('尚未接入')
    expect(document.body.textContent).toContain('AlipayGateway')
    // 零假交互：卡内无输入、无按钮
    expect(document.querySelector('[data-test="ali-pay-card"] input')).toBeNull()
    expect(document.querySelector('[data-test="ali-pay-card"] button')).toBeNull()
  })
})
