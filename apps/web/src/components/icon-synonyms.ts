/**
 * nv ↔ EP 同义映射表：双向风格开关的换算依据，逐对人工定夺（语义贴切优先，
 * 视觉体量接近为次）。表必须单射——同一 EP 名不得对两个 nv 名，反向换算才无歧义。
 * 仅两侧皆有对应图形的对子入表；单侧缺的（如品牌语义 alipay → wallet 在 EP 集
 * 无同形）切换风格时保持原库渲染，不强行错换。
 */
export const NV_TO_EP: Record<string, string> = {
  'home-outline': 'home-filled',
  'cog-outline': 'setting',
  'person-outline': 'user',
  'person-circle-outline': 'avatar',
  'business-outline': 'office-building',
  'people-outline': 'suitcase',
  'people-circle-outline': 'user-filled',
  'menu-outline': 'menu',
  'library-outline': 'collection',
  'time-outline': 'timer',
  'speedometer-outline': 'monitor',
  'document-text-outline': 'document',
  'warning-outline': 'warning',
  'server-outline': 'cpu',
  'construct-outline': 'tools',
  'cloud-outline': 'cloudy',
  'mail-outline': 'promotion',
}

/** 反向换算由同表倒排派生，杜绝两份手写表漂移 */
export const EP_TO_NV: Record<string, string> = Object.fromEntries(
  Object.entries(NV_TO_EP).map(([nvName, epName]) => [epName, nvName]),
)
