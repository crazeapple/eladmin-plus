import { fireEvent, render, waitFor } from '@testing-library/vue'
import { beforeEach, describe, expect, it, vi } from 'vitest'

const mocks = vi.hoisted(() => ({
  fetchCaptcha: vi.fn(),
  checkCaptcha: vi.fn(),
}))

vi.mock('@/api/auth', () => ({
  fetchCaptcha: mocks.fetchCaptcha,
  checkCaptcha: mocks.checkCaptcha,
}))

import SliderCaptcha from '@/components/captcha/SliderCaptcha.vue'

const PUZZLE = {
  token: 'puzzle-token',
  back: 'data:image/png;base64,back',
  jigsaw: 'data:image/png;base64,jigsaw',
  jigsawY: 31,
  imageWidth: 310,
  imageHeight: 155,
}

beforeEach(() => {
  vi.clearAllMocks()
  mocks.fetchCaptcha.mockResolvedValue({ ...PUZZLE })
})

function queryTest(container: Element, name: string): HTMLElement {
  const el = container.querySelector(`[data-test="${name}"]`)
  if (!(el instanceof HTMLElement)) throw new Error(`找不到 data-test=${name}`)
  return el
}

/** 把轨道渲染矩形桩成 left 10、宽 130（窄容器形态），拖动坐标按此换算 */
function stubTrackRect(container: Element) {
  const track = queryTest(container, 'slider-track')
  vi.spyOn(track, 'getBoundingClientRect').mockReturnValue({
    x: 10,
    y: 0,
    left: 10,
    top: 0,
    right: 140,
    bottom: 36,
    width: 130,
    height: 36,
    toJSON: () => ({}),
  } as DOMRect)
  return track
}

describe('SliderCaptcha', () => {
  it('出题渲染后拼块按画布比例纵向定位（窄容器不错位）', async () => {
    const { container } = render(SliderCaptcha, { props: { hint: '拖动', failText: '失败' } })
    await waitFor(() => {
      expect(queryTest(container, 'slider-captcha').querySelector('img')).not.toBeNull()
    })
    const jigsaw = queryTest(container, 'slider-captcha').querySelectorAll('img')[1]
    // jigsawY 31 / 画布高 155 = 20%，随显示高度缩放而非原图像素
    expect((jigsaw as HTMLImageElement).style.top).toBe('20%')
  })

  it('拖动距离按画布尺寸换算回原图像素域提交（任意轨道宽度成立）', async () => {
    mocks.checkCaptcha.mockResolvedValue({ ticket: 'tk' })
    const { container, emitted } = render(SliderCaptcha, {
      props: { hint: '拖动', failText: '失败' },
    })
    // 等题目加载完成（imageWidth 就位），否则 onUp 直接短路
    await waitFor(() => {
      expect(queryTest(container, 'slider-captcha').querySelectorAll('img').length).toBe(2)
    })
    const track = stubTrackRect(container)

    // 拖到轨道中点：逻辑域 slideX = 130/260，换算原图域 = 130/260 × 310 = 155
    fireEvent.pointerDown(track, { pointerId: 1, clientX: 10 })
    fireEvent.pointerMove(track, { pointerId: 1, clientX: 75 })
    fireEvent.pointerUp(track, { pointerId: 1 })

    await waitFor(() => {
      expect(mocks.checkCaptcha).toHaveBeenCalledWith('puzzle-token', 155)
    })
    expect(emitted('verified')).toEqual([['tk']])
  })

  it('校验失败展示提示并自动换题，换题完成后提示保留', async () => {
    mocks.checkCaptcha.mockRejectedValue(new Error('invalid'))
    mocks.fetchCaptcha
      .mockResolvedValueOnce({ ...PUZZLE })
      .mockResolvedValue({ ...PUZZLE, jigsaw: 'data:image/png;base64,jigsaw2' })
    const { container } = render(SliderCaptcha, { props: { hint: '拖动', failText: '校验未通过' } })
    await waitFor(() => {
      expect(queryTest(container, 'slider-captcha').querySelector('img')).not.toBeNull()
    })
    const track = stubTrackRect(container)

    fireEvent.pointerDown(track, { pointerId: 1, clientX: 10 })
    fireEvent.pointerMove(track, { pointerId: 1, clientX: 75 })
    fireEvent.pointerUp(track, { pointerId: 1 })

    await waitFor(() => {
      expect(queryTest(container, 'slider-fail').textContent).toContain('校验未通过')
    })
    // 新题图已渲染 = 换题彻底完成，此时提示仍应可见（换题即清等于没提示）
    await waitFor(() => {
      const jigsaw = queryTest(container, 'slider-captcha').querySelectorAll('img')[1]
      expect(jigsaw?.getAttribute('src')).toContain('jigsaw2')
    })
    expect(queryTest(container, 'slider-fail').textContent).toContain('校验未通过')
  })
})
