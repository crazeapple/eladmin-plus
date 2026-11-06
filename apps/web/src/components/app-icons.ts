/** 本地图标集 name→svg 映射（构建期静态枚举，与动态路由视图映射同构） */
const rawIcons = import.meta.glob<string>('../assets/icons/*.svg', {
  eager: true,
  query: '?raw',
  import: 'default',
})

const iconMap: Record<string, string> = {}
for (const [path, svg] of Object.entries(rawIcons)) {
  const name = path
    .split('/')
    .pop()!
    .replace(/\.svg$/, '')
  iconMap[name] = sanitize(svg)
}

/** 图标名是否在册（侧栏库值解析用：svg: 前缀未知名回退静态映射） */
export function hasAppIcon(name: string): boolean {
  return name in iconMap
}

/** 取消毒后的 svg 原文；未知名返回 null */
export function appIconSvg(name: string): string | null {
  return iconMap[name] ?? null
}

// 本地文件本属可信内容，仍做一道去脚本与事件属性的通用消毒
function sanitize(raw: string): string {
  return injectViewBox(
    raw
      .replace(/<script[\s\S]*?<\/script>/gi, '')
      .replace(/\son\w+\s*=\s*"[^"]*"/gi, '')
      .replace(/\son\w+\s*=\s*'[^']*'/gi, ''),
  )
}

/**
 * 无 viewBox 的资产补上 viewBox：图标本以固定宽高属性绘制，侧栏按 1em 盒缩放渲染，
 * 缺 viewBox 时内容不随盒缩放、只露出坐标系左上角（隐形或残块）。宽高为纯数字的
 * 按原值注入；无法解析的维持原文不强改。
 */
function injectViewBox(svg: string): string {
  if (/viewBox=/i.test(svg)) {
    return svg
  }
  const size = svg.match(/<svg[^>]*\swidth="(\d+)"[^>]*\sheight="(\d+)"/)
  if (!size) {
    return svg
  }
  return svg.replace(/<svg/i, `<svg viewBox="0 0 ${size[1]} ${size[2]}"`)
}
