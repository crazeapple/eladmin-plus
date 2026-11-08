import { client } from './client'

/** 登录公钥：keyId 与公钥同发，密文回传携带 keyId 供后端定位解密密钥 */
export interface PublicKeyPayload {
  keyId: string
  publicKey: string
}

/** 滑块题目：答案只在后端，拼块纵向位置、画布尺寸与双图下发（尺寸供拖动距离与定位按比例换算） */
export interface CaptchaPuzzle {
  token: string
  back: string
  jigsaw: string
  jigsawY: number
  imageWidth: number
  imageHeight: number
}

export interface LoginResult {
  accessToken: string
  expiresIn: number
}

/** 认证端点：全部走统一 client（envelope 解包与 ApiError 已收口） */
export async function fetchPublicKey(): Promise<PublicKeyPayload> {
  const { data } = await client.request({
    url: '/auth/public-key',
    method: 'GET',
    throwOnError: true,
  })
  return data as PublicKeyPayload
}

export async function fetchCaptcha(): Promise<CaptchaPuzzle> {
  const { data } = await client.request({ url: '/auth/captcha', method: 'GET', throwOnError: true })
  return data as CaptchaPuzzle
}

export async function checkCaptcha(
  token: string,
  moveDistance: number,
): Promise<{ ticket: string }> {
  const { data } = await client.request({
    url: '/auth/captcha/check',
    method: 'POST',
    body: { token, moveDistance },
    throwOnError: true,
  })
  return data as { ticket: string }
}

export async function login(payload: {
  username: string
  password: string
  keyId: string
  ticket: string
}): Promise<LoginResult> {
  const { data } = await client.request({
    url: '/auth/login',
    method: 'POST',
    body: payload,
    throwOnError: true,
  })
  return data as LoginResult
}

/** 刷新：cookie 同源自动携带，自定义头必备（CSRF 纵深，与登录后请求同款注入） */
export async function refresh(): Promise<LoginResult> {
  const { data } = await client.request({
    url: '/auth/refresh',
    method: 'POST',
    headers: { 'X-Requested-With': 'XMLHttpRequest' },
    throwOnError: true,
  })
  return data as LoginResult
}

export async function logout(accessToken: string): Promise<void> {
  await client.request({
    url: '/auth/logout',
    method: 'POST',
    headers: { Authorization: `Bearer ${accessToken}` },
  })
}
