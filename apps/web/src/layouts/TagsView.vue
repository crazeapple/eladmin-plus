<script setup lang="ts">
import { storeToRefs } from 'pinia'
import { computed, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { useI18n } from 'vue-i18n'
import { useRoute, useRouter } from 'vue-router'

import { useTagsViewStore, HOME_TAG, type TagView } from '@/stores/tags-view'

defineOptions({ name: 'TagsView' })

const { t } = useI18n()
const store = useTagsViewStore()
const { visitedViews } = storeToRefs(store)
const route = useRoute()
const router = useRouter()

// 路由变更即登记页签
watch(
  () => route.path,
  () => store.addView(route),
  { immediate: true },
)

const menu = ref<{ visible: boolean; x: number; y: number; target: TagView | null }>({
  visible: false,
  x: 0,
  y: 0,
  target: null,
})

const menuStyle = computed(() => ({ left: `${menu.value.x}px`, top: `${menu.value.y}px` }))

function openMenu(event: MouseEvent, view: TagView) {
  menu.value = { visible: true, x: event.clientX, y: event.clientY, target: view }
}

function closeMenu() {
  menu.value = { ...menu.value, visible: false }
}

function isHome(view: TagView) {
  return view.path === HOME_TAG.path
}

function isActive(view: TagView) {
  return view.path === route.path
}

/** 活跃页签被关后，跳最后存活页签（无则首页） */
function goLastAlive() {
  const last = visitedViews.value.at(-1)
  router.push(last?.path ?? HOME_TAG.path)
}

function handleClose(view: TagView) {
  if (isHome(view)) return
  const wasActive = view.path === route.path
  store.closeCurrent(view.path)
  if (wasActive) goLastAlive()
  closeMenu()
}

function handleCloseCurrent() {
  const target = menu.value.target
  if (target) handleClose(target)
}

function handleCloseOthers() {
  const target = menu.value.target
  if (!target) return
  store.closeOthers(target.path)
  if (!visitedViews.value.some((view) => view.path === route.path)) {
    router.push(target.path)
  }
  closeMenu()
}

function handleCloseAll() {
  store.closeAll()
  if (route.path !== HOME_TAG.path) router.push(HOME_TAG.path)
  closeMenu()
}

onMounted(() => document.addEventListener('click', closeMenu))
onBeforeUnmount(() => document.removeEventListener('click', closeMenu))
</script>

<template>
  <div class="tags-view" data-test="tags-view">
    <el-tag
      v-for="view in visitedViews"
      :key="view.path"
      :closable="!isHome(view)"
      :effect="isActive(view) ? 'dark' : 'plain'"
      class="tags-view__tag"
      @close="handleClose(view)"
      @click="router.push(view.path)"
      @contextmenu.prevent="openMenu($event, view)"
    >
      {{ t(view.title) }}
    </el-tag>
    <teleport to="body">
      <ul v-if="menu.visible" class="tags-view__menu" :style="menuStyle" data-test="tags-menu">
        <li @click="handleCloseCurrent">{{ t('tags.closeCurrent') }}</li>
        <li @click="handleCloseOthers">{{ t('tags.closeOthers') }}</li>
        <li @click="handleCloseAll">{{ t('tags.closeAll') }}</li>
      </ul>
    </teleport>
  </div>
</template>

<style scoped lang="scss">
.tags-view {
  display: flex;
  align-items: center;
  gap: 6px;
  padding: 6px 12px;
  background-color: var(--ea-header-bg, #fff);
  border-bottom: 1px solid var(--ea-sidebar-border, #e4e7ed);
}

.tags-view__tag {
  cursor: pointer;
  user-select: none;
  /* 页签圆角片形态：圆角走品牌档位 */
  border-radius: var(--ea-radius-sm, 6px);
}

.tags-view__menu {
  position: fixed;
  z-index: 2000;
  min-width: 120px;
  margin: 0;
  padding: 4px 0;
  list-style: none;
  background-color: var(--el-bg-color-overlay, #fff);
  border: 1px solid var(--el-border-color-light, #e4e7ed);
  border-radius: var(--ea-radius-md, 8px);
  box-shadow: var(--ea-shadow-2, var(--el-box-shadow-light, 0 0 12px rgb(0 0 0 / 12%)));

  li {
    padding: 6px 16px;
    font-size: 13px;
    color: var(--el-text-color-regular, #606266);
    cursor: pointer;

    &:hover {
      background-color: var(--el-fill-color-light, #f5f7fa);
      color: var(--el-color-primary, #409eff);
    }
  }
}
</style>
