import { VueQueryPlugin } from '@tanstack/vue-query'
import { cleanup, fireEvent, render, waitFor } from '@testing-library/vue'
import { createPinia, setActivePinia } from 'pinia'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'

import { buildI18n } from '@/i18n'
import { useMenusStore } from '@/stores/menus'
import Dicts from '../index.vue'
import { buildLabelJson, parseLabelJson } from '../label-json'
import type { Dict, DictDetail } from '@/api/generated'

const dictData: Dict[] = [
  { id: 1, name: 'sys_status', description: '状态开关' },
  { id: 2, name: 'sys_demo', description: '' },
]

const detailData: DictDetail[] = [
  { id: 11, dictId: 1, label: '{"zh-CN":"启用","en":"Enabled"}', value: '1', sort: 0 },
  { id: 12, dictId: 1, label: '{"zh-CN":"停用"}', value: '0', sort: 1 },
]

const {
  dictSaveAsync,
  dictRemoveAsync,
  detailSaveAsync,
  detailRemoveAsync,
  confirmMock,
  registeredLocalesMock,
} = vi.hoisted(() => ({
  dictSaveAsync: vi.fn().mockResolvedValue(undefined),
  dictRemoveAsync: vi.fn().mockResolvedValue(undefined),
  detailSaveAsync: vi.fn().mockResolvedValue(undefined),
  detailRemoveAsync: vi.fn().mockResolvedValue(undefined),
  confirmMock: vi.fn().mockResolvedValue(true),
  registeredLocalesMock: vi.fn(),
}))

vi.mock('@/api/dicts', async (importOriginal) => {
  const actual = await importOriginal<typeof import('@/api/dicts')>()
  const { ref } = await import('vue')
  return {
    ...actual,
    useDictList: () => ({ data: ref(dictData), isLoading: ref(false) }),
    useDictSave: () => ({ mutateAsync: dictSaveAsync }),
    useDictRemove: () => ({ mutateAsync: dictRemoveAsync }),
    useDictDetails: () => ({ data: ref(detailData), isLoading: ref(false) }),
    useDictDetailSave: () => ({ mutateAsync: detailSaveAsync }),
    useDictDetailRemove: () => ({ mutateAsync: detailRemoveAsync }),
  }
})

vi.mock('@/i18n', async (importOriginal) => {
  const actual = await importOriginal<typeof import('@/i18n')>()
  return {
    ...actual,
    // 语言注册表可置换：动态语言行按注册表遍历生成（三语言扩展断言用）
    registeredLocales: registeredLocalesMock,
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
  registeredLocalesMock.mockReturnValue(['zh-CN', 'en'])
})

afterEach(() => {
  cleanup()
  localStorage.clear()
  vi.restoreAllMocks()
  vi.clearAllMocks()
})

async function renderPage() {
  const pinia = createPinia()
  setActivePinia(pinia)
  useMenusStore(pinia).roles = ['ROLE_ROOT']
  render(Dicts, { global: { plugins: [pinia, buildI18n('zh-CN'), VueQueryPlugin] } })
}

function detailInput(value: string): HTMLInputElement {
  return document.querySelector(`[data-test="${value}"]`) as HTMLInputElement
}

describe('字典管理页', () => {
  it('字典列表渲染，行操作在册', async () => {
    await renderPage()
    expect(document.body.textContent).toContain('sys_status')
    expect(byTest('dicts-edit').length).toBe(2)
    expect(byTest('dicts-delete').length).toBe(2)
    expect(byTest('dicts-details').length).toBe(2)
  })

  it('新增字典：保存调接口并携带名称', async () => {
    await renderPage()
    await fireEvent.click(byTest('dicts-create')[0]!)
    await fireEvent.update(detailInput('dicts-name'), 'sys_new')
    await fireEvent.click(byTest('dicts-submit')[0]!)
    await waitFor(() =>
      expect(dictSaveAsync).toHaveBeenCalledWith(expect.objectContaining({ name: 'sys_new' })),
    )
  })

  it('删除字典：级联确认文案在册，确认后调接口', async () => {
    await renderPage()
    await fireEvent.click(byTest('dicts-delete')[0]!)
    await waitFor(() => expect(dictRemoveAsync).toHaveBeenCalledWith(1))
    // 级联提示：确认文案明示明细一并删除
    expect(String(confirmMock.mock.calls[0]?.[0])).toContain('一并删除')
  })

  it('409 冲突消息直显', async () => {
    dictSaveAsync.mockRejectedValueOnce(new Error('字典名已存在'))
    await renderPage()
    await fireEvent.click(byTest('dicts-create')[0]!)
    await fireEvent.update(detailInput('dicts-name'), 'sys_status')
    await fireEvent.click(byTest('dicts-submit')[0]!)
    await waitFor(() => {
      const error = document.querySelector('.el-message--error')
      expect(error?.textContent).toContain('字典名已存在')
    })
  })

  it('明细二级管理：弹窗列表渲染且 label 按当前语言取值', async () => {
    await renderPage()
    await fireEvent.click(byTest('dicts-details')[0]!)
    const dialog = document.querySelector('[data-test="dicts-detail-dialog"]') as HTMLElement
    await waitFor(() => expect(dialog).toBeTruthy())
    expect(document.body.textContent).toContain('启用')
    expect(byTest('dicts-detail-edit').length).toBe(2)
    expect(byTest('dicts-detail-delete').length).toBe(2)
  })

  it('新增明细：label 动态语言行按注册表渲染，空行不写入 JSON', async () => {
    await renderPage()
    await fireEvent.click(byTest('dicts-details')[0]!)
    await fireEvent.click(byTest('dicts-detail-create')[0]!)
    const form = document.querySelector('[data-test="dicts-detail-form"]') as HTMLElement
    await waitFor(() => expect(form).toBeTruthy())
    // 当前注册表双语言两行
    expect(document.querySelector('[data-test="label-row-zh-CN"]')).toBeTruthy()
    expect(document.querySelector('[data-test="label-row-en"]')).toBeTruthy()
    // zh 行填值、en 留空 → 组装 JSON 只含 zh-CN（空行不写入）
    await fireEvent.update(
      document.querySelector('[data-test="label-row-zh-CN"]') as HTMLInputElement,
      '启用',
    )
    await fireEvent.update(detailInput('dicts-value'), '1')
    await fireEvent.click(byTest('dicts-detail-submit')[0]!)
    await waitFor(() => {
      const payload = detailSaveAsync.mock.calls[0]?.[0] as { label: string; value: string }
      expect(payload.value).toBe('1')
      expect(JSON.parse(payload.label)).toEqual({ 'zh-CN': '启用' })
    })
  })

  it('注册表加语言自动加行：三语言注册表渲染三行', async () => {
    registeredLocalesMock.mockReturnValue(['zh-CN', 'en', 'fr'])
    await renderPage()
    await fireEvent.click(byTest('dicts-details')[0]!)
    await fireEvent.click(byTest('dicts-detail-create')[0]!)
    await waitFor(() => {
      expect(document.querySelector('[data-test="label-row-zh-CN"]')).toBeTruthy()
      expect(document.querySelector('[data-test="label-row-en"]')).toBeTruthy()
      expect(document.querySelector('[data-test="label-row-fr"]')).toBeTruthy()
    })
  })

  it('编辑明细：label JSON 回填语言行', async () => {
    await renderPage()
    await fireEvent.click(byTest('dicts-details')[0]!)
    await waitFor(() => expect(byTest('dicts-detail-edit').length).toBe(2))
    await fireEvent.click(byTest('dicts-detail-edit')[0]!)
    const zhRow = document.querySelector('[data-test="label-row-zh-CN"]') as HTMLInputElement
    const enRow = document.querySelector('[data-test="label-row-en"]') as HTMLInputElement
    await waitFor(() => {
      expect(zhRow?.value).toBe('启用')
      expect(enRow?.value).toBe('Enabled')
    })
  })
})

describe('label JSON 组装纯函数', () => {
  it('buildLabelJson 剔除空行并保序', () => {
    expect(buildLabelJson({ 'zh-CN': ' 启用 ', en: '', fr: '  ' })).toBe('{"zh-CN":"启用"}')
    expect(buildLabelJson({})).toBe('{}')
  })

  it('parseLabelJson 非法与空值安全回退', () => {
    expect(parseLabelJson('{"zh-CN":"男"}')).toEqual({ 'zh-CN': '男' })
    expect(parseLabelJson('not-json')).toEqual({})
    expect(parseLabelJson(null)).toEqual({})
    expect(parseLabelJson('["数组"]')).toEqual({})
  })
})
