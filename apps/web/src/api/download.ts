import { send, type RequestOptions } from './request'

/**
 * 文件下载封装：请求走 parseAs 'blob'，文件名以后端 Content-Disposition 为准，
 * 响应到达即触发浏览器保存（本仓首个文件下载，惯例随首案立）。
 */

/** Content-Disposition 解析：attachment; filename=xxx.xlsx（带引号形态兼容） */
export function filenameFromDisposition(disposition: string | null): string | null {
  if (!disposition) {
    return null
  }
  const match = /filename=(?:"([^"]+)"|([^;\s]+))/i.exec(disposition)
  const name = (match?.[1] ?? match?.[2])?.trim()
  return name || null
}

/** 触发浏览器保存：临时 a 标签点一下即撤，对象 URL 用后即收 */
export function saveBlob(blob: Blob, filename: string): void {
  const url = URL.createObjectURL(blob)
  const anchor = document.createElement('a')
  anchor.href = url
  anchor.download = filename
  document.body.appendChild(anchor)
  anchor.click()
  anchor.remove()
  URL.revokeObjectURL(url)
}

/** 下载文件：返回落盘文件名（页面 toast 提示用），header 缺名时回退下载 */
export async function downloadFile(options: RequestOptions): Promise<string> {
  const { data, response } = await send({ ...options, parseAs: 'blob' })
  const filename =
    filenameFromDisposition(response.headers.get('content-disposition')) ?? 'download'
  saveBlob(data as Blob, filename)
  return filename
}
