import { describe, expect, it } from 'vitest'

import { buildAccountRules } from '../rules'

/** 规则纯函数直测：el-form 校验面在真实浏览器由 E2E 覆盖（happy-dom 下 EP validate 不拦，留档） */
describe('个人中心资料表单规则', () => {
  it('昵称规则在册且必填', () => {
    const rules = buildAccountRules((key) => `t(${key})`)
    expect(rules.nickName).toHaveLength(1)
    expect(rules.nickName[0]?.required).toBe(true)
    expect(rules.nickName[0]?.message).toBe('t(account.nickNameRequired)')
  })
})
