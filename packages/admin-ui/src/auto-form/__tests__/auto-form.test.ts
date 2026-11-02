import { afterEach, describe, expect, it } from 'vitest'
import { nextTick } from 'vue'
import { z } from 'zod'
import { cleanup, render, screen } from '@testing-library/vue'
import { fireEvent } from '@testing-library/dom'
import AutoForm from '../AutoForm.vue'
import type { AutoFormField, AutoFormModel } from '../types'

afterEach(() => {
  cleanup()
})

const schema = z.object({
  username: z.string().min(1, '用户名必填'),
  tags: z.array(z.string()).min(1, '至少选一个标签'),
})

const fields: AutoFormField[] = [
  { name: 'username', label: '用户名', type: 'input', placeholder: '请输入用户名' },
  {
    name: 'role',
    label: '角色',
    type: 'select',
    options: [
      { label: '管理员', value: 'admin' },
      { label: '用户', value: 'user' },
    ],
  },
  { name: 'birthday', label: '生日', type: 'date' },
  {
    name: 'gender',
    label: '性别',
    type: 'radio',
    options: [
      { label: '男', value: 'male' },
      { label: '女', value: 'female' },
    ],
  },
  {
    name: 'tags',
    label: '标签',
    type: 'checkbox',
    options: [
      { label: 'Java', value: 'java' },
      { label: '前端', value: 'fe' },
    ],
  },
  {
    name: 'dept',
    label: '部门',
    type: 'tree-select',
    props: { data: [{ value: 1, label: '总部', children: [{ value: 11, label: '研发部' }] }] },
  },
  { name: 'avatar', label: '头像', type: 'upload' },
]

const emptyModel: AutoFormModel = {
  username: '',
  role: null,
  birthday: null,
  gender: '',
  tags: [],
  dept: null,
  avatar: '',
}

describe('AutoForm 字段渲染', () => {
  it('七类字段（含 upload 插槽预留）全部渲染', () => {
    render(AutoForm, { props: { schema, fields, modelValue: emptyModel } })

    // input
    expect(screen.getByPlaceholderText('请输入用户名')).toBeTruthy()
    // select
    expect(document.querySelector('[data-field="role"] .el-select')).toBeTruthy()
    // date
    expect(document.querySelector('[data-field="birthday"] .el-date-editor')).toBeTruthy()
    // radio
    expect(screen.getByText('男')).toBeTruthy()
    expect(screen.getByText('女')).toBeTruthy()
    // checkbox
    expect(screen.getByText('Java')).toBeTruthy()
    expect(screen.getByText('前端')).toBeTruthy()
    // tree-select
    expect(document.querySelector('[data-field="dept"] .el-select')).toBeTruthy()
    // upload 插槽预留提示
    expect(screen.getByText(/上传控件经 field-avatar 插槽接入/)).toBeTruthy()
  })

  it('必填字段带标记：schema 驱动语义', () => {
    render(AutoForm, { props: { schema, fields, modelValue: emptyModel } })

    const usernameLabel = document.querySelector('[data-field="username"] .auto-form__label')
    expect(usernameLabel?.textContent).toContain('*')
    const roleLabel = document.querySelector('[data-field="role"] .auto-form__label')
    expect(roleLabel?.textContent).not.toContain('*')
  })
})

describe('AutoForm 提交校验', () => {
  it('提交时整表 parse：issue 映射字段错误并聚焦首个错误字段', async () => {
    const { emitted } = render(AutoForm, { props: { schema, fields, modelValue: emptyModel } })

    await fireEvent.click(screen.getByRole('button', { name: '提交' }))

    const errorNodes = document.querySelectorAll('[data-test="auto-form-field-error"]')
    expect(errorNodes).toHaveLength(2)
    expect(errorNodes[0]?.textContent).toContain('用户名必填')
    expect(errorNodes[1]?.textContent).toContain('至少选一个标签')
    expect(document.querySelector('[data-test="auto-form-form-error"]')).toBeNull()
    expect(emitted().submit).toBeUndefined()

    // 聚焦首个错误字段（用户名）——组件内 await nextTick 后才聚焦，这里同步等一拍
    await nextTick()
    const focused = document.activeElement as HTMLElement | null
    expect(focused?.closest('[data-field]')?.getAttribute('data-field')).toBe('username')
  })

  it('校验通过后 submit 上抛 zod parse 产物', async () => {
    const { emitted } = render(AutoForm, {
      props: {
        schema,
        fields,
        modelValue: { ...emptyModel, username: '张三', tags: ['java'] },
      },
    })

    await fireEvent.click(screen.getByRole('button', { name: '提交' }))

    expect(emitted().submit).toEqual([[{ username: '张三', tags: ['java'] }]])
    expect(document.querySelector('[data-test="auto-form-field-error"]')).toBeNull()
  })

  it('未映射 issue 归表单级提示', async () => {
    const refinedSchema = schema.refine(() => false, '两次密码不一致')
    render(AutoForm, {
      props: {
        schema: refinedSchema,
        fields,
        modelValue: { ...emptyModel, username: '张三', tags: ['java'] },
      },
    })

    await fireEvent.click(screen.getByRole('button', { name: '提交' }))

    expect(document.querySelector('[data-test="auto-form-form-error"]')?.textContent).toContain(
      '两次密码不一致',
    )
  })

  it('字段修改即清掉该字段错误', async () => {
    render(AutoForm, { props: { schema, fields, modelValue: emptyModel } })

    await fireEvent.click(screen.getByRole('button', { name: '提交' }))
    expect(document.querySelectorAll('[data-test="auto-form-field-error"]')).toHaveLength(2)

    const input = document.querySelector('[data-field="username"] input') as HTMLInputElement
    input.value = '张三'
    await fireEvent.input(input)

    const errors = document.querySelectorAll('[data-test="auto-form-field-error"]')
    expect(errors).toHaveLength(1)
    expect(errors[0]?.textContent).not.toContain('用户名必填')
  })

  it('提交中按钮禁用并带加载态', () => {
    render(AutoForm, { props: { schema, fields, modelValue: emptyModel, submitting: true } })

    const button = screen.getByRole('button', { name: /提交/ })
    expect(button.hasAttribute('disabled')).toBe(true)
    expect(button.classList.contains('is-loading')).toBe(true)
  })
})

describe('AutoForm 插槽接管', () => {
  it('field-<name> 具名插槽可手写任意字段', () => {
    render(AutoForm, {
      props: { schema, fields, modelValue: emptyModel },
      slots: {
        'field-username': '<div data-test="custom-username">自定义用户名控件</div>',
      },
    })

    expect(document.querySelector('[data-test="custom-username"]')).toBeTruthy()
    expect(screen.queryByPlaceholderText('请输入用户名')).toBeNull()
  })
})
