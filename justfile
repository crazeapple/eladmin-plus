set shell := ["bash", "-cu"]

# 安装全部 JS 侧依赖
install:
    pnpm install

# 起本地依赖（compose：MySQL、Redis）并等待健康检查通过
up:
    docker compose up -d --wait

# 停本地依赖（保留数据卷，开发数据不删）
down:
    docker compose down

# 代码检查（ESLint）+ 格式检查（Prettier）
lint:
    pnpm exec eslint .
    pnpm exec prettier --check .

# 格式化（Prettier）
format:
    pnpm exec prettier --write .

# 类型检查（递归跑各工作区成员的 typecheck 脚本）
typecheck:
    pnpm -r run typecheck

# 测试（递归跑各工作区成员的 test 脚本）
test:
    pnpm -r run test

# 生产栈 E2E 冒烟（先起栈：docker compose -f deploy/compose.yaml up -d --wait）
e2e:
    pnpm --filter @eladmin-plus/web run test:e2e

# 前端开发服务器（vite 默认 5173；接口代理到本地后端 8080，先 just server-run）
web-dev:
    pnpm --filter @eladmin-plus/web run dev

# server 构建（apps/server；首次运行自动经镜像下载 Gradle 发行版与依赖）
server-build:
    cd apps/server && ./gradlew build

# server 测试（依赖本地依赖已起：先 just up）
server-test:
    cd apps/server && ./gradlew test

# server 本地运行（依赖本地依赖已起：先 just up；Ctrl+C 中断属正常退出，静默 just 的退出提示）
[no-exit-message]
server-run:
    cd apps/server && ./gradlew bootRun

# SQL 检查（sqlfluff 为全局 Python 工具需自行安装；仅手动运行，不挂提交门禁）
sql-lint:
    cd apps/server && sqlfluff lint src/main/resources/db

# 契约生成：后端构建期导出 OpenAPI JSON + 前端生成 TS 类型、zod schema 与 client（生成物入库）
api-gen:
    cd apps/server && rm -f build/openapi.json && ./gradlew generateOpenApiDocs -q
    pnpm --filter @eladmin-plus/web run api-gen
