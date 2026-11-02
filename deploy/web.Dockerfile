# 前端生产镜像：workspace 全量上下文构建 web 产物，nginx 运行
# 构建上下文为仓库根（compose.yaml 的 context: ..）
FROM node:24-alpine AS build
WORKDIR /app

# 先拷依赖清单装依赖，源码变更走层缓存
COPY pnpm-lock.yaml pnpm-workspace.yaml package.json .npmrc ./
COPY apps/web/package.json apps/web/
COPY packages/config/package.json packages/config/
COPY packages/shared/package.json packages/shared/
COPY packages/admin-ui/package.json packages/admin-ui/
# 容器内无 git 且不装钩子：构建层移除 prepare 脚本（不影响仓库源文件，esbuild 等依赖构建脚本照常）
RUN corepack enable && npm pkg delete scripts.prepare && pnpm install --frozen-lockfile

COPY apps/web apps/web
COPY packages packages
RUN pnpm --filter @eladmin-plus/web build

FROM nginx:alpine
COPY deploy/nginx/app.conf /etc/nginx/conf.d/default.conf
COPY --from=build /app/apps/web/dist /usr/share/nginx/html
