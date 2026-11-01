import { cleanup, render, screen } from '@testing-library/vue'
import { afterEach, describe, expect, it, vi } from 'vitest'

import AppIcon from '../AppIcon.vue'

afterEach(cleanup)

describe('AppIcon', () => {
  it('按 name 渲染本地集合中的图标', () => {
    const { container } = render(AppIcon, { props: { name: 'search' } })
    expect(container.querySelector('.app-icon svg')).toBeTruthy()
  })

  it('未知 name 渲染兜底并输出告警', () => {
    const warn = vi.spyOn(console, 'warn').mockImplementation(() => {})
    render(AppIcon, { props: { name: 'no-such-icon' } })
    expect(screen.getByText('?')).toBeTruthy()
    expect(containerIcon()).toBeNull()
    expect(warn).toHaveBeenCalledTimes(1)
    warn.mockRestore()
  })
})

function containerIcon() {
  return document.querySelector('.app-icon svg')
}
