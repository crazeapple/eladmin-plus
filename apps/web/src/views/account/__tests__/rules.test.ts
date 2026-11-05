import { describe, expect, it } from 'vitest'

import { buildAccountRules, buildPasswordRules } from '../rules'

/** 规则纯函数直测：el-form 校验面在真实浏览器由 E2E 覆盖（happy-dom 下 EP validate 不拦，留档） */
describe('个人中心表单规则', () => {
  it('昵称规则在册且必填', () => {
    const rules = buildAccountRules((key) => `t(${key})`)
    expect(rules.nickName).toHaveLength(1)
    expect(rules.nickName[0]?.required).toBe(true)
    expect(rules.nickName[0]?.message).toBe('t(account.nickNameRequired)')
  })

  it('改密规则三项在册且确认密码校验一致语义', () => {
    const rules = buildPasswordRules(
      (key) => `t(${key})`,
      () => 'new-456',
    )
    expect(rules.oldPassword).toHaveLength(1)
    expect(rules.newPassword).toHaveLength(1)
    const confirmRules = rules.confirmPassword as Array<{
      required?: boolean
      validator?: (rule: unknown, value: string, cb: (e?: Error) => void) => void
    }>
    expect(confirmRules).toHaveLength(2)
    expect(confirmRules[0]?.required).toBe(true)
    // 与新密码一致放行，不一致报错
    const validator = confirmRules[1]?.validator
    expect(validator).toBeTruthy()
    let mismatch: unknown
    validator?.(undefined as never, 'other-111', (e?: Error) => {
      mismatch = e
    })
    expect(mismatch).toBeInstanceOf(Error)
    let same: unknown
    validator?.(undefined as never, 'new-456', (e?: Error) => {
      same = e
    })
    expect(same).toBeUndefined()
  })
})
