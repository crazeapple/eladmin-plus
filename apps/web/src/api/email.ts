import { useMutation } from '@tanstack/vue-query'

import type { EmailSendRequest } from './generated'
import { request } from './request'

/** tools 邮件域契约薄封装：真实发送（不落库，无查询面）；错误消息页内展示 */
export function useEmailSend() {
  return useMutation({
    mutationFn: (payload: EmailSendRequest) =>
      request({ url: '/tools/email/send', method: 'POST', body: payload }),
  })
}
