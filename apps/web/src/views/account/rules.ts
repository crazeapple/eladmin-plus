/**
 * 个人中心资料表单校验规则：零组件依赖纯函数（复杂表单分层定案，与 system/users/rules.ts 同形）。
 * 邮箱与电话不加客户端格式规则：后端是权威校验面（邮箱非法 422 回明确消息，页内展示）。
 */

/** el-form rules 组装（昵称必填防全量替换语义下清空昵称） */
export function buildAccountRules(t: (key: string) => string) {
  return {
    nickName: [{ required: true, message: t('account.nickNameRequired'), trigger: 'blur' }],
  }
}
