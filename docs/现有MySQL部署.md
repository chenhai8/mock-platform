# 使用现有 MySQL 5.7 部署

本方案直接连接已有 MySQL 5.7，默认 compose.yml 仅运行 app 和 runner 两个容器。已有 Nginx 负责代理，不创建、升级或重启 MySQL 或 Nginx 服务。完整入口代理及部署步骤见 [部署教程](部署教程.md)

## 1. 数据库准备

由数据库管理员在现有 MySQL 5.7 实例内为 MockLab 准备独立的 mocklab 数据库，避免与其他系统共用同名表。可执行 deploy/init-mysql.sql，或在连接地址中指定管理员分配的其他库名

已有 schema.sql 兼容 MySQL 5.7：使用 InnoDB、utf8mb4_bin、LONGTEXT、自增主键和外键，没有依赖 MySQL 8 的排序规则、窗口函数或 CTE。驱动固定为 Connector/J 8.0.33

应用账号仅授权目标数据库。默认 DB_INIT_MODE=always，应用启动时执行 CREATE TABLE IF NOT EXISTS，建表需 CREATE、REFERENCES 权限。如果 DBA 手动在目标库执行 backend/src/main/resources/schema.sql，则可设 DB_INIT_MODE=never，应用仅需 SELECT、INSERT、UPDATE、DELETE 权限

建表脚本不更改已有同名表结构，不自动迁移历史数据

## 2. 配置

从项目根目录执行：

```sh
cp deploy/env.example .env
chmod 600 .env
```

如果已有 .env，直接编辑现有文件，保留已有密码和入口配置，不要覆盖。设置 DB_URL、DB_USER、DB_PASSWORD、MOCK_BASE_URL 为实际值

DB_URL 中应填写容器可以访问的数据库内网 IP 或域名，不能填写 127.0.0.1（那是 app 容器自身）。数据库与应用在同一服务器也应填写可路由的宿主内网地址，并由管理员允许应用的实际来源地址访问数据库

不需要 DB_ROOT_PASSWORD。示例为内网非 TLS 连接；现有数据库要求 TLS 时按公司要求调整 DB_URL

## 3. 构建和启动

```sh
docker compose config --quiet
docker compose build
docker compose up -d
docker compose ps
docker compose logs --tail=100 app runner
```

默认 compose.yml 已统一为两服务部署；compose.external-mysql.yml 保留相同配置以兼容已有操作习惯，后续推荐统一使用 docker compose

```sh
curl -f http://127.0.0.1:16080/actuator/health
```

APP_BIND、APP_PORT 决定后端在宿主机的监听地址和端口。默认仅供同机 Nginx 代理，Nginx 在远程时将 APP_BIND 改为业务服务器内网 IP，并替换检查地址。MOCK_BASE_URL 使用已有 Nginx 的实际访问入口。runner 就绪、数据库地址和授权正确后应返回 UP

## 4. 备份及离线部署

数据库备份和恢复由现有 MySQL 的运维流程负责。scripts/backup.sh 是历史数据库容器备份脚本，不适用于当前两服务部署，也不需要执行

离线部署只需 app 和 runner 两个业务镜像，无需 MySQL 或 Nginx 镜像。在联网构建机执行：

```sh
docker compose build
docker save -o mocklab-external-images.tar mocklab-app:1.0.0 mocklab-runner:1.0.0
sha256sum mocklab-external-images.tar > mocklab-external-images.sha256
```

使用新的输出文件名，避免覆盖已有发布包。将镜像、校验文件、compose.yml 和 deploy/ 复制到目标服务器，校验并 docker load 后配置 .env，再执行上述 up -d 启动

也可以直接使用 scripts/package-offline.sh，仅构建并导出 app 和 runner 两个业务镜像

## 5. 验证边界

目前仅验证源码构建和现有单元测试，尚未连接实际 MySQL 5.7 或执行目标 Linux Docker 部署。部署时需要验证建表、配置保存、发布、调用日志和事务回滚
