# eladmin-plus

Vue 3 + Element Plus 前端与 Spring Boot 后端的管理系统 monorepo。

## 常用命令

| 命令             | 作用                                         |
| ---------------- | -------------------------------------------- |
| `just install`   | 安装全部 JS 侧依赖                           |
| `just up`        | 起本地依赖（MySQL、Redis），等待健康检查通过 |
| `just down`      | 停本地依赖（保留数据卷）                     |
| `just lint`      | 代码检查                                     |
| `just format`    | 格式化                                       |
| `just typecheck` | 类型检查                                     |
| `just test`      | 测试                                         |
| `just sql-lint`  | SQL 检查（sqlfluff，需全局安装）             |

`just sql-lint` 依赖全局安装的 [sqlfluff](https://docs.sqlfluff.com/)（如 `pipx install sqlfluff`）；未安装时该配方失败属预期，不影响其余命令与提交门禁。

本地依赖默认端口 MySQL 3307、Redis 6380（错开机器既有实例）；在根目录建 `.env` 可覆盖，模板与既有实例切换样例见 `.env.example`。

## 本地数据库

MySQL 数据目录首次初始化时由 compose 自动创建 `eladmin_plus` 库；已初始化过的旧数据卷不会自动建库，需补建一次（dev 数据可弃也可直接重建卷）：

```bash
docker exec eladmin-plus-mysql mysql -uroot -proot -e "CREATE DATABASE IF NOT EXISTS eladmin_plus CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci"
```

## 契约链

后端接口是唯一真相源：改完后端跑 `just api-gen`（后端构建期导出 OpenAPI，再生成前端 TS 类型、zod schema 与请求 client），生成物入库 `apps/web/src/api/generated`。提交生成物后，接口变更的影响面在 `just typecheck` 编译期可见。

前端请求统一经 `apps/web/src/api/client.ts`：baseURL `/api`，开发态由 vite proxy 去掉 `/api` 前缀转发到 8080；成功响应自动解包取 `data`，失败响应抛 `ApiError`（携带后端 `code` 与可直接展示的 `message`）。

## 环境准备（mise）

node、pnpm、just 的版本由根目录 `.mise.toml` 统一钉定，进目录自动切换：

1. 安装 mise：macOS / Linux 执行 `curl https://mise.run | sh`；Windows 执行 `winget install jdx.mise`
2. 激活（一次性 shell 配置）：zsh 在 `~/.zshrc` 加 `eval "$(mise activate zsh)"`，bash 对应 `~/.bashrc`
3. 进仓库目录执行 `mise install` 装齐钉定版本，`mise exec -- node -v` 应输出 v24.x

未激活 mise 的环境可用 `mise exec -- <命令>` / `mise run <命令>` 等价获得钉定版本。
