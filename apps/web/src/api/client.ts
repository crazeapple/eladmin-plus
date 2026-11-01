import { client } from './generated/client.gen'

/** 后端统一响应结构（成功失败同形） */
interface Envelope<T = unknown> {
  code: number
  message: string
  data: T
}

/** 携带后端 code 的调用错误：message 可直接展示 */
export class ApiError extends Error {
  readonly code: number

  constructor(message: string, code: number) {
    super(message)
    this.name = 'ApiError'
    this.code = code
  }
}

function isEnvelope(value: unknown): value is Envelope {
  return (
    typeof value === 'object' &&
    value !== null &&
    'code' in value &&
    'message' in value &&
    typeof (value as Envelope).code === 'number' &&
    typeof (value as Envelope).message === 'string'
  )
}

// baseURL /api：开发态经 vite proxy 去 /api 前缀转发到后端
client.setConfig({
  baseUrl: '/api',
  // envelope 一处解包：2xx 取 data，调用方拿到纯负载
  responseTransformer: async (data) => (isEnvelope(data) ? data.data : data),
})

// 非 2xx：后端也回同形 envelope，换成 ApiError(message) 挂 code 再抛出
client.interceptors.error.use((error) => {
  if (isEnvelope(error)) {
    return new ApiError(error.message, error.code)
  }
  return error
})

export { client }
