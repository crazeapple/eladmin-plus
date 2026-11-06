import { describe, expect, it } from 'vitest'

import {
  FALLBACK_LOCALE,
  LOCALE_MESSAGES,
  applyLocale,
  buildI18n,
  elementPlusLocale,
  localeKeys,
  registeredLocales,
} from '../index'

/** 递归拍平语言包，产出点分 key 列表 */
function flattenKeys(source: Record<string, unknown>, prefix = ''): string[] {
  return Object.entries(source).flatMap(([key, value]) => {
    const name = prefix ? `${prefix}.${key}` : key
    return typeof value === 'object' && value !== null
      ? flattenKeys(value as Record<string, unknown>, name)
      : [name]
  })
}

const zhKeys = flattenKeys(LOCALE_MESSAGES['zh-CN']).sort()
const enKeys = flattenKeys(LOCALE_MESSAGES.en).sort()

describe('i18n 框架', () => {
  it('双语言包 key 集同构（缺 key 在源头拦住，不依赖 fallback 兜底）', () => {
    expect(enKeys).toEqual(zhKeys)
    expect(zhKeys.length).toBeGreaterThan(0)
  })

  it('实例以 zh-CN 兜底、composition 模式构建', () => {
    const i18n = buildI18n('en')
    expect(i18n.global.locale.value).toBe('en')
    expect(i18n.global.fallbackLocale.value).toBe(FALLBACK_LOCALE)
    expect(i18n.global.t('routes.home')).toBe('Home')
  })

  it('applyLocale 同步实例语言与根元素 lang', () => {
    const target = { locale: { value: 'zh-CN' as string } }
    applyLocale(target, 'en')
    expect(target.locale.value).toBe('en')
    expect(document.documentElement.getAttribute('lang')).toBe('en')
  })

  it('EP 组件文案语言包随 locale 取用', () => {
    expect(elementPlusLocale('zh-CN').name).toBe('zh-cn')
    expect(elementPlusLocale('en').name).toBe('en')
  })
})

describe('配置域词条收口', () => {
  it('路由种子与三页命名空间双包在册', () => {
    for (const locale of ['zh-CN', 'en'] as const) {
      const keys = localeKeys(locale)
      // 身份域四页 + 配置域三页种子路由 key
      for (const route of [
        'routes.systemUsers',
        'routes.systemRoles',
        'routes.systemDepts',
        'routes.systemJobs',
        'routes.systemMenus',
        'routes.systemDicts',
        'routes.systemTasks',
      ]) {
        expect(keys).toContain(route)
      }
      // 配置域三页命名空间在册
      for (const namespace of ['menus', 'dicts', 'tasks']) {
        expect(keys.some((key) => key.startsWith(`${namespace}.`))).toBe(true)
      }
    }
  })

  it('监控域词条收口：目录与四页路由 key、四页命名空间、五条权限点双包在册', () => {
    for (const locale of ['zh-CN', 'en'] as const) {
      const keys = localeKeys(locale)
      // V8 种子路由 key：监控目录 + 四子菜单
      for (const route of [
        'routes.monitor',
        'routes.monitorOperationLogs',
        'routes.monitorExceptionLogs',
        'routes.monitorOnlineUsers',
        'routes.monitorServerInfo',
      ]) {
        expect(keys).toContain(route)
      }
      // 监控四页命名空间在册
      for (const namespace of ['operationLogs', 'exceptionLogs', 'onlineUsers', 'serverInfo']) {
        expect(keys.some((key) => key.startsWith(`${namespace}.`))).toBe(true)
      }
      // V8 五条按钮权限点文案（i18n_key 与种子一致）
      for (const key of [
        'perms.monitorLogList',
        'perms.monitorLogDel',
        'perms.monitorOnlineList',
        'perms.monitorOnlineDel',
        'perms.monitorMonitorList',
      ]) {
        expect(keys).toContain(key)
      }
    }
  })

  it('工具域词条收口：目录与三页路由 key、三页命名空间、四条权限点双包在册', () => {
    for (const locale of ['zh-CN', 'en'] as const) {
      const keys = localeKeys(locale)
      // V10 种子路由 key：工具目录 + 三子菜单
      for (const route of [
        'routes.tools',
        'routes.toolsStorage',
        'routes.toolsEmail',
        'routes.toolsAliPay',
      ]) {
        expect(keys).toContain(route)
      }
      // 已落页命名空间在册
      for (const namespace of ['storage', 'email', 'aliPay']) {
        expect(keys.some((key) => key.startsWith(`${namespace}.`))).toBe(true)
      }
      // V10 四条按钮权限点文案（i18n_key 与种子一致）
      for (const key of [
        'perms.storageList',
        'perms.storageUpload',
        'perms.storageDel',
        'perms.emailSend',
      ]) {
        expect(keys).toContain(key)
      }
    }
  })

  it('运维域词条收口：目录与单页路由 key、页面命名空间、五条权限点双包在册', () => {
    for (const locale of ['zh-CN', 'en'] as const) {
      const keys = localeKeys(locale)
      // V12 种子路由 key：运维目录 + 数据库管理单子菜单
      for (const route of ['routes.ops', 'routes.database']) {
        expect(keys).toContain(route)
      }
      // 数据库管理页命名空间在册
      expect(keys.some((key) => key.startsWith('database.'))).toBe(true)
      // V12 五条按钮权限点文案（i18n_key 与种子一致）
      for (const key of [
        'perms.databaseList',
        'perms.databaseAdd',
        'perms.databaseEdit',
        'perms.databaseDel',
        'perms.databaseBackup',
      ]) {
        expect(keys).toContain(key)
      }
    }
  })

  it('演示域词条收口：目录与两页路由 key 双包在册且子项去前缀', () => {
    // 目录化后 routes.demo 目录在册，两子词条去「演示·」前缀（目录名承担归属）
    for (const locale of ['zh-CN', 'en'] as const) {
      const keys = localeKeys(locale)
      for (const route of ['routes.demo', 'routes.demoCached', 'routes.demoNoCache']) {
        expect(keys).toContain(route)
      }
    }
    const i18n = buildI18n('zh-CN')
    expect(i18n.global.t('routes.demo')).toBe('演示')
    expect(i18n.global.t('routes.demoCached')).toBe('缓存')
    expect(i18n.global.t('routes.demoNoCache')).toBe('不缓存')
    // 页面内标题词条保持页面语境（不随目录化去前缀）
    expect(i18n.global.t('demo.cachedTitle')).toBe('演示·缓存')
    expect(i18n.global.t('demo.noCacheTitle')).toBe('演示·不缓存')
    applyLocale(i18n.global, 'en')
    expect(i18n.global.t('routes.demo')).toBe('Demo')
    expect(i18n.global.t('routes.demoCached')).toBe('Cached')
    expect(i18n.global.t('routes.demoNoCache')).toBe('Not cached')
  })

  it('按钮权限点文案 43 条在册（与菜单种子权限点数一致）', () => {
    for (const locale of ['zh-CN', 'en'] as const) {
      const permKeys = localeKeys(locale).filter((key) => key.startsWith('perms.'))
      expect(permKeys).toHaveLength(43)
      for (const key of [
        'perms.systemMenuList',
        'perms.systemDictDel',
        'perms.systemTaskEdit',
        'perms.systemUserResetPwd',
        'perms.monitorMonitorList',
        'perms.storageList',
        'perms.emailSend',
        'perms.databaseBackup',
      ]) {
        expect(permKeys).toContain(key)
      }
    }
  })

  it('label 动态语言行消费语言注册表：注册表键与语言包键一致', () => {
    expect(registeredLocales()).toEqual(Object.keys(LOCALE_MESSAGES))
    expect(registeredLocales()).toContain(FALLBACK_LOCALE)
  })

  it('语言切换页面文案即时跟随（同实例切换 locale 取值即换）', () => {
    const i18n = buildI18n('zh-CN')
    expect(i18n.global.t('routes.systemMenus')).toBe('菜单管理')
    applyLocale(i18n.global, 'en')
    expect(i18n.global.t('routes.systemMenus')).toBe('Menus')
    expect(i18n.global.t('tasks.execConfirm', { name: 'demo' })).toContain('demo')
  })
})
