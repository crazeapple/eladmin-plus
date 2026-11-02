/**
 * 视图模块映射：构建期由 import.meta.glob 枚举全部页面组件（配错菜单的组件路径 key 编译期即暴露）。
 * 反向导出清单供后续菜单管理下拉使用（P-013 / P-014）。
 */
// 页面组件约定 = views 下的 index.vue；components 子目录等非页面文件天然出局
const modules = import.meta.glob('../views/**/index.vue') as Record<
  string,
  () => Promise<{ default: { name?: string } }>
>

/** 不参与动态注册的视图：登录页在壳外，404 为静态兜底路由 */
const EXCLUDED_VIEW_KEYS = ['../views/login/index.vue']

export interface LoadedView {
  component: () => Promise<{ default: { name?: string } }>
  /** 组件 defineOptions 的 name（动态路由的 name 即它，keep-alive 前提） */
  name: string
}

/** 组件路径 key（后端菜单字段）→ 视图模块键；不存在返回 null */
export function resolveViewKey(componentPathKey: string): string | null {
  // 兼容两种形态：home/index（文件式）与 demo/cached（目录式，映射到 demo/cached/index.vue）
  const candidates = [`../views/${componentPathKey}.vue`, `../views/${componentPathKey}/index.vue`]
  const key = candidates.find(
    (candidate) => modules[candidate] && !EXCLUDED_VIEW_KEYS.includes(candidate),
  )
  return key ?? null
}

/** 加载视图模块并取组件 name（name 缺失即配错，返回 null 交守卫跳过并告警） */
export async function loadView(componentPathKey: string): Promise<LoadedView | null> {
  const key = resolveViewKey(componentPathKey)
  if (!key) {
    return null
  }
  const loader = modules[key]
  const mod = await loader()
  // 组件在模块 default 上，name 来自 defineOptions
  const component = mod.default
  if (!component?.name) {
    console.warn(`视图 ${key} 缺少 defineOptions name，无法动态注册路由`)
    return null
  }
  return { component: loader, name: component.name }
}

/** 全部可注册视图的组件路径 key（菜单管理下拉用）：目录式文件归一为去掉 /index 的形态 */
export function listViewKeys(): string[] {
  return Object.keys(modules)
    .filter((key) => !EXCLUDED_VIEW_KEYS.includes(key))
    .map((key) => {
      const withoutExt = key.replace('../views/', '').replace(/\.vue$/, '')
      // demo/cached/index → demo/cached；home/index 无同名简短文件 → 保持 home/index
      const shortForm = withoutExt.replace(/\/index$/, '')
      return shortForm && !modules[`../views/${shortForm}.vue`] ? shortForm : withoutExt
    })
    .filter((key, index, all) => all.indexOf(key) === index)
}
