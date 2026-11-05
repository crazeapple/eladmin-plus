import type { Ref } from 'vue'
import { onBeforeUnmount, ref, watch } from 'vue'

import { storageFileBlobUrl } from '@/api/storage'

/**
 * 头像文件 id → blob 对象 URL：下载端点须带鉴权头，img 直挂 src 拿不到，
 * 先 blob 后 objectURL；id 变更与组件卸载即 revoke 旧址，过期响应不覆盖新址。
 */
export function useAvatarUrl(avatarId: Ref<number | null | undefined>) {
  const avatarUrl = ref('')
  let seq = 0

  watch(
    avatarId,
    async (id) => {
      const current = ++seq
      const previous = avatarUrl.value
      avatarUrl.value = ''
      if (!id) {
        return
      }
      try {
        const url = await storageFileBlobUrl(id)
        if (current !== seq) {
          // 已被更新的 id 抢先：过期结果用后即收
          URL.revokeObjectURL(url)
          return
        }
        avatarUrl.value = url
      } catch {
        avatarUrl.value = ''
      } finally {
        if (previous) {
          URL.revokeObjectURL(previous)
        }
      }
    },
    { immediate: true },
  )

  onBeforeUnmount(() => {
    if (avatarUrl.value) {
      URL.revokeObjectURL(avatarUrl.value)
    }
  })

  return avatarUrl
}
