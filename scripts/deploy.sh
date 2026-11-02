#!/usr/bin/env bash
# 一键发布：构建 → E2E 冒烟门禁（非零即中止）→ 传输镜像与部署组态 → 远端 compose 拉起
# 用法：scripts/deploy.sh [--dry-run]
#   --dry-run 只走构建与冒烟两阶段（验证的就是将要发布的产物），不触碰远端
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "$0")/.." && pwd)"
ENV_FILE="$ROOT_DIR/deploy/deploy.env"
COMPOSE="docker compose -f $ROOT_DIR/deploy/compose.yaml"
WEB_IMAGE=eladmin-plus-prod-web:latest
SERVER_IMAGE=eladmin-plus-prod-server:latest

DRY_RUN=false
for arg in "$@"; do
  case "$arg" in
    --dry-run) DRY_RUN=true ;;
    *) echo "[deploy] 未知参数：$arg" >&2; exit 1 ;;
  esac
done

log() { echo "[deploy] $*"; }
die() { echo "[deploy] 失败：$*" >&2; exit 1; }

# —— 前置校验：env 在、关键字段齐，缺即停，不进入任何阶段 ——
preflight() {
  [[ -f "$ENV_FILE" ]] || die "deploy env 不存在：${ENV_FILE}（从 deploy/deploy.env.example 复制并填写）"
  set -a
  . "$ENV_FILE"
  set +a
  # 关键字段逐项显式检查（bash 3.2 的间接展开不支持 :- 组合，不玩花活）
  [[ -n "${DEPLOY_HOST:-}" ]] || die "deploy env 缺关键字段：DEPLOY_HOST"
  [[ -n "${DEPLOY_USER:-}" ]] || die "deploy env 缺关键字段：DEPLOY_USER"
  [[ -n "${DEPLOY_PATH:-}" ]] || die "deploy env 缺关键字段：DEPLOY_PATH"
  log "前置校验通过（目标：${DEPLOY_USER}@${DEPLOY_HOST}:${DEPLOY_PATH}，dry-run=${DRY_RUN}）"
}

# —— 构建：生产镜像（web 产物含 .gz、server bootJar 均在镜像构建管线内产出）——
build_web() {
  log "阶段：构建 web 镜像……"
  $COMPOSE build web
}

build_server() {
  log "阶段：构建 server 镜像……"
  $COMPOSE build server
}

# —— 冒烟门禁：本地生产栈全绿才放行；非零即中止，后续阶段不执行 ——
smoke() {
  log "阶段：本地生产栈冒烟（发布门禁）……"
  $COMPOSE up -d --wait
  if ! pnpm --filter @eladmin-plus/web run test:e2e; then
    die "E2E 冒烟未全绿——发布中止（传输与远端阶段未执行）"
  fi
  log "冒烟全绿，放行"
}

# —— 传输：镜像 save|load + 部署组态 scp（远端无需源码与 registry）——
transfer() {
  if $DRY_RUN; then
    log "[dry-run] 跳过传输"
    return 0
  fi
  log "阶段：传输镜像与组态 → ${DEPLOY_USER}@${DEPLOY_HOST}:${DEPLOY_PATH} ……"
  ssh "$DEPLOY_USER@$DEPLOY_HOST" "mkdir -p '$DEPLOY_PATH/deploy'"
  docker save "$WEB_IMAGE" "$SERVER_IMAGE" | gzip | ssh "$DEPLOY_USER@$DEPLOY_HOST" "gunzip | docker load"
  scp "$ROOT_DIR/deploy/compose.yaml" "$DEPLOY_USER@$DEPLOY_HOST:$DEPLOY_PATH/deploy/compose.yaml"
  scp "$ENV_FILE" "$DEPLOY_USER@$DEPLOY_HOST:$DEPLOY_PATH/deploy/deploy.env"
}

# —— 远端拉起：镜像已就位，compose 直接用本地镜像起栈 ——
remote_up() {
  if $DRY_RUN; then
    log "[dry-run] 跳过远端拉起"
    return 0
  fi
  log "阶段：远端 compose 拉起……"
  ssh "$DEPLOY_USER@$DEPLOY_HOST" "cd '$DEPLOY_PATH' && docker compose -f deploy/compose.yaml up -d --wait"
}

preflight
build_web
build_server
smoke
transfer
remote_up
log "发布完成"
