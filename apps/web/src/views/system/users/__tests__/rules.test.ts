import { describe, expect, it } from 'vitest'

import {
  buildUserRules,
  isPasswordVisible,
  isValidEmail,
  isValidPhone,
  isValidUsername,
} from '../rules'

const t = (key: string) => key

describe('用户表单规则纯函数', () => {
  it('用户名格式判定', () => {
    expect(isValidUsername('abc_123')).toBe(true)
    expect(isValidUsername('ab')).toBe(false)
    expect(isValidUsername('a'.repeat(33))).toBe(false)
    expect(isValidUsername('含中文')).toBe(false)
  })

  it('邮箱与手机号宽松格式判定', () => {
    expect(isValidEmail('a@b.co')).toBe(true)
    expect(isValidEmail('a@b')).toBe(false)
    expect(isValidEmail('无邮箱')).toBe(false)
    // 空串的正则判定为 false；可选语义由 validatePhone 的空值守卫承担
    expect(isValidPhone('')).toBe(false)
    expect(isValidPhone('13800138000')).toBe(true)
    expect(isValidPhone('+86 138 0013 8000')).toBe(true)
    expect(isValidPhone('abc')).toBe(false)
  })

  it('可选字段守卫：validatePhone 对空串放行', async () => {
    const { validatePhone } = await import('../rules')
    expect(validatePhone('', t)).toBe('')
    expect(validatePhone('13800138000', t)).toBe('')
    expect(validatePhone('abc', t)).toBe('users.phoneInvalid')
  })

  it('密码字段仅新增可见', () => {
    expect(isPasswordVisible('create')).toBe(true)
    expect(isPasswordVisible('edit')).toBe(false)
    // 编辑态无 password 必填规则
    expect(buildUserRules('edit', t).password).toEqual([])
    expect(buildUserRules('create', t).password.length).toBe(1)
  })
})
