import { cleanup, fireEvent, render, waitFor } from '@testing-library/vue'
import { createPinia, setActivePinia } from 'pinia'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'

import { NV_ICONS } from '@/components/nv-icons'
import { buildI18n } from '@/i18n'
import IconPicker from '../icon-picker.vue'

beforeEach(() => {
  vi.spyOn(window.navigator, 'language', 'get').mockReturnValue('zh-CN')
  setActivePinia(createPinia())
})

afterEach(() => {
  cleanup()
  localStorage.clear()
  vi.restoreAllMocks()
})

/** 打开选择弹层（append-to-body 落 document.body，取用走 document 查询） */
async function openPicker(modelValue: string) {
  const rendered = render(IconPicker, {
    props: { modelValue },
    global: { plugins: [buildI18n('zh-CN')] },
  })
  fireEvent.click(document.querySelector('[data-test="menus-icon-pick"]') as HTMLElement)
  await waitFor(() => {
    expect(document.querySelector('[data-test="menus-icon-dialog"]')).toBeTruthy()
  })
  return rendered
}

describe('图标选择器双 tab', () => {
  it('nv tab 为缺省落点：无值与历史裸名值打开均落 nv，格渲染注册集 25 名', async () => {
    const empty = await openPicker('')
    // 弹层 append-to-body，tab 激活态从 document 取
    expect(document.querySelector('#tab-nv')?.classList.contains('is-active')).toBe(true)
    expect(
      document.querySelectorAll('[data-test="menus-icon-grid-nv"] [data-test^="icon-option-nv-"]')
        .length,
    ).toBe(Object.keys(NV_ICONS).length)
    empty.unmount()

    await openPicker('develop')
    expect(document.querySelector('#tab-nv')?.classList.contains('is-active')).toBe(true)
  })

  it('ep: 值打开落 EP tab，选中格按名段高亮', async () => {
    await openPicker('ep:timer')
    expect(document.querySelector('#tab-ep')?.classList.contains('is-active')).toBe(true)
    const activeCell = document.querySelector('[data-test="icon-option-ep-timer"]')
    expect(activeCell?.classList.contains('is-active')).toBe(true)
  })

  it('nv: 值选中格高亮，重选回填 nv: 前缀值', async () => {
    const { emitted } = await openPicker('nv:home-outline')
    const activeCell = document.querySelector('[data-test="icon-option-nv-home-outline"]')
    expect(activeCell?.classList.contains('is-active')).toBe(true)
    await waitFor(() => {
      // pane 同时在册渲染（隐藏 pane 不卸载），EP 格可定位说明双清单同在
      expect(document.querySelector('[data-test="icon-option-ep-timer"]')).toBeTruthy()
    })
    fireEvent.click(
      document.querySelector('[data-test="icon-option-nv-mail-outline"]') as HTMLElement,
    )
    const updates = emitted()['update:modelValue'] as unknown[][]
    expect(updates.at(-1)?.[0]).toBe('nv:mail-outline')
  })

  it('名称过滤在册：nv tab 输入关键词只剩匹配格', async () => {
    await openPicker('')
    const filterInput = document.querySelector(
      '[data-test="menus-icon-filter"]',
    ) as HTMLInputElement
    await fireEvent.update(filterInput, 'wallet')
    await waitFor(() => {
      const cells = document.querySelectorAll(
        '[data-test="menus-icon-grid-nv"] [data-test^="icon-option-nv-"]',
      )
      expect(cells.length).toBe(1)
      expect(document.querySelector('[data-test="icon-option-nv-wallet-outline"]')).toBeTruthy()
    })
  })
})
