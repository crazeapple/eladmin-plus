<script setup lang="ts">
import { useFullscreen, useMediaQuery } from '@vueuse/core'
import { storeToRefs } from 'pinia'
import { computed, ref } from 'vue'
import { useI18n } from 'vue-i18n'
import { useRoute, useRouter } from 'vue-router'

import AppIcon from '@/components/AppIcon.vue'
import SettingsDrawer from '@/components/settings/SettingsDrawer.vue'
import { routes } from '@/router'
import { logout } from '@/api/auth'
import { useMenusStore } from '@/stores/menus'
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
  await router.push('/login')
}

const menuItems = computed(() =>
  (routes[0].children ?? []).map((child) => ({
    path: `/${(child.path as string).replace(/^\//, '')}`,
    title: child.meta?.title ?? '',
    icon: child.meta?.icon,
  })),
)

// 面包屑按静态路由 matched 生成（title 存 i18n key，渲染处 t()），首页常驻首项
const breadcrumbItems = computed(() => {
  const items = [{ title: 'routes.home', path: '/home' }]
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
        <el-menu-item v-for="item in menuItems" :key="item.path" :index="item.path">
          <app-icon v-if="item.icon" :name="item.icon" />
          <template #title>{{ t(item.title) }}</template>
        </el-menu-item>
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
              {{ username }}
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
