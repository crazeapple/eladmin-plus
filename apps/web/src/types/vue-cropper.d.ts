/** vue-cropper（Vue 3 适配版）自带 typings 引 .vue 源无声明（坏链）：按消费面经 dist 入口最小声明 */
declare module 'vue-cropper/dist/vue-cropper.es.js' {
  import type { DefineComponent } from 'vue'

  export const VueCropper: DefineComponent<Record<string, unknown>, () => null>
}
