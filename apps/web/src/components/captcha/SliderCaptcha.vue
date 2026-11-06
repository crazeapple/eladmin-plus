<script setup lang="ts">
import { computed, onUnmounted, ref } from 'vue'
import { checkCaptcha, fetchCaptcha } from '@/api/auth'

/**
 * 滑块验证码：拉题渲染双图，拖动拼块对齐缺口，松手校验；
 * 通过向父组件发 ticket（一次性），失败自动换题重来。
 */
const emit = defineEmits<{
  verified: [ticket: string]
}>()

defineProps<{
  /** 拖动条文案（i18n 注入） */
  hint: string
  /** 校验失败提示（i18n 注入，组件只展示不持语言包） */
  failText: string
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
// 拼块渲染宽占画布百分比：出图档位可变，按拼块 PNG 实际像素宽回填（缺省取 44/310 基线）
const pieceWidthPercent = ref((44 / 310) * 100)
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
  const puzzle = await fetchCaptcha()
  back.value = puzzle.back
  jigsaw.value = puzzle.jigsaw
  jigsawY.value = puzzle.jigsawY
  imageWidth.value = puzzle.imageWidth
  imageHeight.value = puzzle.imageHeight
  token.value = puzzle.token
  slideX.value = 0
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
      ></div>
      <span v-if="!verifiedDone" class="slider-captcha__hint">{{ hint }}</span>
    </div>
    <div v-if="failed" class="slider-captcha__fail" data-test="slider-fail">{{ failText }}</div>
  </div>
</template>

<style scoped>
.slider-captcha__puzzle {
  position: relative;
  width: 100%;
  overflow: hidden;
  border-radius: 4px;
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

.slider-captcha__track {
  position: relative;
  display: flex;
  align-items: center;
  justify-content: center;
  height: 36px;
  margin-top: 8px;
  background: var(--el-fill-color-light);
  border-radius: 4px;
  cursor: pointer;
  user-select: none;
  /* 触屏拖动不打断（禁掉手势滚动） */
  touch-action: none;
}

.slider-captcha__slider {
  position: absolute;
  left: 0;
  height: 36px;
  background: var(--el-color-primary);
  border-radius: 4px;
}

.slider-captcha__slider--done {
  background: var(--el-color-success);
}

.slider-captcha__hint {
  font-size: 12px;
  color: var(--el-text-color-secondary);
}

.slider-captcha__fail {
  margin-top: 4px;
  font-size: 12px;
  color: var(--el-color-danger);
}
</style>
