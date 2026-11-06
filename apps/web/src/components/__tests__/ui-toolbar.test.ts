import { afterEach, describe, expect, it } from 'vitest'
import { cleanup, render, screen } from '@testing-library/vue'
import { createPinia, setActivePinia } from 'pinia'
import { defineComponent, h, nextTick } from 'vue'

import { DataTable } from '@eladmin-plus/admin-ui'
import { buildI18n } from '@/i18n'
import { usePreferencesStore } from '@/stores/preferences'
import { useTableTexts } from '@/composables/use-table-texts'

/** harness：真实走 useTableTexts → DataTable texts 的注入链（与列表页同一条接线） */
const Harness = defineComponent({
  setup() {
    const texts = useTableTexts()
    return () =>
      h(DataTable, {
        columns: [{ accessorKey: 'name', header: 'Name' }],
        data: [{ id: 1, name: 'row' }],
        texts: texts.value,
      })
  },
})

function mountHarness() {
  const pinia = createPinia()
  setActivePinia(pinia)
  usePreferencesStore().locale = 'zh-CN'
  const i18n = buildI18n('zh-CN')
  render(Harness, { global: { plugins: [i18n, pinia] } })
  return i18n
}

afterEach(cleanup)

describe('列表工具栏 i18n 收口', () => {
  it('中文缺省渲染「列设置」「刷新」', () => {
    mountHarness()
    expect(screen.getByRole('button', { name: '列设置' })).toBeTruthy()
    expect(screen.getByRole('button', { name: '刷新' })).toBeTruthy()
  })

  it('语言切至 en 后「列设置」「刷新」随 locale 联动渲染英文', async () => {
    const i18n = mountHarness()
    i18n.global.locale.value = 'en'
    await nextTick()
    expect(screen.getByRole('button', { name: 'Columns' })).toBeTruthy()
    expect(screen.getByRole('button', { name: 'Refresh' })).toBeTruthy()
    expect(screen.queryByRole('button', { name: '列设置' })).toBeNull()
    expect(screen.queryByRole('button', { name: '刷新' })).toBeNull()
  })
})
