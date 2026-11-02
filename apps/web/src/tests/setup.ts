/** vitest 全局 setup：canvas 在测试环境不可用，vue-echarts 统一 stub（需断言 option 的用例在测试文件内自行 mock） */
import { beforeEach, vi } from 'vitest'

vi.mock('vue-echarts', async () => {
  const { defineComponent, h } = await import('vue')
  return {
    default: defineComponent({
      name: 'VChart',
      props: { option: { type: null, default: undefined } },
      setup() {
        return () => h('div', { class: 'v-chart-stub' })
      },
    }),
  }
})

// —— 平台守卫的测试基建 ——
// 守卫对无会话导航统一回 /login，壳层测试（布局、页签、首页等）需要可恢复的会话：
// 全局 fetch 桩只应答认证两端点（refresh + /auth/me 的菜单树），登录相关测试自行覆盖此桩。
beforeEach(() => {
  localStorage.setItem('auth:logged-in', '1')
  vi.stubGlobal(
    'fetch',
    vi.fn(async (input: RequestInfo | URL) => {
      const url = input instanceof Request ? input.url : String(input)
      const json = (body: unknown) =>
        new Response(JSON.stringify(body), {
          status: 200,
          headers: { 'Content-Type': 'application/json' },
        })
      if (url.includes('/auth/refresh')) {
        return json({
          code: 200,
          message: '',
          data: { accessToken: 'setup-token', expiresIn: 7200 },
        })
      }
      if (url.includes('/auth/me')) {
        return json({
          code: 200,
          message: '',
          data: {
            user: { id: 1, username: 'admin', nickName: '系统管理员' },
            roles: ['ROLE_ROOT'],
            permissions: [],
            menus: [
              {
                id: 1,
                parentId: null,
                type: 1,
                permission: null,
                i18nKey: 'routes.home',
                componentPathKey: 'home/index',
                noCache: false,
                sort: 1,
                children: [],
              },
              {
                id: 2,
                parentId: null,
                type: 1,
                permission: null,
                i18nKey: 'routes.demoCached',
                componentPathKey: 'demo/cached',
                noCache: false,
                sort: 2,
                children: [],
              },
              {
                id: 3,
                parentId: null,
                type: 1,
                permission: null,
                i18nKey: 'routes.demoNoCache',
                componentPathKey: 'demo/no-cache',
                noCache: true,
                sort: 3,
                children: [],
              },
            ],
          },
        })
      }
      throw new Error(`测试不应真发请求：${url}`)
    }),
  )
})
