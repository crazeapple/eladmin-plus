/**
 * 个人中心表单校验规则：零组件依赖纯函数（复杂表单分层定案，与 system/users/rules.ts 同形）。
 * 邮箱与电话不加客户端格式规则：后端是权威校验面（邮箱非法 422 回明确消息，页内展示）。
 */

/** el-form rules 组装（昵称必填防全量替换语义下清空昵称） */
export function buildAccountRules(t: (key: string) => string) {
  return {
    nickName: [{ required: true, message: t('account.nickNameRequired'), trigger: 'blur' }],
  }
}

/** 改密表单规则：三项必填 + 确认密码与新密码一致（密码强度不加客户端规则，后端权威） */
export function buildPasswordRules(t: (key: string) => string, newPassword: () => string) {
  return {
    oldPassword: [{ required: true, message: t('account.oldPasswordRequired'), trigger: 'blur' }],
    newPassword: [{ required: true, message: t('account.newPasswordRequired'), trigger: 'blur' }],
    confirmPassword: [
      { required: true, message: t('account.confirmPasswordRequired'), trigger: 'blur' },
      {
        validator: (_rule: unknown, value: string, cb: (e?: Error) => void) => {
          cb(value === newPassword() ? undefined : new Error(t('account.confirmPasswordMismatch')))
        },
        trigger: 'blur',
      },
    ],
  }
}
