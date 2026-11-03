/**
 * 用户表单校验规则与联动：零组件依赖纯函数（复杂表单分层定案），
 * 规则由页面组装进 el-form，联动语义（密码仅新增）由字段显隐承担。
 */

/** 邮箱宽松格式：非空即要求含 @ 与域名点 */
export function isValidEmail(value: string): boolean {
  return /^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(value)
}

/** 手机号宽松格式：可选；非空即要求 5~20 位数字与常见符号 */
export function isValidPhone(value: string): boolean {
  return /^[0-9+\-\s()]{5,20}$/.test(value)
}

/** 用户名：3~32 位字母数字下划线开头 */
export function isValidUsername(value: string): boolean {
  return /^[a-zA-Z0-9_]{3,32}$/.test(value)
}

export type FormMode = 'create' | 'edit'

/** 字段校验器：返回错误文案或空串（空串即通过） */
export function validateUsername(value: string, t: (key: string) => string): string {
  if (!value) {
    return t('users.usernameRequired')
  }
  if (!isValidUsername(value)) {
    return t('users.usernameInvalid')
  }
  return ''
}

export function validateEmail(value: string, t: (key: string) => string): string {
  if (!value) {
    return t('users.emailRequired')
  }
  if (!isValidEmail(value)) {
    return t('users.emailInvalid')
  }
  return ''
}

export function validatePhone(value: string, t: (key: string) => string): string {
  if (value && !isValidPhone(value)) {
    return t('users.phoneInvalid')
  }
  return ''
}

/** 密码仅新增显示（编辑不改密码，后端语义）；新增必填 */
export function isPasswordVisible(mode: FormMode): boolean {
  return mode === 'create'
}

/** el-form rules 组装（联动由字段显隐承担，此处不含 password 的编辑态规则） */
export function buildUserRules(mode: FormMode, t: (key: string) => string) {
  return {
    username: [
      {
        validator: (_rule: unknown, value: string, cb: (e?: Error) => void) => {
          const message = validateUsername(value ?? '', t)
          cb(message ? new Error(message) : undefined)
        },
        trigger: 'blur',
      },
    ],
    nickName: [{ required: true, message: t('users.nickNameRequired'), trigger: 'blur' }],
    email: [
      {
        validator: (_rule: unknown, value: string, cb: (e?: Error) => void) => {
          const message = validateEmail(value ?? '', t)
          cb(message ? new Error(message) : undefined)
        },
        trigger: 'blur',
      },
    ],
    phone: [
      {
        validator: (_rule: unknown, value: string, cb: (e?: Error) => void) => {
          const message = validatePhone(value ?? '', t)
          cb(message ? new Error(message) : undefined)
        },
        trigger: 'blur',
      },
    ],
    // 密码字段仅新增渲染，编辑态空规则即可
    password: isPasswordVisible(mode)
      ? [{ required: true, message: t('users.passwordRequired'), trigger: 'blur' }]
      : [],
  }
}
