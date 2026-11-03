# 更新日志

## 0.11.0

system 身份域后端:用户、角色、部门、岗位管理与数据权限;登录链路真人试用修复与多 tab 协调随本版一并交付。

- V3 迁移:user 与 role 扩列（邮箱、电话、性别、状态、部门；角色级别、数据范围、描述、状态），新增部门树、岗位、用户岗位、角色自定义部门、通用翻译覆盖五表与演示种子（迁移幂等）
- 通用翻译覆盖机制:主表默认列承载唯一约束与检索，非默认语言展示按 Accept-Language 覆盖（四元组唯一键，随 CRUD 同事务维护、随实体删除级联清理）
- 部门树管理与岗位管理:全量树与分页查询、CRUD、删除关联校验（有子部门或有关联用户不能删）、上级防环
- 用户管理:分页（部门含子级过滤、名称与邮箱模糊、状态与时间筛选）、全字段 CRUD、批量删除级联清理、批量重置默认密码（凭据变更即强退）、状态启停（停用即删全部会话与 refresh）、角色越权约束（不能分配高于自身级别的角色）与自我保护（不能禁用自己、改自己角色、删自己）、admin 账号删除与禁用保护
- 角色管理:分页 CRUD（角色级别、数据范围三档、描述、多语言名）、菜单授权（全量菜单树只读端点 + 勾选集合保存，保存即生效）、内置角色不可删不可改 code 与 level、越权约束（高于自身级别的角色不可操作）
- 数据权限:@DataScope 注解 + MyBatis-Plus 官方 DataPermissionInterceptor，三档语义（ALL 全部 / DEPT 本级含全部子级 / CUSTOM 自定义勾选集合）、多角色并集、ROOT 直通、无部门用户恒假不可见；用户列表为首个消费者
- 登录链挂 per-user 会话索引（按用户精确删全部会话的基础设施，在线用户强退将复用）
- 契约链:OpenAPI 导出含身份域全部端点，前端类型与 client 生成物更新
- 滑块验证码坐标域修复：出题响应下发画布尺寸，前端拖动距离按画布比例换算回原图像素域、拼块纵向改比例定位、轨道 touch-action 防触屏滚动——任意容器宽度（桌面与移动端）校验成立；此前脚本按答案直提与前端 mock 各自全绿掩盖了域不一致，真人拖动必败
- 滑块视觉升级：背景多配色渐变加噪点与形状干扰，拼块形状随机（圆角方块 / 圆 / 六边形，与缺口同形）；失败提示文案定稿，换题后保留 3 秒不再一闪而过
- 登录失败回初始态：账号密码清空、滑块随非空条件隐藏，重填重新出题重滑（票据一次性语义）；错密码 toast 可见（ElMessage 等 API 式组件样式集中显式导入，原缺样式被挤出视口）
- 登录后菜单拉取携带会话令牌：menus 走统一 request 包装（原裸 client 无 Authorization，登录成功即被守卫 401 踢回）
- refresh cookie 路径适配代理形态：path 收窄 /auth 在 /api 前缀反代下 cookie 不可达，F5 静默恢复失效；改根 path，HttpOnly、SameSite 与自定义头三道纵深保留
- 顶栏用户区：图标加用户名触发器（hover 行为与其余触发器一致），下拉「个人中心 / 退出登录」带分隔线
- 多 tab 实时协调：一处退出其他 tab 立即被踢回登录页（广播全清）；主题、主色与语言偏好跨 tab 实时同步（storage 事件）；新 tab 开登录页自动接续会话进系统（守卫对 /login 先静默恢复）


## 0.10.0

登录、动态路由与权限前端。

- 登录页（布局壳之外，偏好主题与语言天然生效）：居中卡片表单、滑块验证码组件（拉题渲染、拖动对齐、失败自动换题）、密码 RSA-OAEP 加密（Web Crypto 原生，不引 jsencrypt）、公钥过期自动重取重试一次、redirect 站内校验（拒绝外站形态）
- 会话令牌只住内存（不落 localStorage），持久化由 refresh cookie 承担；认证业务请求统一包装：Authorization 与自定义头注入、401 单飞刷新并重放原请求一次（防循环）、刷新失败清空会话与登录意图
- 多 tab 会话协调：Web Locks 串行化刷新 + BroadcastChannel 广播共享结果（刷新结果同步内存令牌、登出全清），环境缺失自动降级为直刷
- 动态路由：后端菜单是唯一真相——视图模块构建期 glob 映射（组件路径 key 兼容文件式与目录式）、守卫链「无会话回登录（带 redirect）→ F5 静默 refresh 恢复 → 拉菜单注册路由 → 未注册路径 404 兜底」；路由 name 即组件 defineOptions name（keep-alive 前提），配错编译期暴露
- 顶栏用户区：头像下拉（用户名 / 个人中心占位 / 退出登录）；退出调后端、本地全清（令牌、菜单、页签回首页初始态），后端异常也继续清
- 页签缓存动态接线：动态路由 noCache 语义进 keep-alive 缓存集，固定首页 tag 维持
- 权限点查询 useAuth（hasAuth）：权限集合查询 + ROLE_ROOT 直通，配合 v-if 藏显，不做 DOM 移除式指令

## 0.9.0

认证会话与菜单权限后端。

- RBAC 五表落地（用户 / 角色 / 用户角色 / 菜单 / 角色菜单）：按钮型权限点绑菜单树，菜单含 i18n key、组件路径 key、noCache、排序；幂等种子——admin 账号（初始密码 123456）、ROLE_ROOT 内置超管角色位（判定走角色位不依赖用户名）、初始菜单三条（首页与两个演示页）
- 登录链路全链：滑块验证码（出题答案只进 Redis、题目即焚、一次性票据）→ 密码 RSA-OAEP 加密传输（密钥对存 Redis 双槽：current + retired 带 10 分钟 TTL）→ keyId 定位解密（current 直接命中零额外读，跨重启在途密文点查 retired 兜底，皆无返回「公钥已过期」驱动前端重取）→ BCrypt 比对 → 不透明令牌对签发
- 会话语义：accessToken 即 Redis 会话键（删键即强退），refresh 按 userId + tokenId 记账；刷新旋转——旧键 TTL 收窄至 30 秒宽限窗口（可配），窗口内多 tab 并发各得新对；登出删会话与 refresh，幂等重入
- refresh cookie：HttpOnly + SameSite=Lax + path 收窄 /auth，Secure 标志环境变量可配（TLS 开启即开）；刷新端点自定义头必备（CSRF 纵深）
- 平台过滤链就位：匿名清单精确到端点，其余一律 authenticated；Bearer 令牌过滤器还原身份（authorities = 角色位 + 权限点）；401 / 403 同形结构输出；方法级 @PreAuthorize 载体生效
- `/auth/me`：用户视图（不含密码哈希）+ 角色码 + 权限点 + 菜单树（按角色关联组装，ROLE_ROOT 直通全菜单）
- 认证错误词条 zh / en 双语补齐，缺省语言锚定中文（locale 链 miss 落 root 包，不随 JVM 系统 locale 漂移）

## 0.8.0

发布基础设施。

- 生产编排 `deploy/compose.yaml`：web（nginx，唯一宿主映射端口）+ server + MySQL + Redis 四容器，数据服务端口一律不映射宿主，运行参数经环境变量注入（内联缺省即本地冒烟形态）
- 生产镜像两件：前端 workspace 构建进 nginx 运行镜像（依赖清单层缓存），后端 gradle 构建 bootJar 进 JRE 运行镜像（构建脚本层预热依赖缓存）
- nginx：history 路由回退、静态资产强缓存、`/api` 反代（去前缀与开发代理同语义）；gzip_static 优先 + 普通 gzip 兜底；TLS 与 WebSocket 反代均为注释预留段
- 前端构建产物预生成 gzip（文本类静态资产逐个同名 .gz），nginx gzip_static 直接命中
- Playwright 生产栈冒烟（`just e2e`）：首页渲染、主题 / 语言切换、深链刷新（history 回退）、gzip_static 压缩头、无控制台错误；门禁语义 = 非零退出即中止发布
- 一键发布 `scripts/deploy.sh`：前置校验 deploy env（缺文件或缺关键字段即停）→ 构建镜像 → 本地生产栈冒烟门禁 → 镜像 save|load 与组态 scp（远端无需源码与 registry）→ 远端 compose 拉起；`--dry-run` 只走构建与冒烟
- 数据库备份 `scripts/backup-mysql.sh`：docker exec mysqldump 单事务快照 + gzip，文件名带时间戳模式，滚动清理只删本脚本产出的 7 天前备份；crontab 安装示例内嵌脚本注释
- README 部署一节：本地生产栈验证、发布流程、备份定时安装

## 0.7.0

admin-ui 组件层。

- 新增 workspace 包 `@eladmin-plus/admin-ui`：声明式 CRUD 组件层，后续系统域页面直接消费；只依赖 vue / Element Plus / TanStack Table / zod / vueuse，不依赖应用代码
- DataTable：TanStack Table headless 自绘 DOM、EP CSS 变量对齐视觉；声明式 columns + 受控数据（组件不发请求），工具栏（刷新、列设置显隐即时生效），分页 page 0 起换算、单字段排序 asc→desc→取消循环、行选择按行 id 跨页记账；768px 断点自动切卡片形态（内容由 card-row 插槽提供），分页两形态共用
- AutoForm：zod schema 驱动 input / select / date / radio / checkbox / tree-select 字段渲染（schema 承载必填语义），提交时整表 parse、issue 精确映射字段错误并聚焦首个错误字段、未映射 issue 归表单级提示；具名插槽手写清单外字段，upload 类型留插槽位
- useDict：模块 Map 缓存 + 同 type 并发去重 + invalidate 失效口子；数据源注入式（setDictSource），包内置静态 mock 源，字典后端就位后换源组件层不动
- web 应用接入组件层消费冒烟测试；vitest 配置内联组件层与 Element Plus 样式入口（外部化模块的 .css 导入在 node 侧报错）
- 修复：顶栏设置入口观感（齿轮图标、三触发器统一样式与提示）、偏好抽屉控件顺序与等宽（语言→主题→主色，「随系统」统一殿后）

## 0.6.0

首页仪表盘。

- 首页换成静态演示仪表盘（版面与原版一致）：四张统计卡片（数字滚动动画）+ 全宽折线 + 雷达 / 饼 / 柱三列
- 卡片数字接入 `GET /stats/overview` 占位端点：加载中显占位符、失败显重试入口、数据变化重新滚动；切换真实数据仅换后端实现，视图不动
- ECharts 按需接入（只注册用到的图表与组件）：折线 / 饼 / 柱 / 雷达统一封装组件，容器尺寸变化自动重绘
- 图表随主题与语言联动：深浅切换与主色自定义即时重算色板（暗色分支单独适配），语言切换图例与轴标签随动，均不重建图表实例
- 卡片点击切换折线数据集（沿用原版交互）

## 0.5.0

主题与多语言。

- 偏好基础设施：主题、主色、语言统一 store 管理，localStorage 持久化（即改即存，无需保存按钮）；首次访问按系统语言与系统深浅色检测默认值
- 主题三态（浅色 / 深色 / 随系统）：随系统模式实时跟随系统切换；深色走 Element Plus 官方暗色变量
- 主色运行时自定义：预设色板 + 任意取色，即时生效持久化；色阶变体按浅 / 深两套基准计算（对齐 Element Plus 官方公式）
- 多语言框架（zh-CN / en）：静态文案全链 key 化——菜单、面包屑、页签、设置面板、演示页与 404 全覆盖；语言切换即时生效；语言包缺 key 时回退中文，双包 key 同构由测试把关
- 设置抽屉：右上角设置图标集中管理主题、主色、语言
- 偏好语言联动请求头 `Accept-Language`，后端错误消息随应用内语言本地化

## 0.4.0

接口契约链与数据访问。

- 响应结构全链统一为 `{code， message， data}`：成功失败同形，code 取 HTTP 状态码；失败回真实 4xx/5xx 状态行，message 可直接展示（资源文件本地化）；无返回体的写操作统一 200 加空 data
- 契约链：后端 springdoc 接口描述（swagger-ui 就位）+ 构建期导出 OpenAPI；`just api-gen` 一条命令生成前端 TS 类型、zod schema 与请求 client（生成物入库，克隆即可构建），接口变更在前端编译期暴露
- 统计概览占位端点（`GET /stats/overview`，静态演示数据）：为首页仪表盘预留数据源形状
- 数据访问：MySQL 数据源、MyBatis-Plus（分页插件与页码换算封装）、Flyway 数据库迁移、SQL 检查命令 `just sql-lint`
- 前端数据层：TanStack Query 接入（默认 30 秒缓存、失败重试一次、开发态调试面板）；请求统一解包取数据，失败抛携带状态码的错误；统计查询样例（zod 校验演示）

## 0.3.0

后端应用壳。

- Spring Boot 4.1 应用骨架：Gradle 9.8.2 wrapper（发行版走腾讯云镜像、依赖走阿里云镜像，克隆即构建）、Java 21（仓库根 .java-version 锁定 21.0.12 + 构建期 toolchain 兜底）、Lombok
- 统一错误响应 `{status， timestamp， message}`：受控异常映射语义化状态码、未知异常回 500 通用消息（堆栈只进日志），错误消息经资源文件管理；成功响应裸数据
- 安全骨架：全放行过滤链（结构为后续认证段接线而立），端点匿名可达
- actuator 健康端点（Redis UP 才 UP）、端口 8080、配置默认本地 compose 实例（Redis 6380）可环境变量覆盖
- Redis 连接模板：key 字符串 / value JSON 序列化，key 统一 eladmin-plus:域:键 前缀
- 日志：控制台 + 按天与大小上限滚动落盘
- 仓库级命令：just server-build / server-test / server-run

## 0.2.0

前端应用壳与布局。

- Vue 3 应用骨架：Vite 8 构建、TypeScript strict、SCSS 全局样式（颜色全部走 Element Plus CSS 变量）
- Element Plus 按需自动注册（构建后最大 JS chunk gzip 约 125KB）；开发代理 /api 指本地后端
- 静态路由（history 模式）：首页占位、两个演示页（其一不缓存）、404；路由 meta 惯例（title / icon / noCache）
- 布局：侧栏可折叠、移动端抽屉化（768px 断点）、菜单自路由表生成、面包屑、全屏、搜索与头像等占位项
- 多页签：固定首页页签、右键菜单（关闭当前 / 其他 / 全部）、keep-alive 按页面名缓存、不缓存页关闭即销毁
- SVG 图标体系：80 个本地图标按名渲染，未知图标兜底
- 测试基座：Vitest（happy-dom）+ testing-library，20 个用例接入提交门禁

## 0.1.0

首个版本：仓库底座。

- pnpm 工作区骨架（apps/web、apps/server、packages/config、packages/admin-ui、packages/shared）与版本钉定（mise：node 24、pnpm 12.14.0、just）
- 一条命令起本地依赖：MySQL 8（默认 3307）+ Redis 7（默认 6380），compose 编排、健康检查、数据卷；端口可经 `.env` 切换既有实例
- 统一命令入口 justfile：install / up / down / lint / format / typecheck / test
- 共享检查链：ESLint（含 Vue 规则）+ Prettier，配置集中在 packages/config，tsconfig 严格基座
- 提交门禁：lefthook（pre-commit 跑检查与测试，commit-msg 跑常规提交规范校验）
- 项目约定文档（命名、行文、提交、worktree）
