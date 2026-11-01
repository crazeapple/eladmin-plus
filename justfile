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

# 代码检查（lint 链就位前为占位）
lint:
    echo "lint 尚未接入"

# 格式化（format 链就位前为占位）
format:
    echo "format 尚未接入"

# 类型检查（仓库内暂无 TypeScript 项目，提示即过）
typecheck:
    echo "typecheck: 暂无 TypeScript 项目"

# 测试（测试链就位前为占位）
test:
    echo "test 尚未接入"
