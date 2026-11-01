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

本地依赖默认端口 MySQL 3307、Redis 6380（错开机器既有实例）；在根目录建 `.env` 可覆盖，模板与既有实例切换样例见 `.env.example`。

## 环境准备（mise）

node、pnpm、just 的版本由根目录 `.mise.toml` 统一钉定，进目录自动切换：

1. 安装 mise：macOS / Linux 执行 `curl https://mise.run | sh`；Windows 执行 `winget install jdx.mise`
2. 激活（一次性 shell 配置）：zsh 在 `~/.zshrc` 加 `eval "$(mise activate zsh)"`，bash 对应 `~/.bashrc`
3. 进仓库目录执行 `mise install` 装齐钉定版本，`mise exec -- node -v` 应输出 v24.x

未激活 mise 的环境可用 `mise exec -- <命令>` / `mise run <命令>` 等价获得钉定版本。
