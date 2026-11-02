/** vitest 全局 setup：canvas 在测试环境不可用，vue-echarts 统一 stub（需断言 option 的用例在测试文件内自行 mock） */
import { vi } from 'vitest'

vi.mock('vue-echarts', async () => {
  const { defineComponent, h } = await import('vue')
  return {
    default: defineComponent({
      name: 'VChart',
      props: { option: { type: null, default: undefined } },
      setup() {
        return () => h('div', { class: 'v-chart-stub' })
      },
    }),
  }
})
