<script setup lang="ts">
import { useFullscreen, useMediaQuery } from '@vueuse/core'
import { storeToRefs } from 'pinia'
import { computed, ref } from 'vue'
import { useI18n } from 'vue-i18n'
import { useRoute, useRouter } from 'vue-router'

import AppIcon from '@/components/AppIcon.vue'
import { hasAppIcon } from '@/components/app-icons'
import SettingsDrawer from '@/components/settings/SettingsDrawer.vue'
import { logout } from '@/api/auth'
import { broadcastLogout } from '@/api/auth-sync'
import { useMenusStore, type MenuNode } from '@/stores/menus'
import { useSessionStore } from '@/stores/session'
import { useTagsViewStore } from '@/stores/tags-view'

import TagsView from '@/layouts/TagsView.vue'

defineOptions({ name: 'Layout' })

const { t } = useI18n()
const route = useRoute()
const { cachedViews } = storeToRefs(useTagsViewStore())

// 移动端断点：768px 以下侧栏抽屉化
const isMobile = useMediaQuery('(max-width: 768px)')
const mobileOpen = ref(false)

// 侧栏折叠为组件内部状态，刷新还原（持久化随偏好框架）
const collapsed = ref(false)

// 设置抽屉：主题三态、主色、语言集中一处
const settingsOpen = ref(false)
const router = useRouter()

const menusStore = useMenusStore()
const sessionStore = useSessionStore()
const tagsViewStore = useTagsViewStore()
const username = computed(() => menusStore.user?.username ?? '')

/** 退出登录：后端异常也继续清本地（登出的目的是离开）；页签与缓存回首页初始态 */
async function onUserCommand(command: string | number | object) {
  if (command !== 'logout') return
  const token = sessionStore.accessToken
  try {
    if (token) {
      await logout(token)
    }
  } catch {
    // 忽略后端异常，本地全清
  }
  sessionStore.reset()
  menusStore.reset()
  tagsViewStore.closeAll()
  // 同浏览器其他 tab 立即清态踢回登录页（跨设备靠会话失效被动接管）
  broadcastLogout()
  await router.push('/login')
}

/**
 * 菜单 icon 静态映射：i18n key → 前端图标名，库值缺失时的兜底通路
 * （存量菜单无 icon 值照常渲染；库值优先，映射退位兜底）。
 */
const ICON_BY_I18N_KEY: Record<string, string> = {
  'routes.home': 'index',
  'routes.demoCached': 'tab',
  'routes.demoNoCache': 'edit',
  'routes.system': 'system',
  'routes.systemUsers': 'user',
  'routes.systemRoles': 'role',
  'routes.systemDepts': 'dept',
  'routes.systemJobs': 'people',
}

/**
 * 侧栏图标解析（库值优先）：icon 值 `svg:name` 且名在册 → 图标资产名；
 * 无前缀或未知名 → i18n key 静态映射兜底；两处皆无 → undefined 不渲染。
 */
function navIcon(menu: MenuNode): string | undefined {
  const byKey = () => ICON_BY_I18N_KEY[menu.i18nKey ?? '']
  const value = menu.icon ?? ''
  if (value.startsWith('svg:')) {
    const name = value.slice('svg:'.length)
    return name && hasAppIcon(name) ? name : byKey()
  }
  return byKey()
}

/** 侧栏节点：目录（type=0）带子级，页面（type=1）可点；按钮（type=2）不进侧栏 */
interface NavNode {
  key: string
  title: string
  icon?: string
  path?: string
  children?: NavNode[]
}

const navNodes = computed<NavNode[]>(() => {
  const toNode = (menu: MenuNode): NavNode | null => {
    if (menu.type !== 0 && menu.type !== 1) {
      return null
    }
    const node: NavNode = {
      key: `${menu.type}:${menu.i18nKey ?? menu.id}`,
      title: menu.i18nKey ?? '',
      icon: navIcon(menu),
    }
    if (menu.type === 1) {
      if (!menu.componentPathKey) {
        return null
      }
      node.path = `/${menu.componentPathKey}`
    } else {
      node.children = (menu.children ?? [])
        .map(toNode)
        .filter((child): child is NavNode => child !== null)
      if (!node.children.length) {
        return null
      }
    }
    return node
  }
  return menusStore.menus.map(toNode).filter((node): node is NavNode => node !== null)
})

/**
 * 面包屑：菜单树路径链（目录 → 页面）优先；未在树中的页面回退静态路由 matched。
 * 首页常驻首项，目录级无路由为纯文本。
 */
const breadcrumbItems = computed(() => {
  const items: { title: string; path?: string }[] = [{ title: 'routes.home', path: '/home' }]
  const chain: { title: string; path?: string }[] = []
  const walk = (nodes: MenuNode[], trail: { title: string }[]): boolean => {
    for (const node of nodes) {
      const step = { title: node.i18nKey ?? '' }
      if (node.type === 1 && node.componentPathKey && `/${node.componentPathKey}` === route.path) {
        chain.push(...trail, { title: step.title, path: `/${node.componentPathKey}` })
        return true
      }
      if (node.children?.length && walk(node.children, [...trail, step])) {
        return true
      }
    }
    return false
  }
  if (walk(menusStore.menus, [])) {
    items.push(...chain.filter((seg) => seg.path !== '/home'))
    return items
  }
  for (const record of route.matched) {
    if (record.meta?.title && record.path !== '/' && record.path !== '/home') {
      items.push({ title: record.meta.title, path: record.path })
    }
  }
  return items
})

const { isSupported, isFullscreen, toggle } = useFullscreen()

function onTriggerClick() {
  if (isMobile.value) {
    mobileOpen.value = !mobileOpen.value
  } else {
    collapsed.value = !collapsed.value
  }
}

function onMenuSelect() {
  // 移动端选中菜单后收起抽屉
  mobileOpen.value = false
}
</script>

<template>
  <el-container class="layout">
    <div v-if="isMobile && mobileOpen" class="layout__overlay" @click="mobileOpen = false" />
    <aside
      :class="[
        'layout__aside',
        {
          'is-collapsed': collapsed && !isMobile,
          'is-mobile': isMobile,
          'is-open': isMobile && mobileOpen,
        },
      ]"
    >
      <div class="layout__logo">
        <span v-if="collapsed && !isMobile">e+</span>
        <span v-else>eladmin-plus</span>
      </div>
      <el-menu
        :default-active="route.path"
        :collapse="collapsed && !isMobile"
        :collapse-transition="false"
        router
        class="layout__menu"
        @select="onMenuSelect"
      >
        <template v-for="item in navNodes" :key="item.key">
          <el-sub-menu v-if="item.children" :index="item.key">
            <template #title>
              <app-icon v-if="item.icon" :name="item.icon" />
              <span>{{ t(item.title) }}</span>
            </template>
            <el-menu-item v-for="child in item.children" :key="child.key" :index="child.path!">
              <app-icon v-if="child.icon" :name="child.icon" />
              <template #title>{{ t(child.title) }}</template>
            </el-menu-item>
          </el-sub-menu>
          <el-menu-item v-else :index="item.path!">
            <app-icon v-if="item.icon" :name="item.icon" />
            <template #title>{{ t(item.title) }}</template>
          </el-menu-item>
        </template>
      </el-menu>
    </aside>
    <el-container class="layout__body">
      <el-header class="layout__header">
        <button
          type="button"
          class="layout__trigger"
          :title="t('layout.collapseToggle')"
          :aria-label="t('layout.collapseToggle')"
          @click="onTriggerClick"
        >
          <app-icon name="menu" />
        </button>
        <el-breadcrumb class="layout__breadcrumb" separator="/">
          <el-breadcrumb-item v-for="item in breadcrumbItems" :key="item.path" :to="item.path">
            {{ t(item.title) }}
          </el-breadcrumb-item>
        </el-breadcrumb>
        <div class="layout__actions">
          <button
            v-if="isSupported"
            type="button"
            class="layout__trigger"
            :title="t('layout.fullscreenToggle')"
            :aria-label="t('layout.fullscreenToggle')"
            @click="toggle()"
          >
            <app-icon :name="isFullscreen ? 'exit-fullscreen' : 'fullscreen'" />
          </button>
          <button
            type="button"
            class="layout__trigger"
            :title="t('layout.openSettings')"
            :aria-label="t('layout.openSettings')"
            data-test="settings-trigger"
            @click="settingsOpen = true"
          >
            <app-icon name="settings" />
          </button>
          <el-dropdown
            class="layout__user"
            data-test="user-area"
            trigger="click"
            @command="onUserCommand"
          >
            <button
              type="button"
              class="layout__trigger layout__avatar"
              :title="username"
              data-test="user-avatar"
            >
              <app-icon name="user" />
              <span>{{ username }}</span>
            </button>
            <template #dropdown>
              <el-dropdown-menu>
                <el-dropdown-item disabled data-test="user-profile">{{
                  t('layout.profile')
                }}</el-dropdown-item>
                <el-dropdown-item divided command="logout" data-test="user-logout">
                  {{ t('layout.logout') }}
                </el-dropdown-item>
              </el-dropdown-menu>
            </template>
          </el-dropdown>
        </div>
        <SettingsDrawer v-model="settingsOpen" />
      </el-header>
      <TagsView />
      <el-main class="layout__main">
        <router-view v-slot="{ Component }">
          <keep-alive :include="cachedViews">
            <component :is="Component" />
          </keep-alive>
        </router-view>
      </el-main>
      <el-footer class="layout__footer">eladmin-plus</el-footer>
    </el-container>
  </el-container>
</template>

<style scoped lang="scss">
.layout {
  height: 100vh;
}

.layout__aside {
  display: flex;
  flex-direction: column;
  width: 210px;
  transition:
    width 0.2s,
    transform 0.2s;
  background-color: var(--el-bg-color, #fff);
  border-right: 1px solid var(--el-border-color-light, #e4e7ed);
  overflow-x: hidden;

  &.is-collapsed {
    width: 64px;
  }

  &.is-mobile {
    position: fixed;
    top: 0;
    bottom: 0;
    left: 0;
    z-index: 1001;
    transform: translateX(-100%);

    &.is-open {
      transform: translateX(0);
    }
  }
}

.layout__overlay {
  position: fixed;
  inset: 0;
  z-index: 1000;
  background-color: rgb(0 0 0 / 50%);
}

.layout__logo {
  display: flex;
  align-items: center;
  justify-content: center;
  height: 48px;
  font-weight: 700;
  font-size: 16px;
  color: var(--el-text-color-primary, #303133);
  white-space: nowrap;
}

.layout__menu {
  flex: 1;
  border-right: none;

  .app-icon {
    margin-right: 8px;
  }
}

.layout__body {
  min-width: 0;
}

.layout__header {
  display: flex;
  align-items: center;
  gap: 12px;
  height: 48px;
  background-color: var(--el-bg-color, #fff);
  border-bottom: 1px solid var(--el-border-color-light, #e4e7ed);
}

.layout__trigger {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 32px;
  height: 32px;
  padding: 0;
  font-size: 16px;
  color: var(--el-text-color-regular, #606266);
  cursor: pointer;
  background: none;
  border: none;
  border-radius: 6px;
  transition:
    color 0.2s,
    background-color 0.2s;

  &:hover {
    color: var(--el-color-primary, #409eff);
    background: var(--el-fill-color, #f0f2f5);
  }
}

/* 用户区触发器：图标 + 用户名，宽度随内容走，hover 行为与其余触发器一致 */
.layout__avatar {
  width: auto;
  gap: 4px;
  padding: 0 10px;
  font-size: 14px;
}

.layout__breadcrumb {
  min-width: 0;
}

.layout__actions {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-left: auto;
  font-size: 16px;
}

.layout__main {
  background-color: var(--el-bg-color-page, #f2f3f5);
}

.layout__footer {
  display: flex;
  align-items: center;
  justify-content: center;
  height: 36px;
  font-size: 12px;
  color: var(--el-text-color-secondary, #909399);
}
</style>
