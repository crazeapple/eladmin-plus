import { describe, expect, it } from 'vitest'

import autoFormSource from '../auto-form/AutoForm.vue?raw'
import dataTableSource from '../data-table/DataTable.vue?raw'

// 组件层品牌 token 消费断言：组件样式引用品牌变量（var(--ea-*)）而非字面色值，
// fallback 保证脱离宿主应用时仍有可用形态
describe('组件样式品牌 token 引用', () => {
  it('DataTable 圆角与阴影引用品牌 token（非字面值）', () => {
    expect(dataTableSource).toContain('var(--ea-radius-md,')
    expect(dataTableSource).toContain('var(--ea-shadow-1,')
    // 卡片框不再写字面圆角像素值（圆形 50% 属形状语义，不在约束内）
    expect(dataTableSource).not.toMatch(/border-radius: \d+px/)
  })

  it('DataTable 主色强调引用品牌主色（带 EP 兜底）', () => {
    expect(dataTableSource).toContain('var(--ea-primary,')
  })

  it('AutoForm 圆角引用品牌 token（非字面值）', () => {
    expect(autoFormSource).toContain('var(--ea-radius-sm,')
    expect(autoFormSource).not.toMatch(/border-radius: var\(--el-border-radius-base\)/)
  })

  it('品牌 token 消费全带 fallback 值', () => {
    for (const source of [dataTableSource, autoFormSource]) {
      for (const declaration of source.matchAll(/var\(--ea-[\w-]+, /g)) {
        const text = declaration[0]
        expect(text.endsWith(', '), `var(--ea-*) 必须带 fallback：${text}`).toBe(true)
      }
    }
  })
})
