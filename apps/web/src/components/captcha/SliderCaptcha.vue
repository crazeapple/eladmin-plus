<script setup lang="ts">
import { computed, onUnmounted, ref } from 'vue'
import { checkCaptcha, fetchCaptcha } from '@/api/auth'

/**
 * 滑块验证码：拉题渲染双图，拖动拼块对齐缺口，松手校验；
 * 通过向父组件发 ticket（一次性），失败自动换题重来，出图失败提示可重试。
 */
const emit = defineEmits<{
  verified: [ticket: string]
}>()

defineProps<{
  /** 拖动条文案（i18n 注入） */
  hint: string
  /** 校验失败提示（i18n 注入，组件只展示不持语言包） */
  failText: string
  /** 出图失败的重试文案（i18n 注入，可缺省回退 failText） */
  retryText?: string
}>()

const back = ref('')
const jigsaw = ref('')
const jigsawY = ref(0)
const imageWidth = ref(0)
const imageHeight = ref(0)
const token = ref('')
const trackWidth = 260
const sliderSize = 44
const slideX = ref(0)
const dragging = ref(false)
const failed = ref(false)
const verifiedDone = ref(false)
const loadFailed = ref(false)
// 拼块渲染宽占画布百分比：出图档位可变，按拼块 PNG 实际像素宽回填（缺省取 44/310 基线）
const pieceWidthPercent = ref((sliderSize / 310) * 100)
let failTimer: ReturnType<typeof setTimeout> | null = null

onUnmounted(() => {
  if (failTimer) clearTimeout(failTimer)
})

const percent = computed(() => `${(slideX.value / trackWidth) * 100}%`)
// 拼块纵向按画布比例定位：显示高度随容器宽缩放，原图像素直用会在窄容器垂直错位
const topPercent = computed(() => (jigsawY.value / imageHeight.value) * 100)

/** 拼块图加载完成：以 PNG 原生宽度换算渲染宽占比，档位变化时与缺口对齐 */
function onPieceLoad(event: Event) {
  const img = event.target as HTMLImageElement
  if (img.naturalWidth > 0 && imageWidth.value > 0) {
    pieceWidthPercent.value = (img.naturalWidth / imageWidth.value) * 100
  }
}

async function loadPuzzle() {
  loadFailed.value = false
  try {
    const puzzle = await fetchCaptcha()
    back.value = puzzle.back
    jigsaw.value = puzzle.jigsaw
    jigsawY.value = puzzle.jigsawY
    imageWidth.value = puzzle.imageWidth
    imageHeight.value = puzzle.imageHeight
    token.value = puzzle.token
    slideX.value = 0
  } catch {
    // 出图失败：弹层内错误提示，用户重试或关层后再点登录重新触发
    loadFailed.value = true
  }
}

void loadPuzzle()

function onDown(event: PointerEvent) {
  if (verifiedDone.value) return
  dragging.value = true
  const target = event.currentTarget as HTMLElement | null
  target?.setPointerCapture?.(event.pointerId)
}

function onMove(event: PointerEvent) {
  if (!dragging.value) return
  const track = event.currentTarget as HTMLElement
  const rect = track.getBoundingClientRect()
  slideX.value = Math.min(
    trackWidth,
    Math.max(0, ((event.clientX - rect.left) / rect.width) * trackWidth),
  )
}

async function onUp() {
  if (!dragging.value || !imageWidth.value) return
  dragging.value = false
  try {
    // 后端答案是原图像素域：把轨道逻辑域的拖动距离按画布尺寸换算回去
    const moveDistance = Math.round((slideX.value / trackWidth) * imageWidth.value)
    const { ticket } = await checkCaptcha(token.value, moveDistance)
    verifiedDone.value = true
    emit('verified', ticket)
  } catch {
    // 失败提示换题后保留数秒再清（换题即清等于没提示）
    failed.value = true
    if (failTimer) clearTimeout(failTimer)
    await loadPuzzle()
    failTimer = setTimeout(() => (failed.value = false), 3000)
  }
}
</script>

<template>
  <div class="slider-captcha" data-test="slider-captcha">
    <div v-if="loadFailed" class="slider-captcha__error" data-test="slider-load-error">
      <span>{{ retryText || failText }}</span>
      <button
        type="button"
        class="slider-captcha__retry"
        data-test="slider-retry"
        @click="loadPuzzle"
      >
        {{ retryText || failText }}
      </button>
    </div>
    <template v-else>
      <div class="slider-captcha__puzzle">
        <img v-if="back" class="slider-captcha__back" :src="back" alt="" />
        <img
          v-if="jigsaw"
          class="slider-captcha__jigsaw"
          :src="jigsaw"
          :style="{ top: `${topPercent}%`, left: percent, width: `${pieceWidthPercent}%` }"
          alt=""
          @load="onPieceLoad"
        />
        <div v-if="!back" class="slider-captcha__loading">···</div>
      </div>
      <div
        class="slider-captcha__track"
        data-test="slider-track"
        @pointerdown="onDown"
        @pointermove="onMove"
        @pointerup="onUp"
      >
        <div
          class="slider-captcha__slider"
          data-test="slider-handle"
          :class="{ 'slider-captcha__slider--done': verifiedDone }"
          :style="{ left: percent, width: `${sliderSize}px` }"
        >
          »
        </div>
        <span v-if="!verifiedDone" class="slider-captcha__hint">{{ hint }}</span>
      </div>
    </template>
    <div v-if="failed" class="slider-captcha__fail" data-test="slider-fail">{{ failText }}</div>
  </div>
</template>

<style scoped>
.slider-captcha__puzzle {
  position: relative;
  width: 100%;
  overflow: hidden;
  background: var(--el-fill-color-lighter, #fafafa);
  border: 1px solid var(--ea-card-border, var(--el-border-color-lighter, #e4e7ed));
  border-radius: var(--ea-radius-md, 8px);
}

.slider-captcha__back {
  display: block;
  width: 100%;
}

.slider-captcha__jigsaw {
  position: absolute;
  /* 渲染宽随出图档位以行内样式回填（PNG 原生宽 / 画布宽），此处仅兜底 */
  width: 14.2%;
}

.slider-captcha__loading {
  position: absolute;
  inset: 0;
  display: grid;
  place-items: center;
  font-size: 14px;
  letter-spacing: 4px;
  color: var(--el-text-color-secondary, #909399);
  background: var(--el-fill-color-lighter, #fafafa);
}

.slider-captcha__track {
  position: relative;
  display: flex;
  align-items: center;
  justify-content: center;
  height: 40px;
  margin-top: 10px;
  background: var(--el-fill-color, #f0f2f5);
  border-radius: var(--ea-radius-md, 8px);
  cursor: pointer;
  user-select: none;
  /* 触屏拖动不打断（禁掉手势滚动） */
  touch-action: none;
}

.slider-captcha__slider {
  position: absolute;
  top: 0;
  left: 0;
  display: flex;
  align-items: center;
  justify-content: center;
  height: 40px;
  color: #fff;
  font-size: 16px;
  background: var(--ea-gradient-primary, var(--el-color-primary));
  border-radius: var(--ea-radius-md, 8px);
  box-shadow: var(--ea-shadow-1, 0 1px 2px rgb(15 23 42 / 6%));
}

.slider-captcha__slider--done {
  background: var(--el-color-success, #67c23a);
}

.slider-captcha__hint {
  font-size: 12px;
  color: var(--el-text-color-secondary, #909399);
}

.slider-captcha__fail {
  margin-top: 6px;
  font-size: 12px;
  color: var(--el-color-danger, #f56c6c);
}

.slider-captcha__error {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 8px;
  padding: 16px;
  font-size: 13px;
  color: var(--el-text-color-secondary, #909399);
  border: 1px dashed var(--el-border-color, #dcdfe6);
  border-radius: var(--ea-radius-md, 8px);
}

.slider-captcha__retry {
  padding: 4px 16px;
  font-size: 13px;
  color: var(--ea-primary, var(--el-color-primary, #409eff));
  cursor: pointer;
  background: none;
  border: 1px solid var(--ea-primary, var(--el-color-primary, #409eff));
  border-radius: var(--ea-radius-sm, 6px);
}
</style>
