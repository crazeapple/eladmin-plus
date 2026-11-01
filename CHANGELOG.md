# 更新日志

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

- 响应结构全链统一为 `{code, message, data}`：成功失败同形，code 取 HTTP 状态码；失败回真实 4xx/5xx 状态行，message 可直接展示（资源文件本地化）；无返回体的写操作统一 200 加空 data
- 契约链：后端 springdoc 接口描述（swagger-ui 就位）+ 构建期导出 OpenAPI；`just api-gen` 一条命令生成前端 TS 类型、zod schema 与请求 client（生成物入库，克隆即可构建），接口变更在前端编译期暴露
- 统计概览占位端点（`GET /stats/overview`，静态演示数据）：为首页仪表盘预留数据源形状
- 数据访问：MySQL 数据源、MyBatis-Plus（分页插件与页码换算封装）、Flyway 数据库迁移、SQL 检查命令 `just sql-lint`
- 前端数据层：TanStack Query 接入（默认 30 秒缓存、失败重试一次、开发态调试面板）；请求统一解包取数据，失败抛携带状态码的错误；统计查询样例（zod 校验演示）

## 0.3.0

后端应用壳。

- Spring Boot 4.1 应用骨架：Gradle 9.8.2 wrapper（发行版走腾讯云镜像、依赖走阿里云镜像，克隆即构建）、Java 21（仓库根 .java-version 锁定 21.0.12 + 构建期 toolchain 兜底）、Lombok
- 统一错误响应 `{status, timestamp, message}`：受控异常映射语义化状态码、未知异常回 500 通用消息（堆栈只进日志），错误消息经资源文件管理；成功响应裸数据
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
