import type { Component } from 'vue'
import {
  AlbumsOutline,
  AnalyticsOutline,
  BusinessOutline,
  CloudOutline,
  CodeSlashOutline,
  CogOutline,
  ConstructOutline,
  ContractOutline,
  DocumentTextOutline,
  ExpandOutline,
  HomeOutline,
  LibraryOutline,
  MailOutline,
  MenuOutline,
  PeopleCircleOutline,
  PeopleOutline,
  PersonCircleOutline,
  PersonOutline,
  RocketOutline,
  ServerOutline,
  SettingsOutline,
  SpeedometerOutline,
  TimeOutline,
  WalletOutline,
  WarningOutline,
} from '@vicons/ionicons5'

/**
 * Ionicons 图标具名清单：@vicons/ionicons5 Outline 变体挑在用 25 语义（P-027
 * 接替 svg 资产源；具名引入免全量 500+ 枚进 bundle）。key 为 `nv:` 库值的名段
 * （Outline 全名 kebab 形，与包导出名直映：kebab → Pascal + Outline），增补只动这份常量。
 */
export const NV_ICONS: Record<string, Component> = {
  'albums-outline': AlbumsOutline,
  'analytics-outline': AnalyticsOutline,
  'business-outline': BusinessOutline,
  'cloud-outline': CloudOutline,
  'code-slash-outline': CodeSlashOutline,
  'cog-outline': CogOutline,
  'construct-outline': ConstructOutline,
  'contract-outline': ContractOutline,
  'document-text-outline': DocumentTextOutline,
  'expand-outline': ExpandOutline,
  'home-outline': HomeOutline,
  'library-outline': LibraryOutline,
  'mail-outline': MailOutline,
  'menu-outline': MenuOutline,
  'people-circle-outline': PeopleCircleOutline,
  'people-outline': PeopleOutline,
  'person-circle-outline': PersonCircleOutline,
  'person-outline': PersonOutline,
  'rocket-outline': RocketOutline,
  'server-outline': ServerOutline,
  'settings-outline': SettingsOutline,
  'speedometer-outline': SpeedometerOutline,
  'time-outline': TimeOutline,
  'wallet-outline': WalletOutline,
  'warning-outline': WarningOutline,
}

/** nv 名是否在册（`nv:` 库值解析用：未知名走缺省占位） */
export function hasNvIcon(name: string): boolean {
  return name in NV_ICONS
}
