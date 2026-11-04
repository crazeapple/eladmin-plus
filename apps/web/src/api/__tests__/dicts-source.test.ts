import { setDictSource, useDict } from '@eladmin-plus/admin-ui'
import { cleanup } from '@testing-library/vue'
import { createPinia, setActivePinia } from 'pinia'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'

import { setupDictSource } from '../dicts'
import { usePreferencesStore } from '@/stores/preferences'

const { requestMock } = vi.hoisted(() => ({ requestMock: vi.fn() }))

vi.mock('../request', () => ({ request: requestMock }))

beforeEach(() => {
  vi.spyOn(window.navigator, 'language', 'get').mockReturnValue('zh-CN')
  setActivePinia(createPinia())
  // 未接源返回空数组（mock 源已移除），接线后走真实端点
  setupDictSource()
})

afterEach(() => {
  cleanup()
  localStorage.clear()
  vi.restoreAllMocks()
  vi.clearAllMocks()
})

describe('useDict 真实数据源接线', () => {
  it('按名取端点出选项，label 按当前 locale 取值', async () => {
    requestMock.mockResolvedValue([
      { label: '{"zh-CN":"男","en":"Male"}', value: '1' },
      { label: '{"zh-CN":"女","en":"Female"}', value: '2' },
    ])
    const { options, loading } = useDict('sys_user_sex')
    await vi.waitFor(() => expect(loading.value).toBe(false))
    expect(requestMock).toHaveBeenCalledWith(
      expect.objectContaining({ url: '/dicts/sys_user_sex' }),
    )
    expect(options.value).toEqual([
      { label: '男', value: '1' },
      { label: '女', value: '2' },
    ])
  })

  it('当前语言缺失回退默认语言 zh-CN，再回退任一非空值', async () => {
    usePreferencesStore().locale = 'en'
    requestMock.mockResolvedValue([
      { label: '{"zh-CN":"停用"}', value: '0' },
      { label: '{"fr":"Actif"}', value: '1' },
    ])
    const { options } = useDict('sys_enable')
    await vi.waitFor(() => expect(options.value).toHaveLength(2))
    // en 缺失回退 zh-CN
    expect(options.value[0]?.label).toBe('停用')
    // zh-CN 亦缺失取任一非空值
    expect(options.value[1]?.label).toBe('Actif')
  })

  it('当前 locale 为 en 时直取 en 值', async () => {
    usePreferencesStore().locale = 'en'
    requestMock.mockResolvedValue([{ label: '{"zh-CN":"男","en":"Male"}', value: '1' }])
    const { options } = useDict('sys_locale_en')
    await vi.waitFor(() => expect(options.value).toHaveLength(1))
    expect(options.value[0]?.label).toBe('Male')
  })

  it('缓存命中：同 type 二次取用不再请求端点（语义与 mock 源期一致）', async () => {
    requestMock.mockResolvedValue([{ label: '{"zh-CN":"启用"}', value: '1' }])
    const first = useDict('sys_cached')
    await vi.waitFor(() => expect(first.options.value).toHaveLength(1))

    const second = useDict('sys_cached')
    expect(second.options.value).toHaveLength(1)
    expect(requestMock).toHaveBeenCalledTimes(1)
  })

  it('未知类型端点返回空数组，选项空态', async () => {
    requestMock.mockResolvedValue([])
    const { options } = useDict('sys_unknown_type')
    await vi.waitFor(() => expect(options.value).toEqual([]))
  })

  it('端点失败不阻塞：options 空态可重试（invalidate 换新请求）', async () => {
    requestMock.mockRejectedValueOnce(new Error('网络错误'))
    const dict = useDict('sys_fail_fetch')
    await vi.waitFor(() => expect(dict.loading.value).toBe(false))
    expect(dict.options.value).toEqual([])

    requestMock.mockResolvedValue([{ label: '{"zh-CN":"恢复"}', value: 'ok' }])
    await dict.invalidate()
    expect(dict.options.value).toEqual([{ label: '恢复', value: 'ok' }])
  })

  it('setDictSource 可整体换源（注入口契约保持）', async () => {
    setDictSource(async () => [{ label: '外部源', value: 'x' }])
    const { options } = useDict('sys_any')
    await vi.waitFor(() => expect(options.value).toEqual([{ label: '外部源', value: 'x' }]))
  })
})
