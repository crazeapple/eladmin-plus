import { ref } from 'vue'
import type { Ref } from 'vue'
import type { DictOption, DictSource } from './types'

// 数据源由应用注入（setDictSource）：未接源时返回空数组（真实字典归配置域数据源接线）
let dictSource: DictSource = async () => []

/** 注入字典数据源；已入缓存不自动清，需要时对相应 type 调 invalidate */
export function setDictSource(source: DictSource): void {
  dictSource = source
}

// 模块级 Map 缓存：字典量小、随页面集生灭，不进 Pinia（定案 4.2.3）
const cache = new Map<string, DictOption[]>()
const inflight = new Map<string, Promise<DictOption[]>>()

// 同 type 并发共享同一 promise，数据源只请求一次
function fetchDict(type: string): Promise<DictOption[]> {
  const pending = inflight.get(type)
  if (pending) return pending
  const promise = dictSource(type)
    .then((options) => {
      cache.set(type, options)
      return options
    })
    .finally(() => {
      inflight.delete(type)
    })
  inflight.set(type, promise)
  return promise
}

export interface UseDictResult {
  options: Ref<DictOption[]>
  loading: Ref<boolean>
  /** 清该 type 缓存并立即重新拉取（手动失效口子） */
  invalidate: () => Promise<void>
}

export function useDict(type: string): UseDictResult {
  const options = ref<DictOption[]>([])
  const loading = ref(false)

  async function load(): Promise<void> {
    const cached = cache.get(type)
    if (cached) {
      options.value = cached
      return
    }
    loading.value = true
    try {
      options.value = await fetchDict(type)
    } catch {
      // 字典拉取失败不阻塞页面：options 保持空态；缓存与在途态已清理，可重试
    } finally {
      loading.value = false
    }
  }

  async function invalidate(): Promise<void> {
    cache.delete(type)
    await load()
  }

  void load()

  return { options, loading, invalidate }
}
