/** en 语言包：与 zh-CN 的 key 集同构（测试断言），缺失即 fallback zh-CN */
export default {
  common: {
    notFoundDesc: 'Page not found or has been moved.',
    backHome: 'Back to home',
  },
  home: {
    placeholder: 'Dashboard arrives with a later proposal; this is a placeholder view.',
  },
  demo: {
    cachedTitle: 'Demo · Cached',
    cachedDesc: 'This page joins keep-alive: leave and come back, the input below is preserved.',
    cachedPlaceholder: 'Type something, switch away and back',
    noCacheTitle: 'Demo · Not cached',
    noCacheDesc:
      'This page sets meta.noCache: tag can open and close, never cached, destroyed on close.',
    noCachePlaceholder: 'Type something, switching away and back clears it',
  },
  layout: {
    collapseToggle: 'Collapse or expand sidebar',
    searchPlaceholder: 'Menu search (placeholder)',
    sizePlaceholder: 'Size switch (placeholder)',
    fullscreenToggle: 'Toggle fullscreen',
    avatarPlaceholder: 'Avatar menu (placeholder)',
    openSettings: 'Open settings',
  },
  tags: {
    closeCurrent: 'Close current',
    closeOthers: 'Close others',
    closeAll: 'Close all',
  },
  settings: {
    title: 'Settings',
    theme: 'Theme',
    themeLight: 'Light',
    themeDark: 'Dark',
    themeAuto: 'Follow system',
    primaryColor: 'Primary color',
    language: 'Language',
    localeSystem: 'Follow system',
  },
  routes: {
    home: 'Home',
    demoCached: 'Demo · Cached',
    demoNoCache: 'Demo · Not cached',
    notFound: '404',
  },
}
