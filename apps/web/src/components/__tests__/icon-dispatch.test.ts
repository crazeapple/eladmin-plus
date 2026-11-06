import { cleanup, render } from '@testing-library/vue'
import { afterEach, describe, expect, it } from 'vitest'

import MenuIcon from '../MenuIcon.vue'

afterEach(cleanup)

function renderIcon(value?: string | null) {
  return render(MenuIcon, { props: { value } })
}

describe('MenuIcon 前缀分派', () => {
  it('svg: 在册名渲染本地 svg 形态', () => {
    const { container } = renderIcon('svg:settings')
    // 本地集形态：app-icon 直包 svg（无 el-icon 包裹）
    expect(container.querySelector('.app-icon > svg')).toBeTruthy()
    expect(container.querySelector('.el-icon')).toBeNull()
  })

  it('ep: 在册名渲染 el-icon 组件形态', () => {
    const { container } = renderIcon('ep:refresh-left')
    // EP 形态：el-icon 包裹动态组件，svg 具 viewBox 特征
    const elIcon = container.querySelector('.app-icon--ep .el-icon')
    expect(elIcon).toBeTruthy()
    expect(elIcon?.querySelector('svg')).toBeTruthy()
  })

  it('双色继承绑 currentColor：ep 形态 path fill 与 svg 形态 stroke 均随文本色（暗色自适应）', () => {
    const ep = renderIcon('ep:refresh-left')
    // EP 图标 fill 落在每个 path 上（svg 根无 fill 属性）
    expect(ep.container.querySelector('.app-icon--ep svg path')?.getAttribute('fill')).toBe(
      'currentColor',
    )
    ep.unmount()
    // settings.svg 根元素自带 stroke="currentColor"，同绑文本色
    const svg = renderIcon('svg:settings')
    expect(svg.container.querySelector('.app-icon > svg')?.getAttribute('stroke')).toBe(
      'currentColor',
    )
  })

  it('同 em 盒：ep 形态与 svg 形态渲染盒同为 1em app-icon', () => {
    const svg = renderIcon('svg:settings')
    expect(svg.container.querySelector('.app-icon')?.className).not.toContain('app-icon--ep')
    svg.unmount()
    const ep = renderIcon('ep:setting')
    expect(ep.container.querySelector('.app-icon')?.className).toContain('app-icon--ep')
  })

  it('svg: 未知名渲染缺省占位不崩', () => {
    const { container } = renderIcon('svg:no-such-icon')
    expect(container.querySelector('.app-icon--missing')?.textContent).toBe('?')
    expect(container.querySelector('svg')).toBeNull()
  })

  it('ep: 未知名渲染缺省占位不崩', () => {
    const { container } = renderIcon('ep:no-such-icon')
    expect(container.querySelector('.app-icon--missing')?.textContent).toBe('?')
    expect(container.querySelector('svg')).toBeNull()
  })

  it('无前缀裸名（历史脏数据防御）不渲染', () => {
    const { container } = renderIcon('settings')
    expect(container.querySelector('.app-icon')).toBeNull()
  })

  it('缺失与空值不渲染', () => {
    const empty = renderIcon('')
    expect(empty.container.querySelector('.app-icon')).toBeNull()
    empty.unmount()
    const none = renderIcon(null)
    expect(none.container.querySelector('.app-icon')).toBeNull()
  })
})
