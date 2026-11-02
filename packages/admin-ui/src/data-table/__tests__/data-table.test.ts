import { describe, expect, it, afterEach } from 'vitest'
import { cleanup, render, screen } from '@testing-library/vue'
import DataTable from '../DataTable.vue'

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
