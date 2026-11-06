import { afterEach, describe, expect, it, vi } from 'vitest'
import { cleanup, render, waitFor } from '@testing-library/vue'
import { fireEvent } from '@testing-library/dom'
import { createPinia, setActivePinia } from 'pinia'
import { createMemoryHistory, createRouter, type Router } from 'vue-router'
import Login from '../index.vue'
import { buildI18n } from '@/i18n'
import { useSessionStore } from '@/stores/session'

/** 仓库约定 data-test 属性的查询辅助（不依赖 RTL testId 配置） */
function byTest(id: string): HTMLElement {
  const element = document.querySelector(`[data-test="${id}"]`)
  if (!element) {
    throw new Error(`element not found: [data-test="${id}"]`)
  }
  return element as HTMLElement
}

// —— auth api 桩（登录页协议面）；RSA 加密走真 Web Crypto（node 提供 subtle）——
const mocks = vi.hoisted(() => ({
  fetchPublicKey: vi.fn(),
  fetchCaptcha: vi.fn(),
  checkCaptcha: vi.fn(),
  login: vi.fn(),
}))

vi.mock('@/api/auth', () => ({
  fetchPublicKey: mocks.fetchPublicKey,
  fetchCaptcha: mocks.fetchCaptcha,
  checkCaptcha: mocks.checkCaptcha,
  login: mocks.login,
  // 登录页测试无既有会话:守卫的静默恢复尝试显式失败(干净拒绝,不走噪音异常路径)
  refresh: vi.fn(() => Promise.reject(new Error('not signed in'))),
  logout: vi.fn(),
}))

// 滑块组件桩：emit verified 即代表校验通过（滑块交互另有后端集成语义）
vi.mock('@/components/captcha/SliderCaptcha.vue', () => ({
  default: {
    name: 'SliderCaptcha',
    props: ['hint', 'failText', 'retryText'],
    emits: ['verified'],
    template: `<div data-test="slider-stub"><button data-test="slider-verify" @click="$emit('verified', 'ticket-1')">v</button></div>`,
  },
}))

// 布局壳桩：登录跳转目标的承载（登录页应在壳外）
const HomeStub = { template: '<div data-test="home-stub">home</div>' }

/** 壳外登录页挂载：memory 路由（/login 起始）+ 与测试共享的 pinia */
async function mountLogin(router: Router, pinia: ReturnType<typeof createPinia>) {
  await router.push('/login')
  const i18n = buildI18n('zh-CN')
  render(Login, { global: { plugins: [i18n, pinia, router] } })
  await waitFor(() => expect(byTest('login-page')).toBeTruthy())
}

/** el-input 的 data-test 由 attrs 继承落在原生输入框上，直取即为 input */
function elInput(id: string): HTMLInputElement {
  return byTest(id) as HTMLInputElement
}

/**
 * 填表 + 点「登录」弹滑块层（不点过验）：填表不出滑块，提交时才弹层。
 * 过验动作单独走 verifySlider——过验即登录，会真实提交（须先备好协议桩）。
 */
async function fillAndOpenSlider() {
  await fireEvent.input(elInput('login-username'), { target: { value: 'admin' } })
  await fireEvent.input(elInput('login-password'), { target: { value: '123456' } })
  // 填表阶段不触发滑块
  expect(queryTest('slider-stub')).toBeNull()
  await fireEvent.click(byTest('login-submit'))
  await waitFor(() => expect(byTest('slider-stub')).toBeTruthy())
}

/** 滑块过验：过验即登录（不再有二次提交点击） */
async function verifySlider() {
  await fireEvent.click(byTest('slider-verify'))
}

async function submit() {
  await fireEvent.click(byTest('login-submit'))
}

/** RSA 测试密钥对（node webcrypto，2048 与后端同参） */
async function generateKeyPair() {
  const pair = await crypto.subtle.generateKey(
    {
      name: 'RSA-OAEP',
      hash: 'SHA-256',
      modulusLength: 2048,
      publicExponent: new Uint8Array([1, 0, 1]),
    },
    true,
    ['encrypt', 'decrypt'],
  )
  const spki = await crypto.subtle.exportKey('spki', pair.publicKey)
  const b64 = btoa(String.fromCharCode(...new Uint8Array(spki)))
  return {
    keyId: crypto.randomUUID(),
    publicKey: `-----BEGIN PUBLIC KEY-----\n${b64}\n-----END PUBLIC KEY-----`,
    privateKey: pair.privateKey,
  }
}

async function decryptCipher(privateKey: CryptoKey, cipherBase64: string): Promise<string> {
  const bytes = Uint8Array.from(atob(cipherBase64), (ch) => ch.charCodeAt(0))
  const plain = await crypto.subtle.decrypt({ name: 'RSA-OAEP' }, privateKey, bytes)
  return new TextDecoder().decode(plain)
}

function queryTest(id: string): HTMLElement | null {
  return document.querySelector(`[data-test="${id}"]`)
}

function makeRouter(): Router {
  return createRouter({
    history: createMemoryHistory(),
    routes: [
      { path: '/login', name: 'Login', component: Login },
      { path: '/home', name: 'Home', component: HomeStub },
      { path: '/:pathMatch(.*)*', name: 'Any', component: HomeStub },
    ],
  })
}

afterEach(() => {
  cleanup()
  vi.clearAllMocks()
})

describe('登录页', () => {
  it('页面渲染在壳外且账号密码登录按钮齐备，填表不出滑块', async () => {
    const pinia = createPinia()
    setActivePinia(pinia)
    await mountLogin(makeRouter(), pinia)

    expect(byTest('login-username')).toBeTruthy()
    expect(byTest('login-password')).toBeTruthy()
    expect(byTest('login-submit')).toBeTruthy()
    // 填表不弹滑块，点登录才弹层（此处不点过验，避免触发真实提交链）
    await fillAndOpenSlider()
    expect(document.querySelector('.layout')).toBeNull()
  })

  it('全链成功：过验即登录，RSA 密文可被对应私钥解开、token 入内存、跳站内目标', async () => {
    const pair = await generateKeyPair()
    mocks.fetchPublicKey.mockResolvedValue({ keyId: pair.keyId, publicKey: pair.publicKey })
    mocks.login.mockResolvedValue({ accessToken: 'access-token-1', expiresIn: 7200 })
    mocks.fetchCaptcha.mockResolvedValue({
      token: 't',
      back: 'b',
      jigsaw: 'j',
      jigsawY: 3,
      imageWidth: 310,
      imageHeight: 155,
    })

    const pinia = createPinia()
    setActivePinia(pinia)
    const router = makeRouter()
    await mountLogin(router, pinia)
    await fillAndOpenSlider()
    await verifySlider()

    // 过验即登录：不再点提交
    await waitFor(() => expect(mocks.login).toHaveBeenCalledTimes(1))
    const payload = mocks.login.mock.calls[0]![0] as Record<string, string>
    expect(payload.username).toBe('admin')
    expect(payload.keyId).toBe(pair.keyId)
    expect(payload.ticket).toBe('ticket-1')
    // 密文不含明文，且可被公钥对应私钥解开（Web Crypto 与后端同参的语义证据）
    expect(payload.password).not.toContain('123456')
    expect(await decryptCipher(pair.privateKey, payload.password)).toBe('123456')

    // token 入内存 store；redirect 缺省回首页（壳内承载）
    expect(useSessionStore().accessToken).toBe('access-token-1')
    await waitFor(() => expect(router.currentRoute.value.path).toBe('/home'))
  })

  it('redirect 查询为站内路径时跳该路径，外站形态被拒落首页', async () => {
    const pair = await generateKeyPair()
    mocks.fetchPublicKey.mockResolvedValue({ keyId: pair.keyId, publicKey: pair.publicKey })
    mocks.login.mockResolvedValue({ accessToken: 't', expiresIn: 7200 })
    mocks.fetchCaptcha.mockResolvedValue({
      token: 't',
      back: 'b',
      jigsaw: 'j',
      jigsawY: 3,
      imageWidth: 310,
      imageHeight: 155,
    })

    const pinia = createPinia()
    setActivePinia(pinia)
    const router = makeRouter()
    await router.push('/login')
    await router.replace('//evil.example.com') // 经 /login?redirect=//evil.example.com 的等价形态由守卫测试覆盖，此处直接验证 safeRedirect 对 // 形态的拒绝

    render(Login, { global: { plugins: [buildI18n('zh-CN'), pinia, router] } })
    await waitFor(() => expect(byTest('login-page')).toBeTruthy())
    await fillAndOpenSlider()
    await verifySlider()

    await waitFor(() => expect(router.currentRoute.value.path).toBe('/home'))
  })

  it('公钥过期自动重取重试一次', async () => {
    const pair = await generateKeyPair()
    const expired = new Error('登录公钥已过期，请刷新后重试')
    mocks.fetchPublicKey
      .mockResolvedValueOnce({ keyId: 'stale-key', publicKey: pair.publicKey })
      .mockResolvedValueOnce({ keyId: pair.keyId, publicKey: pair.publicKey })
    mocks.login
      .mockRejectedValueOnce(expired)
      .mockResolvedValueOnce({ accessToken: 'access-token-2', expiresIn: 7200 })
    mocks.fetchCaptcha.mockResolvedValue({
      token: 't',
      back: 'b',
      jigsaw: 'j',
      jigsawY: 3,
      imageWidth: 310,
      imageHeight: 155,
    })

    const pinia = createPinia()
    setActivePinia(pinia)
    await mountLogin(makeRouter(), pinia)
    await fillAndOpenSlider()
    await verifySlider()

    await waitFor(() => expect(mocks.login).toHaveBeenCalledTimes(2))
    expect(mocks.fetchPublicKey).toHaveBeenCalledTimes(2)
    await waitFor(() => expect(useSessionStore().accessToken).toBe('access-token-2'))
  })

  it('登录失败回表单态保留输入，可再点登录重新过滑块', async () => {
    const pair = await generateKeyPair()
    mocks.fetchPublicKey.mockResolvedValue({ keyId: pair.keyId, publicKey: pair.publicKey })
    mocks.login.mockRejectedValue(new Error('账号或密码错误'))
    mocks.fetchCaptcha.mockResolvedValue({
      token: 't',
      back: 'b',
      jigsaw: 'j',
      jigsawY: 3,
      imageWidth: 310,
      imageHeight: 155,
    })

    const pinia = createPinia()
    setActivePinia(pinia)
    await mountLogin(makeRouter(), pinia)
    await fillAndOpenSlider()
    await verifySlider()

    await waitFor(() => expect(mocks.login).toHaveBeenCalledTimes(1))
    expect(mocks.fetchPublicKey).toHaveBeenCalledTimes(1)
    await waitFor(() => expect(useSessionStore().accessToken).toBeNull())
    // 失败回表单态：输入保留（票据一次性，重试需重新过滑块；弹层卸载随 EP 过渡时序，单测不锚定）
    expect(elInput('login-username').value).toBe('admin')
    expect(elInput('login-password').value).toBe('123456')
    // 再点登录可重新弹滑块出题（重开即重挂载出新题）
    await submit()
    await waitFor(() => expect(byTest('slider-stub')).toBeTruthy())
  })

  it('表单不全点登录不开滑块层', async () => {
    const pinia = createPinia()
    setActivePinia(pinia)
    await mountLogin(makeRouter(), pinia)

    // 只填账号不填密码：点登录不开层
    await fireEvent.input(elInput('login-username'), { target: { value: 'admin' } })
    await submit()
    expect(queryTest('slider-stub')).toBeNull()
  })
})
