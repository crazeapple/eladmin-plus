/** 浅色分支混合基准：向白 */
const LIGHT_MIX_BASE = '#ffffff'

/** 深色分支混合基准：向 EP 暗色背景（与官方暗色变量公式一致） */
const DARK_MIX_BASE = '#141414'

function toRgb(hex: string): [number, number, number] {
  const value = hex.replace('#', '')
  const full = value.length === 3 ? [...value].map((c) => c + c).join('') : value
  return [
    parseInt(full.slice(0, 2), 16),
    parseInt(full.slice(2, 4), 16),
    parseInt(full.slice(4, 6), 16),
  ]
}

function channelToHex(channel: number): string {
  return Math.max(0, Math.min(255, Math.round(channel)))
    .toString(16)
    .padStart(2, '0')
}

/** hex 线性混合：ratio 为 base 占比（0 → color 原样，1 → 全 base） */
export function mix(color: string, base: string, ratio: number): string {
  const [r1, g1, b1] = toRgb(color)
  const [r2, g2, b2] = toRgb(base)
  return `#${channelToHex(r1 + (r2 - r1) * ratio)}${channelToHex(g1 + (g2 - g1) * ratio)}${channelToHex(b1 + (b2 - b1) * ratio)}`
}

/** 主色变体组：浅色分支向白、深色分支向暗底（方向相反），dark-2 两分支分别向黑 / 向白 */
export function primaryVariants(color: string, dark: boolean): Record<string, string> {
  const mixBase = dark ? DARK_MIX_BASE : LIGHT_MIX_BASE
  return {
    '--el-color-primary': color.toLowerCase(),
    '--el-color-primary-light-3': mix(color, mixBase, 0.3),
    '--el-color-primary-light-5': mix(color, mixBase, 0.5),
    '--el-color-primary-light-7': mix(color, mixBase, 0.7),
    '--el-color-primary-light-8': mix(color, mixBase, 0.8),
    '--el-color-primary-light-9': mix(color, mixBase, 0.9),
    '--el-color-primary-dark-2': dark ? mix(color, '#ffffff', 0.2) : mix(color, '#000000', 0.2),
  }
}

/** 预设色板：品牌靛蓝打头，EP 默认蓝殿后保底，避开 success / warning / danger 语义色 */
export const PRESET_COLORS = [
  '#5d87ff',
  '#409eff',
  '#11a983',
  '#13c2c2',
  '#6954dc',
  '#b37feb',
  '#f759ab',
]
