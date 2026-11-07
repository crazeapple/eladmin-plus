import { cleanup, render } from '@testing-library/vue'
import { createPinia, setActivePinia } from 'pinia'
import { nextTick } from 'vue'
import { afterEach, beforeEach, describe, expect, it } from 'vitest'

import MenuIcon from '../MenuIcon.vue'
import { usePreferencesStore } from '@/stores/preferences'

beforeEach(() => {
  setActivePinia(createPinia())
})

afterEach(cleanup)

function renderIcon(value?: string | null) {
  return render(MenuIcon, { props: { value } })
}

describe('MenuIcon 前缀分派', () => {
  it('nv: 在册名渲染 ionicons 组件形态', () => {
    const { container } = renderIcon('nv:home-outline')
    // ionicons 形态：nv 盒直包 svg（自带 viewBox，无 el-icon 包裹）
    const svg = container.querySelector('.app-icon--nv > svg')
    expect(svg).toBeTruthy()
    expect(svg?.getAttribute('viewBox')).toBe('0 0 512 512')
    expect(container.querySelector('.el-icon')).toBeNull()
  })

  it('ep: 在册名渲染 el-icon 组件形态', () => {
    const { container } = renderIcon('ep:refresh-left')
    // EP 形态：el-icon 包裹动态组件
    const elIcon = container.querySelector('.app-icon--ep .el-icon')
    expect(elIcon).toBeTruthy()
    expect(elIcon?.querySelector('svg')).toBeTruthy()
  })

  it('svg: 在册名渲染本地 svg 形态（存量待迁移，直渲）', () => {
    const { container } = renderIcon('svg:settings')
    expect(container.querySelector('.app-icon > svg')).toBeTruthy()
    expect(container.querySelector('.app-icon--nv')).toBeNull()
    expect(container.querySelector('.el-icon')).toBeNull()
  })

  it('双色继承绑 currentColor：nv 描边与 ep 面填充均随文本色（暗色自适应）', () => {
    const nv = renderIcon('nv:home-outline')
    // ionicons 线性描边落在每个 path 上（fill none + stroke currentColor）
    const nvPath = nv.container.querySelector('.app-icon--nv svg path')
    expect(nvPath?.getAttribute('stroke')).toBe('currentColor')
    expect(nvPath?.getAttribute('fill')).toBe('none')
    nv.unmount()
    const ep = renderIcon('ep:refresh-left')
    expect(ep.container.querySelector('.app-icon--ep svg path')?.getAttribute('fill')).toBe(
      'currentColor',
    )
  })

  it('同 em 盒：nv 形态与 ep 形态渲染盒同为 1em app-icon', () => {
    const nv = renderIcon('nv:home-outline')
    expect(nv.container.querySelector('.app-icon')?.className).toContain('app-icon--nv')
    nv.unmount()
    const ep = renderIcon('ep:refresh-left')
    expect(ep.container.querySelector('.app-icon')?.className).toContain('app-icon--ep')
  })

  it('nv: 未知名渲染缺省占位不崩', () => {
    const { container } = renderIcon('nv:no-such-icon')
    expect(container.querySelector('.app-icon--missing')?.textContent).toBe('?')
    expect(container.querySelector('svg')).toBeNull()
  })

  it('ep: 未知名渲染缺省占位不崩', () => {
    const { container } = renderIcon('ep:no-such-icon')
    expect(container.querySelector('.app-icon--missing')?.textContent).toBe('?')
    expect(container.querySelector('svg')).toBeNull()
  })

  it('svg: 未知名渲染缺省占位不崩', () => {
    const { container } = renderIcon('svg:no-such-icon')
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

describe('MenuIcon 风格开关换算（iconSet 偏好 + 同义映射）', () => {
  it('EP 风格下 nv 值经映射换 el-icon 形态，映射缺失保持 nv 形态', () => {
    const prefs = usePreferencesStore()
    prefs.iconSet = 'ep'
    const { container, unmount } = renderIcon('nv:cog-outline')
    // cog-outline → setting 在册：换 EP 形态
    expect(container.querySelector('.app-icon--ep .el-icon svg')).toBeTruthy()
    unmount()
    // wallet-outline 无 EP 同义项：保持 nv 形态
    const unmapped = renderIcon('nv:wallet-outline')
    expect(unmapped.container.querySelector('.app-icon--nv > svg')).toBeTruthy()
    expect(unmapped.container.querySelector('.el-icon')).toBeNull()
  })

  it('缺省（nv）风格下 ep 值经反向映射换 ionicons 形态，映射缺失保持 EP 形态', () => {
    const prefs = usePreferencesStore()
    prefs.iconSet = 'svg'
    const { container, unmount } = renderIcon('ep:user')
    // user → person-outline 在册：换 nv 形态
    expect(container.querySelector('.app-icon--nv > svg')).toBeTruthy()
    expect(container.querySelector('.el-icon')).toBeNull()
    unmount()
    // refresh-left 无 nv 同义项：保持 EP 形态
    const unmapped = renderIcon('ep:refresh-left')
    expect(unmapped.container.querySelector('.app-icon--ep .el-icon svg')).toBeTruthy()
  })

  it('切换偏好即时翻转渲染形态（响应式重渲）', async () => {
    const prefs = usePreferencesStore()
    const { container } = renderIcon('nv:cog-outline')
    // 缺省风格：ionicons 形态
    expect(container.querySelector('.app-icon--nv > svg')).toBeTruthy()
    prefs.iconSet = 'ep'
    await nextTick()
    // cog-outline → setting 在册：翻转为 EP 形态
    expect(container.querySelector('.app-icon--ep .el-icon svg')).toBeTruthy()
  })
})
