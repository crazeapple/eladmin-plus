/** 数字滚动动画：requestAnimationFrame 计数，目标值变化自动重滚（自写实现，不引依赖） */
import { onBeforeUnmount, ref, watch, type Ref } from 'vue'

export function useCountUp(target: Ref<number | undefined>, duration = 2000) {
  const display = ref(0)
  let rafId = 0

  function stop() {
    if (rafId) cancelAnimationFrame(rafId)
    rafId = 0
  }

  function run(from: number, to: number) {
    stop()
    const start = performance.now()
    const step = (now: number) => {
      const progress = Math.min((now - start) / duration, 1)
      // easeOutCubic：前快后慢，数字落地有过程感
      const eased = 1 - Math.pow(1 - progress, 3)
      display.value = Math.round(from + (to - from) * eased)
      if (progress < 1) rafId = requestAnimationFrame(step)
      else rafId = 0
    }
    rafId = requestAnimationFrame(step)
  }

  watch(
    target,
    (to) => {
      if (to === undefined) return
      run(display.value, to)
    },
    { immediate: true },
  )

  onBeforeUnmount(stop)

  return { display }
}
