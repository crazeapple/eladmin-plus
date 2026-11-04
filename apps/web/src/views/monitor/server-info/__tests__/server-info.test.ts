import { cleanup, fireEvent, render } from '@testing-library/vue'
import { createPinia, setActivePinia } from 'pinia'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'

import { buildI18n } from '@/i18n'
import ServerInfo from '../index.vue'

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

const { refetchMock, monitorState } = vi.hoisted(() => ({
  refetchMock: vi.fn().mockResolvedValue(undefined),
  monitorState: { data: null as { value: unknown } | null },
}))

vi.mock('@/api/monitor', async () => {
  const { ref } = await import('vue')
  if (!monitorState.data) {
    monitorState.data = ref(undefined)
  }
  return {
    useServerMonitor: () => ({
      data: monitorState.data,
      isFetching: ref(false),
      refetch: refetchMock,
    }),
  }
})

/** 页面渲染前 mock 工厂已建 ref：测试经此写入查询数据 */
function setView(value: unknown) {
  monitorState.data!.value = value
}

function fullView() {
  return {
    cpu: { usedPercent: 42.5, physicalCores: 8, logicalCores: 16 },
    memory: {
      totalBytes: 16 * 1024 ** 3,
      usedBytes: 8 * 1024 ** 3,
      freeBytes: 8 * 1024 ** 3,
      usedPercent: 50,
    },
    swap: { totalBytes: 2 * 1024 ** 3, usedBytes: 1024 ** 3 },
    disks: [{ mount: '/', totalBytes: 500 * 1024 ** 3, usableBytes: 250 * 1024 ** 3 }],
    jvm: {
      heapMaxBytes: 1024 ** 3,
      heapUsedBytes: 512 * 1024 ** 2,
      heapCommittedBytes: 768 * 1024 ** 2,
      threadCount: 42,
    },
    pool: { active: 3, idle: 7, pending: 0, max: 20, allocated: 10 },
  }
}

beforeEach(() => {
  vi.spyOn(window.navigator, 'language', 'get').mockReturnValue('zh-CN')
  setActivePinia(createPinia())
})

afterEach(() => {
  cleanup()
  vi.useRealTimers()
  vi.restoreAllMocks()
  vi.clearAllMocks()
  localStorage.clear()
  setView(undefined)
})

function renderPage() {
  render(ServerInfo, { global: { plugins: [createPinia(), buildI18n('zh-CN')] } })
}

describe('服务监控页（仪表、卡片、轮询与置空占位）', () => {
  it('指标渲染：三仪表值与核数、内存、JVM 副文本在册', () => {
    setView(fullView())
    renderPage()
    expect(document.querySelector('[data-test="server-info-cpu-value"]')?.textContent).toContain(
      '42.5%',
    )
    expect(document.querySelector('[data-test="server-info-memory-value"]')?.textContent).toContain(
      '50.0%',
    )
    expect(document.querySelector('[data-test="server-info-jvm-value"]')?.textContent).toContain(
      '50.0%',
    )
    expect(document.body.textContent).toContain('物理 8 核 / 逻辑 16 核')
    expect(document.body.textContent).toContain('已用 8.0 GB / 共 16.0 GB')
    expect(document.body.textContent).toContain('线程 42')
  })

  it('仪表 option 收到占比段数据（CPU 42.5）', () => {
    setView(fullView())
    renderPage()
    const cpuOption = optionSpy.mock.calls
      .map((call) => call[0])
      .find(
        (option) =>
          Array.isArray(option?.series?.[0]?.data) && option.series[0].data[0]?.name === 'CPU',
      )
    expect(cpuOption).toBeTruthy()
    expect(cpuOption.series[0].data).toEqual([
      { name: 'CPU', value: 42.5 },
      { name: 'rest', value: 57.5 },
    ])
  })

  it('Swap、磁盘与连接池卡片进度条与明细在册', () => {
    setView(fullView())
    renderPage()
    expect(
      document.querySelector('[data-test="server-info-swap-progress"]')?.textContent,
    ).toContain('50%')
    expect(document.querySelector('[data-test="server-info-swap-sub"]')?.textContent).toContain(
      '已用 1.0 GB / 共 2.0 GB',
    )
    expect(document.body.textContent).toContain('/')
    expect(document.body.textContent).toContain('已用 250 GB / 共 500 GB')
    expect(
      document.querySelector('[data-test="server-info-pool-progress"]')?.textContent,
    ).toContain('50%')
    expect(document.querySelector('[data-test="server-info-pool-active"]')?.textContent).toBe('3')
    expect(document.querySelector('[data-test="server-info-pool-idle"]')?.textContent).toBe('7')
    expect(document.querySelector('[data-test="server-info-pool-pending"]')?.textContent).toBe('0')
    expect(document.querySelector('[data-test="server-info-pool-max"]')?.textContent).toBe('20')
  })

  it('置空组渲染「—」占位，不报错不空卡', () => {
    setView({ cpu: null, memory: null, swap: null, disks: null, jvm: null, pool: null })
    renderPage()
    for (const test of [
      'server-info-cpu-empty',
      'server-info-memory-empty',
      'server-info-jvm-empty',
      'server-info-swap-empty',
      'server-info-disks-empty',
      'server-info-pool-empty',
    ]) {
      expect(document.querySelector(`[data-test="${test}"]`)?.textContent).toBe('—')
    }
    expect(document.querySelector('[data-test="server-info-cpu-value"]')).toBeNull()
    expect(document.querySelector('[data-test="server-info-pool-active"]')).toBeNull()
  })

  it('数据整体未达（首屏前）同样全占位', () => {
    setView(undefined)
    renderPage()
    expect(document.querySelector('[data-test="server-info-cpu-empty"]')?.textContent).toBe('—')
    expect(document.querySelector('[data-test="server-info-pool-empty"]')?.textContent).toBe('—')
  })

  it('10 秒轮询与手动刷新共用取数函数，卸载后定时器销毁', async () => {
    vi.useFakeTimers()
    renderPage()

    // 手动刷新即取数
    await fireEvent.click(
      document.querySelector('[data-test="server-info-refresh"]') as HTMLElement,
    )
    expect(refetchMock).toHaveBeenCalledTimes(1)

    // 轮询：10 秒一次
    await vi.advanceTimersByTimeAsync(10_000)
    expect(refetchMock).toHaveBeenCalledTimes(2)
    await vi.advanceTimersByTimeAsync(10_000)
    expect(refetchMock).toHaveBeenCalledTimes(3)

    // 卸载后不再续跑
    cleanup()
    await vi.advanceTimersByTimeAsync(60_000)
    expect(refetchMock).toHaveBeenCalledTimes(3)
  })
})
