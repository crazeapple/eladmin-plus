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

# server 构建（apps/server；首次运行自动经镜像下载 Gradle 发行版与依赖）
server-build:
    cd apps/server && ./gradlew build

# server 测试（依赖本地依赖已起：先 just up）
server-test:
    cd apps/server && ./gradlew test

# server 本地运行（依赖本地 Redis 已起：先 just up）
server-run:
    cd apps/server && ./gradlew bootRun

# SQL 检查（sqlfluff 为全局 Python 工具需自行安装；仅手动运行，不挂提交门禁）
sql-lint:
    cd apps/server && sqlfluff lint src/main/resources/db
