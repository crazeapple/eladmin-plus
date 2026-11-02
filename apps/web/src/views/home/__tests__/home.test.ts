import { render } from '@testing-library/vue'
import { createPinia, setActivePinia } from 'pinia'
import { nextTick } from 'vue'
import { afterEach, describe, expect, it, vi } from 'vitest'

import Home from '@/views/home/index.vue'
import { buildI18n } from '@/i18n'

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

afterEach(() => {
  vi.useRealTimers()
  vi.clearAllMocks()
})

/** 渲染 Home：偏好 store 与 i18n 注入（图表联动用例直接驱动 store 与 locale） */
function renderHome() {
  const pinia = createPinia()
  setActivePinia(pinia)
  const i18n = buildI18n('zh-CN')
  render(Home, { global: { plugins: [pinia, i18n] } })
  return i18n
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
    // 数字滚动到位（静态演示值）
    expect(document.body.textContent).toContain('10240')
  })

  it('切换语言后卡片文案随动', async () => {
    vi.useFakeTimers()
    const i18n = renderHome()
    await vi.advanceTimersByTimeAsync(2100)

    i18n.global.locale.value = 'en'
    await nextTick()

    expect(document.body.textContent).toContain('Users')
    expect(document.body.textContent).toContain('Online users')
    expect(document.body.textContent).toContain('Visits today')
    expect(document.body.textContent).toContain('Logs')
  })
})
