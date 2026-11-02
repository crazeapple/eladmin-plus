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
| `just web-dev`   | 前端开发服务器（需后端已起）                 |
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

## 偏好（主题与语言）

右上角设置图标打开设置抽屉：主题三态（浅色 / 深色 / 随系统）、主色（预设色板或任意取色）、语言（跟随系统 / 简体中文 / English）即改即生效，并持久化在浏览器（localStorage），下次打开自动恢复；随系统模式跟随系统深浅色实时切换。偏好语言会同步到请求头 `Accept-Language`，后端错误消息随之本地化。

## 部署

生产栈与本地开发同构：`deploy/compose.yaml` 编排 web（nginx，唯一宿主映射端口）+ server + MySQL + Redis 四容器，数据服务端口一律不映射宿主，运行参数经环境变量注入。

### 本地生产栈验证

```bash
docker compose -f deploy/compose.yaml up -d --wait   # 构建镜像并起栈，等全部 healthy
just e2e                                             # 生产栈冒烟：首页渲染 / 主题语言切换 / 深链刷新 / gzip / 无 console 错误
docker compose -f deploy/compose.yaml down           # 停栈（保留数据卷）
```

栈起在 `http://localhost:80`（`WEB_PORT` 可覆盖）；`docker compose ps` 中四容器均 healthy 即验证形态。

### 一键发布

```bash
cp deploy/deploy.env.example deploy/deploy.env   # 填远程主机、路径与运行参数（实文件不入库）
scripts/deploy.sh --dry-run                       # 只走构建与冒烟，验证发布链不触碰远端
scripts/deploy.sh                                 # 构建 → 冒烟门禁（非零即中止）→ 传输镜像与组态 → 远端 compose 拉起
```

冒烟全绿才允许发布：`test:e2e` 非零退出时脚本立即中止，传输与远端阶段不执行。TLS 未启用（nginx 配置内含证书就位后的注释段）；WebSocket 反代段同样以注释预留。

### 备份

```bash
scripts/backup-mysql.sh eladmin-plus-prod-mysql   # 立即备份一次（docker exec mysqldump + gzip）
```

备份写入 `backups/`（`BACKUP_DIR` 可覆盖），文件名带时间戳，脚本自动滚动清理 7 天前的旧备份（只识别本脚本产出的文件名模式）。宿主机定时安装一行 crontab（每日 02:30）：

```bash
30 2 * * * /bin/bash /opt/eladmin-plus/scripts/backup-mysql.sh eladmin-plus-prod-mysql >> /var/log/eladmin-plus-backup.log 2>&1
```

## 环境准备（mise）

node、pnpm、just 的版本由根目录 `.mise.toml` 统一钉定，进目录自动切换：

1. 安装 mise：macOS / Linux 执行 `curl https://mise.run | sh`；Windows 执行 `winget install jdx.mise`
2. 激活（一次性 shell 配置）：zsh 在 `~/.zshrc` 加 `eval "$(mise activate zsh)"`，bash 对应 `~/.bashrc`
3. 进仓库目录执行 `mise install` 装齐钉定版本，`mise exec -- node -v` 应输出 v24.x

未激活 mise 的环境可用 `mise exec -- <命令>` / `mise run <命令>` 等价获得钉定版本。
