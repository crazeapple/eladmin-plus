import { afterEach, describe, expect, it } from 'vitest'
import { cleanup, render, screen } from '@testing-library/vue'
import { fireEvent } from '@testing-library/dom'
import DataTable from '../DataTable.vue'
import type { DataTableColumn } from '../types'

interface Row {
  id: number
  name: string
  age: number
}

const columns = [
  { accessorKey: 'name', header: '姓名' },
  { accessorKey: 'age', header: '年龄' },
]

const rows: Row[] = [
  { id: 1, name: '张三', age: 28 },
  { id: 2, name: '李四', age: 33 },
]

afterEach(() => {
  cleanup()
})

describe('DataTable 基础渲染', () => {
  it('按 columns 声明出表头，按 data 渲染行', () => {
    render(DataTable, { props: { columns, data: rows } })

    expect(screen.getByRole('columnheader', { name: '姓名' })).toBeTruthy()
    expect(screen.getByRole('columnheader', { name: '年龄' })).toBeTruthy()
    expect(screen.getByRole('cell', { name: '张三' })).toBeTruthy()
    expect(screen.getByRole('cell', { name: '33' })).toBeTruthy()
  })

  it('空数据呈现空态文案', () => {
    render(DataTable, { props: { columns, data: [] } })

    expect(screen.getByText('暂无数据')).toBeTruthy()
    expect(document.querySelector('[data-test="admin-table-empty"]')).toBeTruthy()
  })

  it('loading 态呈现加载遮罩，收起后遮罩消失', async () => {
    const { rerender } = render(DataTable, { props: { columns, data: rows, loading: true } })

    expect(document.querySelector('[data-test="admin-table-loading"]')).toBeTruthy()

    await rerender({ columns, data: rows, loading: false })
    expect(document.querySelector('[data-test="admin-table-loading"]')).toBeNull()
  })

  it('空态与加载遮罩互斥：加载中不显示空态', () => {
    render(DataTable, { props: { columns, data: [], loading: true } })

    expect(screen.queryByText('暂无数据')).toBeNull()
  })

  it('texts 覆盖默认空态文案', () => {
    render(DataTable, { props: { columns, data: [], texts: { empty: 'No records' } } })

    expect(screen.getByText('No records')).toBeTruthy()
    expect(screen.queryByText('暂无数据')).toBeNull()
  })
})

describe('分页与排序契约', () => {
  it('page 0 起换算：props page=0 时 EP 分页当前页显示 1', () => {
    render(DataTable, { props: { columns, data: rows, page: 0, pageSize: 10, total: 25 } })

    const active = document.querySelector('.data-table__pagination .el-pager li.is-active')
    expect(active?.textContent?.trim()).toBe('1')
  })

  it('点下一页上抛 page-change，page 换算回 0 起', async () => {
    const { emitted } = render(DataTable, {
      props: { columns, data: rows, page: 0, pageSize: 10, total: 25 },
    })

    await fireEvent.click(document.querySelector('.data-table__pagination .btn-next')!)

    expect(emitted()['page-change']).toEqual([[{ page: 1, pageSize: 10 }]])
  })

  it('页大小切换上抛 page-change 且 page 重置为 0', async () => {
    const { emitted } = render(DataTable, {
      props: { columns, data: rows, page: 1, pageSize: 10, total: 250 },
    })

    // 打开页大小下拉并选择 50（EP 下拉项文案随 locale 变化，按值前缀匹配）
    await fireEvent.click(document.querySelector('.data-table__pagination .el-select__wrapper')!)
    await fireEvent.click(
      await screen.findByText((_, element) => {
        const isOption = element?.classList.contains('el-select-dropdown__item') ?? false
        return isOption && (element?.textContent?.startsWith('50') ?? false)
      }),
    )

    expect(emitted()['page-change']).toEqual([[{ page: 0, pageSize: 50 }]])
  })

  it('排序点击循环：asc → desc → 取消（null）', async () => {
    const { emitted, rerender } = render(DataTable, {
      props: { columns, data: rows, sort: null },
    })

    await fireEvent.click(screen.getByRole('button', { name: /姓名/ }))
    expect(emitted()['sort-change']).toEqual([[{ field: 'name', order: 'asc' }]])

    await rerender({ columns, data: rows, sort: { field: 'name', order: 'asc' } })
    await fireEvent.click(screen.getByRole('button', { name: /姓名/ }))
    expect(emitted()['sort-change']).toEqual([
      [{ field: 'name', order: 'asc' }],
      [{ field: 'name', order: 'desc' }],
    ])

    await rerender({ columns, data: rows, sort: { field: 'name', order: 'desc' } })
    await fireEvent.click(screen.getByRole('button', { name: /姓名/ }))
    expect(emitted()['sort-change']).toEqual([
      [{ field: 'name', order: 'asc' }],
      [{ field: 'name', order: 'desc' }],
      [null],
    ])
  })

  it('排序表头带 aria-sort 语义', () => {
    render(DataTable, { props: { columns, data: rows, sort: { field: 'name', order: 'asc' } } })

    const nameTh = screen.getByRole('columnheader', { name: /姓名/ })
    expect(nameTh.getAttribute('aria-sort')).toBe('ascending')
  })

  it('刷新按钮上抛 refresh 事件', async () => {
    const { emitted } = render(DataTable, { props: { columns, data: rows } })

    await fireEvent.click(screen.getByRole('button', { name: '刷新' }))

    expect(emitted().refresh).toHaveLength(1)
  })
})

describe('工具栏与列设置', () => {
  it('toolbar-extra 插槽内容渲染在工具栏', () => {
    render(DataTable, {
      props: { columns, data: rows },
      slots: { 'toolbar-extra': '<div data-test="toolbar-extra-content">新增按钮位</div>' },
    })

    expect(document.querySelector('[data-test="toolbar-extra-content"]')).toBeTruthy()
  })

  it('列设置勾选即时显隐列', async () => {
    render(DataTable, { props: { columns, data: rows } })

    await fireEvent.click(screen.getByRole('button', { name: '列设置' }))
    const ageCheckbox = await screen.findByRole('checkbox', { name: /年龄/ })
    await fireEvent.click(ageCheckbox)

    expect(screen.queryByRole('columnheader', { name: '年龄' })).toBeNull()
    // 再勾回来，列恢复
    await fireEvent.click(ageCheckbox)
    expect(screen.getByRole('columnheader', { name: '年龄' })).toBeTruthy()
  })
})

describe('行选择', () => {
  const selectionColumns: DataTableColumn[] = [{ id: 'selection', type: 'selection' }, ...columns]

  it('选择列渲染勾选框，勾选上抛 selection-change', async () => {
    const { emitted } = render(DataTable, { props: { columns: selectionColumns, data: rows } })

    const checkboxes = document.querySelectorAll('[data-test="admin-table-row-select"] input')
    expect(checkboxes).toHaveLength(2)
    await fireEvent.click(checkboxes[0]!)

    expect(emitted()['selection-change']).toEqual([[[rows[0]!]]])
  })

  it('表头全选勾中所有行，再点全不选', async () => {
    const { emitted } = render(DataTable, { props: { columns: selectionColumns, data: rows } })

    await fireEvent.click(document.querySelector('[data-test="admin-table-select-all"] input')!)
    expect(emitted()['selection-change']).toEqual([[rows]])

    await fireEvent.click(document.querySelector('[data-test="admin-table-select-all"] input')!)
    expect(emitted()['selection-change']).toEqual([[rows], [[]]])
  })
})
