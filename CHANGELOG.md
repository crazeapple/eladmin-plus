# 更新日志

## 0.1.0

首个版本：仓库底座。

- pnpm 工作区骨架（apps/web、apps/server、packages/config、packages/admin-ui、packages/shared）与版本钉定（mise：node 24、pnpm 12.14.0、just）
- 一条命令起本地依赖：MySQL 8（默认 3307）+ Redis 7（默认 6380），compose 编排、健康检查、数据卷；端口可经 `.env` 切换既有实例
- 统一命令入口 justfile：install / up / down / lint / format / typecheck / test
- 共享检查链：ESLint（含 Vue 规则）+ Prettier，配置集中在 packages/config，tsconfig 严格基座
- 提交门禁：lefthook（pre-commit 跑检查与测试，commit-msg 跑常规提交规范校验）
- 项目约定文档（命名、行文、提交、worktree）
