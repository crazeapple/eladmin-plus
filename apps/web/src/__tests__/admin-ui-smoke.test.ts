import { afterEach, describe, expect, it, vi } from 'vitest'
import { nextTick } from 'vue'
import { z } from 'zod'
import { cleanup, render, screen } from '@testing-library/vue'
import { fireEvent } from '@testing-library/dom'
import { AutoForm, DataTable, setDictSource, useDict } from '@eladmin-plus/admin-ui'

afterEach(() => {
  cleanup()
})

// admin-ui 组件层消费冒烟：包入口在 web 应用内可用、三件套可运行
// （组件行为的完整验收在 admin-ui 包内测试，真实页面随 P-012+ 落地）
describe('admin-ui 消费冒烟', () => {
  it('DataTable 以声明式渲染数据', () => {
    render(DataTable, {
      props: {
        columns: [{ accessorKey: 'name', header: '姓名' }],
        data: [{ id: 1, name: '冒烟' }],
      },
    })

    expect(screen.getByRole('columnheader', { name: '姓名' })).toBeTruthy()
    expect(screen.getByRole('cell', { name: '冒烟' })).toBeTruthy()
  })

  it('AutoForm 提交校验链可用', async () => {
    const schema = z.object({ name: z.string().min(1, '名称必填') })
    render(AutoForm, {
      props: {
        schema,
        fields: [{ name: 'name', label: '名称', type: 'input' }],
        modelValue: { name: '' },
      },
    })

    await fireEvent.click(screen.getByRole('button', { name: '提交' }))
    await nextTick()
    expect(screen.getByText('名称必填')).toBeTruthy()
  })

  it('useDict 契约与换源口子可用', async () => {
    expect(typeof setDictSource).toBe('function')

    const { options, loading } = useDict('sys_user_sex')
    await vi.waitFor(() => expect(loading.value).toBe(false))
    expect(options.value).toHaveLength(3)
  })
})
