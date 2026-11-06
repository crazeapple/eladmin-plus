import { cleanup, fireEvent, render } from '@testing-library/vue'
import { createPinia, setActivePinia } from 'pinia'
import { nextTick } from 'vue'
import { afterEach, describe, expect, it, vi } from 'vitest'

import Home from '@/views/home/index.vue'
import { buildI18n } from '@/i18n'
import { chartPalette } from '@/components/charts/options'
import { DEFAULT_PRIMARY_COLOR, usePreferencesStore } from '@/stores/preferences'
import { lineDatasets } from '../demo-data'
import { statsQueryState } from './stats-query-mock'

/** canvas 在测试环境不可用——mock vue-echarts 组件层，收到的 option 记入 spy 供断言 */
const { optionSpy } = vi.hoisted(() => ({ optionSpy: vi.fn() }))

vi.mock('vue-echarts', async () => {
  const { defineComponent, h } = await import('vue')
  return {
    default: defineComponent({
      name: 'VChart',
      props: { option: { type: null, default: undefined } },
      setup(props) {
        return () => {
          optionSpy(props.option)
          return h('div', { class: 'v-chart-stub' })
        }
      },
    }),
  }
})

vi.mock('@/api/stats', () => import('./stats-query-mock'))

afterEach(() => {
  cleanup()
  vi.useRealTimers()
  vi.clearAllMocks()
  localStorage.clear()
  statsQueryState.data.value = undefined
  statsQueryState.isError.value = false
})

/** 渲染 Home：偏好 store 与 i18n 注入（联动用例直接驱动 store 与 locale） */
function renderHome() {
  const pinia = createPinia()
  setActivePinia(pinia)
  const i18n = buildI18n('zh-CN')
  render(Home, { global: { plugins: [pinia, i18n] } })
  return { i18n, prefs: usePreferencesStore() }
}

interface RecordedOption {
  color?: string[]
  legend?: { data?: string[] }
  xAxis?: { data?: string[] }
  series?: { type?: string; data?: number[] }[]
}

/** spy 收到的是四个图表的全部 option：按折线特征（首系列 type line）取最近一次 */
function lastLineOption(): RecordedOption {
  for (let i = optionSpy.mock.calls.length - 1; i >= 0; i--) {
    const option = optionSpy.mock.calls[i]?.[0] as RecordedOption
    if (option?.series?.[0]?.type === 'line') return option
  }
  throw new Error('折线 option 未被记录')
}

describe('首页版面', () => {
  it('四统计卡片与四图表位渲染齐全', async () => {
    vi.useFakeTimers()
    renderHome()
    await vi.advanceTimersByTimeAsync(2100)

    const cards = document.querySelectorAll('[data-test="stat-card"]')
    expect(cards).toHaveLength(4)
    expect(
      document.querySelectorAll('[data-test="stat-cards"] [data-test="stat-card"]'),
    ).toHaveLength(4)
    for (const name of ['chart-line', 'chart-radar', 'chart-pie', 'chart-bar']) {
      expect(document.querySelector(`[data-test="${name}"]`)).toBeTruthy()
    }
  })

  it('切换语言后卡片文案随动', async () => {
    vi.useFakeTimers()
    const { i18n } = renderHome()
    await vi.advanceTimersByTimeAsync(2100)

    i18n.global.locale.value = 'en'
    await nextTick()

    expect(document.body.textContent).toContain('Users')
    expect(document.body.textContent).toContain('Online users')
    expect(document.body.textContent).toContain('Visits today')
    expect(document.body.textContent).toContain('Logs')
  })
})

describe('端点消费', () => {
  it('pending：卡片数字显占位符', async () => {
    vi.useFakeTimers()
    renderHome()
    await vi.advanceTimersByTimeAsync(100)
    expect((document.body.textContent?.match(/—/g) ?? []).length).toBe(4)
  })

  it('成功：数字取查询数据；数据变化后重新滚动', async () => {
    vi.useFakeTimers()
    statsQueryState.data.value = {
      userCount: 10240,
      onlineCount: 321,
      todayVisitCount: 8123,
      logCount: 2048,
    }
    renderHome()
    await vi.advanceTimersByTimeAsync(2100)
    expect(document.body.textContent).toContain('10240')

    statsQueryState.data.value = {
      userCount: 20000,
      onlineCount: 64,
      todayVisitCount: 9000,
      logCount: 3000,
    }
    await vi.advanceTimersByTimeAsync(2100)
    expect(document.body.textContent).toContain('20000')
    expect(document.body.textContent).not.toContain('10240')
  })

  it('失败：错误条与重试入口；点击触发 refetch', async () => {
    vi.useFakeTimers()
    statsQueryState.isError.value = true
    renderHome()
    await vi.advanceTimersByTimeAsync(100)
    expect(document.querySelector('[data-test="stats-error"]')).toBeTruthy()
    expect(document.body.textContent).toContain('统计数据加载失败，请重试。')

    await fireEvent.click(document.querySelector('[data-test="stats-retry"]') as HTMLElement)
    expect(statsQueryState.refetch).toHaveBeenCalledTimes(1)
  })
})

describe('卡片交互', () => {
  it('点击卡片切换折线数据集', async () => {
    vi.useFakeTimers()
    renderHome()
    await vi.advanceTimersByTimeAsync(100)

    const cards = document.querySelectorAll('[data-test="stat-card"]')
    await fireEvent.click(cards[1] as HTMLElement)
    await nextTick()

    const option = lastLineOption()
    expect(option.series?.[0]).toMatchObject({ data: lineDatasets.online.expected })
    expect(option.series?.[1]).toMatchObject({ data: lineDatasets.online.actual })
  })
})

describe('主题与语言联动', () => {
  it('深浅与主色变更后图表色板按公式重算', async () => {
    vi.useFakeTimers()
    const { prefs } = renderHome()
    await vi.advanceTimersByTimeAsync(100)

    prefs.theme = 'dark'
    await nextTick()
    // 期望值按当前缺省主色（品牌靛蓝）同公式重算，与组件消费侧同源
    const dark = chartPalette(DEFAULT_PRIMARY_COLOR.toLowerCase(), true)
    expect(lastLineOption().color).toEqual([dark.dark2, dark.primary])

    prefs.theme = 'light'
    prefs.primaryColor = '#11a983'
    await nextTick()
    const light = chartPalette('#11a983', false)
    expect(lastLineOption().color).toEqual([light.dark2, light.primary])
  })

  it('切换语言后图例与轴标签随动', async () => {
    vi.useFakeTimers()
    const { i18n } = renderHome()
    await vi.advanceTimersByTimeAsync(100)

    i18n.global.locale.value = 'en'
    await nextTick()
    const option = lastLineOption()
    expect(option.xAxis?.data).toEqual(['Mon', 'Tue', 'Wed', 'Thu', 'Fri', 'Sat', 'Sun'])
    expect(option.legend?.data).toEqual(['Expected', 'Actual'])
  })
})
