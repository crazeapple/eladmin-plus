import { VueQueryPlugin } from '@tanstack/vue-query'
import { cleanup, fireEvent, render, waitFor } from '@testing-library/vue'
import { createPinia, setActivePinia } from 'pinia'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { ref } from 'vue'

import { buildI18n } from '@/i18n'
import { useSessionStore } from '@/stores/session'
import Account from '../index.vue'

const {
  updateMutate,
  clearMutate,
  uploadMutate,
  changeMutate,
  blobUrl,
  confirmMock,
  routerPush,
  logoutBroadcast,
} = vi.hoisted(() => ({
  updateMutate: vi.fn().mockResolvedValue(undefined),
  clearMutate: vi.fn().mockResolvedValue(undefined),
  uploadMutate: vi.fn().mockResolvedValue(9),
  changeMutate: vi.fn().mockResolvedValue(undefined),
  blobUrl: vi.fn().mockResolvedValue('blob:mock-avatar'),
  confirmMock: vi.fn().mockResolvedValue(true),
  routerPush: vi.fn().mockResolvedValue(undefined),
  logoutBroadcast: vi.fn(),
}))

/** me 用户视图（各用例自行赋值；null 即未加载态） */
let meUser: Record<string, unknown> | null = null

vi.mock('@/api/profile', () => ({
  useMe: () => ({ data: ref({ user: meUser }) }),
  useUpdateProfile: () => ({ mutateAsync: updateMutate }),
  useUploadAvatar: () => ({ mutateAsync: uploadMutate }),
  useClearAvatar: () => ({ mutateAsync: clearMutate }),
  useChangePassword: () => ({ mutateAsync: changeMutate }),
}))

vi.mock('@/api/storage', () => ({
  storageFileBlobUrl: blobUrl,
}))

vi.mock('@/api/auth-sync', () => ({
  broadcastLogout: logoutBroadcast,
  broadcastRefreshed: vi.fn(),
  onAuthBroadcast: vi.fn(() => () => {}),
  withRefreshLock: vi.fn((fn: () => Promise<unknown>) => fn()),
}))

// 个人中心页对 vue-router 只消费 push（跳登录页）
vi.mock('vue-router', () => ({
  useRouter: () => ({ push: routerPush }),
}))

// vue-cropper 桩：暴露 getCropBlob（回一个 png blob），裁剪交互本体不在组件测试面
vi.mock('vue-cropper/dist/vue-cropper.es.js', () => ({
  VueCropper: {
    name: 'VueCropper',
    setup(_props: unknown, { expose }: { expose: (o: unknown) => void }) {
      expose({
        getCropBlob: (cb: (blob: Blob) => void) => cb(new Blob(['x'], { type: 'image/png' })),
      })
      return () => null
    },
  },
}))

vi.mock('element-plus', async (importOriginal) => {
  const actual = await importOriginal<typeof import('element-plus')>()
  return { ...actual, ElMessageBox: { ...actual.ElMessageBox, confirm: confirmMock } }
})

function byTest(test: string): HTMLElement {
  const element = document.querySelector(`[data-test="${test}"]`)
  if (!element) {
    throw new Error(`element not found: [data-test="${test}"]`)
  }
  return element as HTMLElement
}

beforeEach(() => {
  vi.spyOn(window.navigator, 'language', 'get').mockReturnValue('zh-CN')
  meUser = null
})

afterEach(() => {
  cleanup()
  localStorage.clear()
  vi.restoreAllMocks()
  vi.clearAllMocks()
})

function renderPage() {
  const pinia = createPinia()
  setActivePinia(pinia)
  render(Account, {
    global: { plugins: [pinia, buildI18n('zh-CN'), VueQueryPlugin] },
  })
  return pinia
}

/** 改密页签懒加载：点开第二个页签后三字段渲染 */
async function openPasswordTab() {
  await waitFor(() => expect(document.querySelectorAll('.el-tabs__item').length).toBe(2))
  await fireEvent.click(document.querySelectorAll('.el-tabs__item')[1]!)
  await waitFor(() => expect(byTest('account-password-old')).toBeTruthy())
}

describe('个人中心资料与头像 tab', () => {
  it('骨架：双页签与资料表单四字段在册，me 未加载时默认头像占位', async () => {
    renderPage()
    expect(byTest('account-page')).toBeTruthy()
    expect(document.querySelectorAll('.el-tabs__item').length).toBe(2)
    expect(byTest('account-profile-nickname')).toBeTruthy()
    expect(byTest('account-profile-email')).toBeTruthy()
    expect(byTest('account-profile-phone')).toBeTruthy()
    expect(byTest('account-profile-gender')).toBeTruthy()
    expect(byTest('account-avatar-default')).toBeTruthy()
    // 无头像即无清空入口
    expect(document.querySelectorAll('[data-test="account-avatar-clear"]').length).toBe(0)
  })

  it('me 到位回填：四字段进表单、头像走对象 URL、清空按钮出现', async () => {
    meUser = {
      id: 1,
      username: 'admin',
      nickName: '系统管理员',
      email: 'admin@eladmin.local',
      phone: '13800000000',
      gender: 1,
      avatarId: 5,
    }
    renderPage()
    await waitFor(() => {
      expect((byTest('account-profile-nickname') as HTMLInputElement).value).toBe('系统管理员')
      expect((byTest('account-profile-email') as HTMLInputElement).value).toBe(
        'admin@eladmin.local',
      )
      expect((byTest('account-profile-phone') as HTMLInputElement).value).toBe('13800000000')
    })
    expect(blobUrl).toHaveBeenCalledWith(5)
    await waitFor(() => expect(byTest('account-avatar').querySelector('img')).toBeTruthy())
    expect(byTest('account-avatar-clear')).toBeTruthy()
  })

  it('资料保存：全量四字段提交并提示成功', async () => {
    meUser = { id: 1, username: 'admin', nickName: '系统管理员', gender: 0 }
    renderPage()
    await waitFor(() => {
      expect((byTest('account-profile-nickname') as HTMLInputElement).value).toBe('系统管理员')
    })
    await fireEvent.update(byTest('account-profile-nickname'), '系统管理员改')
    await fireEvent.update(byTest('account-profile-email'), 'me@eladmin.local')
    await fireEvent.click(byTest('account-profile-save'))
    await waitFor(() => expect(updateMutate).toHaveBeenCalledTimes(1))
    expect(updateMutate.mock.calls[0]?.[0]).toEqual({
      nickName: '系统管理员改',
      email: 'me@eladmin.local',
      phone: '',
      gender: 0,
    })
    await waitFor(() => expect(document.body.textContent).toContain('资料已保存'))
  })

  it('资料保存失败：后端错误消息页内展示（邮箱非法 422 通路）', async () => {
    updateMutate.mockRejectedValueOnce(new Error('邮箱格式不合法'))
    meUser = { id: 1, username: 'admin', nickName: '系统管理员', gender: 0 }
    renderPage()
    await waitFor(() => {
      expect((byTest('account-profile-nickname') as HTMLInputElement).value).toBe('系统管理员')
    })
    await fireEvent.update(byTest('account-profile-email'), 'bad-email')
    await fireEvent.click(byTest('account-profile-save'))
    await waitFor(() => expect(updateMutate).toHaveBeenCalledTimes(1))
    await waitFor(() => expect(document.body.textContent).toContain('邮箱格式不合法'))
  })

  it('头像清空：确认后调端点提示成功；取消不调', async () => {
    meUser = { id: 1, username: 'admin', nickName: '系统管理员', avatarId: 5 }
    renderPage()
    await waitFor(() => expect(byTest('account-avatar-clear')).toBeTruthy())

    // 取消不调
    confirmMock.mockRejectedValueOnce('cancel')
    await fireEvent.click(byTest('account-avatar-clear'))
    await waitFor(() => expect(confirmMock).toHaveBeenCalledTimes(1))
    expect(clearMutate).not.toHaveBeenCalled()

    // 确认即清空
    await fireEvent.click(byTest('account-avatar-clear'))
    await waitFor(() => expect(confirmMock).toHaveBeenCalledTimes(2))
    await waitFor(() => expect(clearMutate).toHaveBeenCalledTimes(1))
    await waitFor(() => expect(document.body.textContent).toContain('头像已清空'))
  })

  it('裁剪弹窗：选图后裁剪框出现，确认走头像端点；取消不产生上传', async () => {
    meUser = { id: 1, username: 'admin', nickName: '系统管理员' }
    renderPage()
    await fireEvent.click(byTest('account-avatar-change'))
    const dialog = await waitFor(() => {
      const element = document.querySelector('[data-test="account-crop-dialog"]')
      expect(element).toBeTruthy()
      return element as HTMLElement
    })
    // 未选图：确认按钮禁用，不产生上传
    expect(byTest('account-crop-confirm').hasAttribute('disabled')).toBe(true)

    // 选图（data URL 进 cropper 图源）
    const file = new File(['image-bytes'], 'avatar.png', { type: 'image/png' })
    const input = dialog.querySelector<HTMLInputElement>(
      '[data-test="account-avatar-pick"] input[type="file"]',
    )
    await fireEvent.change(input!, { target: { files: [file] } })
    await waitFor(() => {
      expect(document.querySelector('.avatar-cropper__box')).toBeTruthy()
    })

    // 取消：不调端点（happy-dom 过渡不卸载弹层，取消语义以未产生上传为准）
    await fireEvent.click(byTest('account-crop-cancel'))
    await new Promise((resolve) => setTimeout(resolve, 50))
    expect(uploadMutate).not.toHaveBeenCalled()

    // 重开再确认：裁剪结果 blob 直传头像端点
    await fireEvent.click(byTest('account-avatar-change'))
    await waitFor(() =>
      expect(document.querySelector('[data-test="account-crop-dialog"]')).toBeTruthy(),
    )
    const input2 = document.querySelector<HTMLInputElement>(
      '[data-test="account-avatar-pick"] input[type="file"]',
    )
    await fireEvent.change(input2!, { target: { files: [file] } })
    await waitFor(() => {
      expect(document.querySelector('.avatar-cropper__box')).toBeTruthy()
    })
    await fireEvent.click(byTest('account-crop-confirm'))
    await waitFor(() => expect(uploadMutate).toHaveBeenCalledTimes(1))
    const uploaded = uploadMutate.mock.calls[0]?.[0] as File
    expect(uploaded.type).toBe('image/png')
    await waitFor(() => expect(document.body.textContent).toContain('头像已更新'))
  })

  it('改密页签：激活后三字段在册', async () => {
    meUser = { id: 1, username: 'admin', nickName: '系统管理员' }
    renderPage()
    await openPasswordTab()
    expect(byTest('account-password-new')).toBeTruthy()
    expect(byTest('account-password-confirm')).toBeTruthy()
    expect(byTest('account-password-save')).toBeTruthy()
  })

  it('改密成功：调端点后按登出语义清态跳登录', async () => {
    meUser = { id: 1, username: 'admin', nickName: '系统管理员' }
    const pinia = renderPage()
    const session = useSessionStore(pinia)
    session.save('token-in-memory')

    await openPasswordTab()
    await fireEvent.update(byTest('account-password-old'), 'old-123')
    await fireEvent.update(byTest('account-password-new'), 'new-456')
    await fireEvent.update(byTest('account-password-confirm'), 'new-456')
    await fireEvent.click(byTest('account-password-save'))

    await waitFor(() =>
      expect(changeMutate).toHaveBeenCalledWith({ oldPassword: 'old-123', newPassword: 'new-456' }),
    )
    // 会话态与登录意图全清，广播其他 tab，跳登录页
    await waitFor(() => expect(session.accessToken).toBeNull())
    expect(localStorage.getItem('auth:logged-in')).toBeNull()
    expect(logoutBroadcast).toHaveBeenCalled()
    await waitFor(() => expect(routerPush).toHaveBeenCalledWith('/login'))
  })

  it('改密失败：旧密码错误页内展示且不清态', async () => {
    changeMutate.mockRejectedValueOnce(new Error('旧密码不正确'))
    meUser = { id: 1, username: 'admin', nickName: '系统管理员' }
    const pinia = renderPage()
    const session = useSessionStore(pinia)
    session.save('token-in-memory')

    await openPasswordTab()
    await fireEvent.update(byTest('account-password-old'), 'wrong-old')
    await fireEvent.update(byTest('account-password-new'), 'new-456')
    await fireEvent.update(byTest('account-password-confirm'), 'new-456')
    await fireEvent.click(byTest('account-password-save'))

    await waitFor(() => expect(changeMutate).toHaveBeenCalledTimes(1))
    await waitFor(() => expect(document.body.textContent).toContain('旧密码不正确'))
    // 失败不清态不跳转
    expect(session.accessToken).toBe('token-in-memory')
    expect(routerPush).not.toHaveBeenCalled()
  })
})
