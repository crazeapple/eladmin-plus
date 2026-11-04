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
  return raw
    .replace(/<script[\s\S]*?<\/script>/gi, '')
    .replace(/\son\w+\s*=\s*"[^"]*"/gi, '')
    .replace(/\son\w+\s*=\s*'[^']*'/gi, '')
}
