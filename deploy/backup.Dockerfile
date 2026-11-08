# 备份 sidecar 镜像：mariadb:11 自带 mariadb-dump（含 MySQL 8 caching_sha2_password 认证插件，
# alpine 的精简 client 缺该插件连不上 mysql:8.4），叠加 busybox 提供 CGI HTTP 端点
# （alpine 的 busybox 构建无 httpd applet，取 Debian 包）。
# 构建上下文为仓库根（compose.yaml 的 context）
FROM mariadb:11@sha256:c4124d1afa875e47f2892edf26b482fa54f796e2f342d3717a04ca091a22a9bd
RUN apt-get update -qq \
    && apt-get install -y --no-install-recommends -qq busybox \
    && rm -rf /var/lib/apt/lists/*
COPY deploy/backup/dump /var/www/cgi-bin/dump
RUN chmod 755 /var/www/cgi-bin/dump
ENV DUMP_DB_HOST=mysql \
    DUMP_DB_PORT=3306 \
    DUMP_HTTP_PORT=8080
EXPOSE 8080
ENTRYPOINT ["busybox", "httpd", "-f", "-p", "8080", "-h", "/var/www"]
