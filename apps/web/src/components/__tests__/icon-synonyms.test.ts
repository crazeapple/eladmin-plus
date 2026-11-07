import { describe, expect, it } from 'vitest'

import { hasEpIcon } from '../ep-icons'
import { EP_TO_NV, NV_TO_EP } from '../icon-synonyms'
import { hasNvIcon } from '../nv-icons'

describe('nv ↔ ep 同义映射', () => {
  it('正向表单射：同一 EP 名不重复对两个 nv 名', () => {
    const epNames = Object.values(NV_TO_EP)
    expect(new Set(epNames).size).toBe(epNames.length)
  })

  it('对子两侧名各自在册（杜绝换边落空）', () => {
    for (const [nvName, epName] of Object.entries(NV_TO_EP)) {
      expect(hasNvIcon(nvName)).toBe(true)
      expect(hasEpIcon(epName)).toBe(true)
    }
  })

  it('反向表为正向倒排派生，双向换算闭合', () => {
    expect(Object.keys(EP_TO_NV)).toHaveLength(Object.keys(NV_TO_EP).length)
    for (const [epName, nvName] of Object.entries(EP_TO_NV)) {
      expect(NV_TO_EP[nvName]).toBe(epName)
    }
  })
})
