/**
 * svg ↔ EP 同义映射表：双向风格开关的换算依据，逐对人工定夺（语义贴切优先，
 * 视觉体量接近为次）。表必须单射——同一 EP 名不得对两个 svg 名，反向换算才无歧义。
 * 未收录项（如品牌类 alipay、无对应图形的 database / deploy / refresh-left）
 * 切换风格时保持原库渲染，不强行错换。
 */
export const SVG_TO_EP: Record<string, string> = {
  index: 'home-filled',
  tab: 'files',
  system: 'setting',
  user: 'user',
  role: 'avatar',
  dept: 'office-building',
  people: 'suitcase',
  menu: 'menu',
  dictionary: 'collection',
  timing: 'timer',
  monitor: 'monitor',
  log: 'document',
  error: 'warning',
  peoples: 'user-filled',
  server: 'cpu',
  tools: 'tools',
  qiniu: 'cloudy',
  email: 'promotion',
}

/** 反向换算由同表倒排派生，杜绝两份手写表漂移 */
export const EP_TO_SVG: Record<string, string> = Object.fromEntries(
  Object.entries(SVG_TO_EP).map(([svgName, epName]) => [epName, svgName]),
)
