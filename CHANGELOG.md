# 更新日志

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
