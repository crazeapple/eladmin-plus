import { describe, expect, it } from 'vitest'

import { hasNvIcon, NV_ICONS } from '../nv-icons'

/** 在用 25 语义全集（选型拍板）：菜单在用 21 名 + 固定 UI 消费 4 名 */
const NV_NAMES = [
  'albums-outline',
  'analytics-outline',
  'business-outline',
  'cloud-outline',
  'code-slash-outline',
  'cog-outline',
  'contract-outline',
  'construct-outline',
  'document-text-outline',
  'expand-outline',
  'home-outline',
  'library-outline',
  'mail-outline',
  'menu-outline',
  'people-circle-outline',
  'people-outline',
  'person-circle-outline',
  'person-outline',
  'rocket-outline',
  'server-outline',
  'settings-outline',
  'speedometer-outline',
  'time-outline',
  'wallet-outline',
  'warning-outline',
]

describe('nv-icons 注册集', () => {
  it('在册 25 名与语义全集一致（无缺漏、无冗余）', () => {
    expect(Object.keys(NV_ICONS).sort()).toEqual([...NV_NAMES].sort())
  })

  it('名段与包导出名直映：kebab → Pascal + Outline', () => {
    for (const [name, component] of Object.entries(NV_ICONS)) {
      const pascal = name
        .split('-')
        .map((part) => part.charAt(0).toUpperCase() + part.slice(1))
        .join('')
      expect((component as { name?: string }).name).toBe(pascal)
    }
  })

  it('hasNvIcon 在册判定与注册集同源', () => {
    for (const name of NV_NAMES) {
      expect(hasNvIcon(name)).toBe(true)
    }
    expect(hasNvIcon('no-such-icon')).toBe(false)
    expect(hasNvIcon('')).toBe(false)
  })
})
