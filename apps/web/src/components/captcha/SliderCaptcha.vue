<script setup lang="ts">
import { computed, ref } from 'vue'
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
const token = ref('')
const trackWidth = 260
const sliderSize = 44
const slideX = ref(0)
const dragging = ref(false)
const failed = ref(false)
const verifiedDone = ref(false)

const percent = computed(() => `${(slideX.value / trackWidth) * 100}%`)

async function loadPuzzle() {
  const puzzle = await fetchCaptcha()
  back.value = puzzle.back
  jigsaw.value = puzzle.jigsaw
  jigsawY.value = puzzle.jigsawY
  token.value = puzzle.token
  slideX.value = 0
  failed.value = false
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
  if (!dragging.value) return
  dragging.value = false
  try {
    const { ticket } = await checkCaptcha(token.value, Math.round(slideX.value))
    verifiedDone.value = true
    emit('verified', ticket)
  } catch {
    failed.value = true
    await loadPuzzle()
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
        :style="{ top: `${jigsawY}px`, left: percent }"
        alt=""
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
