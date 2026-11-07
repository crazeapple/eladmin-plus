import { execFileSync } from 'node:child_process'
import { constants as cryptoConstants, publicEncrypt } from 'node:crypto'
import type { APIRequestContext, Page } from '@playwright/test'

/** 栈内 Redis 容器名（deploy/compose.yaml 固定命名） */
const REDIS_CONTAINER = 'eladmin-plus-prod-redis'

export interface CaptchaChallenge {
  token: string
  imageWidth: number
  answer: number
}

/**
 * 从栈内 Redis 读滑块答案（原图像素域）。
 * 真实验证码语义不旁路：答案仍由后端核验，E2E 只是把「人眼看缺口」换成读出题时存的答案。
 */
function readPuzzleAnswer(token: string): number {
  const raw = execFileSync(
    'docker',
    ['exec', REDIS_CONTAINER, 'redis-cli', '--raw', 'GET', `auth:captcha:puzzle:${token}`],
    { encoding: 'utf8', stdio: ['ignore', 'pipe', 'ignore'] },
  ).trim()
  const answer = Number(raw)
  if (!Number.isFinite(answer)) throw new Error(`滑块答案读取失败：token=${token}`)
  return answer
}

/** 注册出题响应监听并在响应到达后读答案（须在触发出题的操作前调用） */
export async function nextCaptchaChallenge(page: Page): Promise<CaptchaChallenge> {
  const response = await page.waitForResponse(
    (res) => res.url().includes('/auth/captcha') && res.request().method() === 'GET',
  )
  const puzzle = (await response.json()) as {
    data: { token: string; imageWidth: number }
  }
  return {
    token: puzzle.data.token,
    imageWidth: puzzle.data.imageWidth,
    answer: readPuzzleAnswer(puzzle.data.token),
  }
}

/**
 * 按答案拖动滑块：slideX 由指针相对轨道左缘的绝对位置决定（非起点位移），
 * 终点 clientX = 轨道左缘 + (答案 / 原图宽) × 轨道渲染宽——与组件「轨道逻辑域 →
 * 原图像素域」的提交换算互逆，任意容器宽度成立。
 */
export async function solveSlider(page: Page, challenge: CaptchaChallenge): Promise<void> {
  const track = page.locator('[data-test="slider-track"]')
  const handle = page.locator('[data-test="slider-handle"]')
  const trackBox = await track.boundingBox()
  const handleBox = await handle.boundingBox()
  if (!trackBox || !handleBox) throw new Error('滑块轨道未渲染')
  const startY = handleBox.y + handleBox.height / 2
  const targetClientX = trackBox.x + (challenge.answer / challenge.imageWidth) * trackBox.width
  await page.mouse.move(handleBox.x + handleBox.width / 2, startY)
  await page.mouse.down()
  await page.mouse.move(targetClientX, startY, { steps: 10 })
  await page.mouse.up()
}

/** 完整登录：填表（不触发验证码）→ 点「登录」弹层出题 → 按答案拖动（过验即登录）；返回是否进入了系统（密码错返回 false） */
export async function loginAs(page: Page, username: string, password: string): Promise<boolean> {
  await page.goto('/login')
  await page.locator('[data-test="login-username"]').fill(username)
  await page.locator('[data-test="login-password"]').fill(password)
  const challengePromise = nextCaptchaChallenge(page)
  await page.locator('[data-test="login-submit"]').click()
  const challenge = await challengePromise
  await solveSlider(page, challenge)
  try {
    await page.waitForURL((url) => !url.pathname.startsWith('/login'), { timeout: 8000 })
    return true
  } catch {
    return false
  }
}

/** 管理员完整登录：填表 → 点「登录」弹层出题 → 按答案拖动过验即登录 → 等进系统 */
export async function loginAsAdmin(page: Page): Promise<void> {
  const succeeded = await loginAs(page, 'admin', '123456')
  if (!succeeded) {
    throw new Error('管理员登录失败')
  }
}

/**
 * 已持会话（storageState 的 refresh cookie）进入系统内页：
 * 守卫先静默恢复会话再放行，落地页即目标路径。
 */
export async function gotoWithSession(page: Page, path: string): Promise<void> {
  await page.goto(path)
  await page.waitForURL((url) => url.pathname === path)
}

/** 栈内 MySQL 容器名（deploy/compose.yaml 固定命名） */
const MYSQL_CONTAINER = 'eladmin-plus-prod-mysql'

/** API 全链登录态：access token（accessToken 只住应用内存，E2E 直调 API 须自取） */
export interface ApiSession {
  accessToken: string
}

/** bearer 请求头（API 直调端点的 Authorization 组装） */
export function bearer(session: ApiSession): Record<string, string> {
  return { Authorization: `Bearer ${session.accessToken}` }
}

/** API 登录尝试的原始响应：状态与统一 R 结构（锁定 / 限流 / 密码错语义由用例自决断言） */
export interface LoginAttemptResult {
  status: number
  body: { code: number; message: string; data: { accessToken?: string } | null }
}

/**
 * API 全链登录尝试（不经浏览器 UI）：出题 → Redis 读答案 → 校验签票 → 公钥 → RSA-OAEP 加密 → 登录。
 * 加密参数与后端解密同参（OAEP SHA-256，MGF1 同 SHA-256）；返回登录端点原始响应。
 */
export async function apiLoginAttempt(
  request: APIRequestContext,
  username: string,
  password: string,
): Promise<LoginAttemptResult> {
  const captcha = (await (await request.get('/api/auth/captcha')).json()) as {
    data: { token: string }
  }
  const answer = readPuzzleAnswer(captcha.data.token)
  const check = (await (
    await request.post('/api/auth/captcha/check', {
      data: { token: captcha.data.token, moveDistance: answer },
    })
  ).json()) as { data: { ticket: string } }
  const key = (await (await request.get('/api/auth/public-key')).json()) as {
    data: { keyId: string; publicKey: string }
  }
  const cipher = publicEncrypt(
    {
      key: key.data.publicKey,
      padding: cryptoConstants.RSA_PKCS1_OAEP_PADDING,
      oaepHash: 'sha256',
    },
    Buffer.from(password, 'utf8'),
  ).toString('base64')
  const login = await request.post('/api/auth/login', {
    data: {
      username,
      password: cipher,
      keyId: key.data.keyId,
      ticket: check.data.ticket,
    },
  })
  const body = (await login.json()) as LoginAttemptResult['body']
  return { status: login.status(), body }
}

/** API 全链登录：成功返回会话态，失败（密码错 / 锁定 / 限流）抛错 */
export async function apiLogin(
  request: APIRequestContext,
  username: string,
  password: string,
): Promise<ApiSession> {
  const result = await apiLoginAttempt(request, username, password)
  if (result.status !== 200 || !result.body.data?.accessToken) {
    throw new Error(`API 登录失败：${username} 状态 ${result.status}`)
  }
  return { accessToken: result.body.data.accessToken }
}

/**
 * 管理员账户偏好归零（preferences 置 NULL = 缺省）。
 * 偏好同步用例的自净前提：不管此前运行留下什么账户态，用例起点恒为缺省。
 */
export function resetAdminPreferences(): void {
  execFileSync(
    'docker',
    [
      'exec',
      MYSQL_CONTAINER,
      'mysql',
      '-uroot',
      '-proot',
      '-e',
      "UPDATE eladmin_plus.`user` SET preferences = NULL WHERE username = 'admin'",
    ],
    { stdio: 'ignore' },
  )
}
