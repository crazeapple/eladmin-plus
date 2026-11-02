#!/usr/bin/env bash
# MySQL 备份：docker exec mysqldump | gzip 写备份目录，按文件名模式滚动清理（保留最近 7 天）
# 用法：scripts/backup-mysql.sh [容器名]
#   容器名缺省为本地开发容器 eladmin-plus-mysql；生产传 eladmin-plus-prod-mysql
# 环境变量：BACKUP_DIR（缺省仓库根 backups/）、MYSQL_ROOT_PASSWORD（缺省 root）、DB_NAME（缺省 eladmin_plus）
# crontab 安装示例（每日 02:30，宿主执行）：
#   30 2 * * * /bin/bash /opt/eladmin-plus/scripts/backup-mysql.sh eladmin-plus-prod-mysql >> /var/log/eladmin-plus-backup.log 2>&1
set -euo pipefail

CONTAINER="${1:-eladmin-plus-mysql}"
RETAIN_DAYS=7
DB_NAME="${DB_NAME:-eladmin_plus}"
BACKUP_DIR="${BACKUP_DIR:-"$(cd "$(dirname "$0")/.." && pwd)/backups"}"
STAMP_PREFIX="eladmin-plus-db-"

log() { echo "[backup] $*"; }
die() { echo "[backup] 失败：${*}，容器未运行或不可达" >&2; exit 1; }

mkdir -p "$BACKUP_DIR"
docker inspect "$CONTAINER" >/dev/null 2>&1 || die "容器 ${CONTAINER} 不存在"

STAMP="$(date +%Y%m%d-%H%M%S)"
TARGET="$BACKUP_DIR/${STAMP_PREFIX}${STAMP}.sql.gz"
log "备份 ${CONTAINER}/${DB_NAME} → ${TARGET} ……"

# 单事务一致性快照，含触发器与存储过程；密码经环境变量传入 mysqldump
docker exec "$CONTAINER" sh -c "MYSQL_PWD=\${MYSQL_ROOT_PASSWORD:-root} mysqldump -uroot --single-transaction --routines --triggers ${DB_NAME}" | gzip > "$TARGET"

SIZE="$(du -h "$TARGET" | cut -f1)"
log "备份完成（${SIZE}）"

# 滚动清理：只删本脚本产出的过期备份（按文件名模式识别），不碰目录内其他文件
DELETED=$(find "$BACKUP_DIR" -name "${STAMP_PREFIX}*.sql.gz" -type f -mtime +"${RETAIN_DAYS}" -delete -print | wc -l | tr -d ' ')
log "滚动清理完成：删除 ${RETAIN_DAYS} 天前备份 ${DELETED} 份（保留最近 ${RETAIN_DAYS} 天）"
