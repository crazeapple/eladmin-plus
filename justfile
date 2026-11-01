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

# 类型检查（仓库内暂无 TypeScript 项目，提示即过）
typecheck:
    echo "typecheck: 暂无 TypeScript 项目"

# 测试（Vitest；空测试集不阻塞）
test:
    pnpm exec vitest run --passWithNoTests
