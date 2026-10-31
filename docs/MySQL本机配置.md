# MySQL 本机配置

开发与生产统一使用 MySQL，兼容现有 MySQL 5.7，也可连接 MySQL 8.x。JDBC 驱动固定为 Connector/J 8.0.33，避免 Spring Boot 默认驱动版本变化影响 5.7 兼容性

## 1. 准备数据库

在 Navicat、DataGrip 或 MySQL 客户端中，用有建库权限的账号执行 `deploy/init-mysql.sql`，创建 `mocklab` 数据库。此脚本不删除或清空已有数据库

请数据库管理员创建应用账号并授予该库所需权限。默认账号名为 `mocklab`，也可以通过 `DB_USER` 指定已有账号。首次启动自动建表需要 CREATE、REFERENCES 权限，正常运行需要 SELECT、INSERT、UPDATE、DELETE 权限

`backend/src/main/resources/schema.sql` 是建表脚本，后端启动时自动在连接的数据库执行，不需要手动逐表创建。也可以在选中 `mocklab` 数据库后手动执行该脚本

所有表显式使用 InnoDB、utf8mb4、utf8mb4_bin，支持事务、外键、中文及 Emoji，并保持字符串大小写敏感。CREATE TABLE IF NOT EXISTS 只创建缺失的表，不修改已有表结构

## 2. IDEA 启动配置

打开 backend 的 Spring Boot Run Configuration，在 Environment variables 中设置以下变量，各变量应单独添加：

| 变量 | 值 |
| --- | --- |
| DB_URL | jdbc:mysql://127.0.0.1:3306/mocklab?useUnicode=true&characterEncoding=UTF-8&serverTimezone=UTC&allowPublicKeyRetrieval=true&useSSL=false |
| DB_USER | 实际数据库账号，默认 mocklab |
| DB_PASSWORD | 实际数据库密码，必填 |

MySQL 在其他服务器或端口时，替换 DB_URL 中的地址和端口。密码通过环境变量提供，不写入 application.yml

后端保留当前本机端口 16080。前端在 frontend/.env.development 中设置 VITE_BACKEND_URL=http://127.0.0.1:16080，开发代理、管理请求和复制的 Mock 链接共用此配置。后端端口变化时同步修改该前端文件并重启 npm run dev，不需要修改 vite.config.js

本机开发时访问 Vite 输出的前端地址，后端 16080 仅提供 HTTP 接口。生产将 dist 部署到 Nginx，前端默认使用当前页面入口生成 Mock 链接，无需后端地址环境变量

## 3. 终端启动

在启动后端的 PowerShell 窗口中设置 DB_USER、DB_PASSWORD，需要连接非本机数据库时再设置 DB_URL，然后运行 scripts/start-app.ps1。脚本会继承当前窗口的环境变量

修改后先重新执行 Maven package，使用 backend/target/backend-1.0.0.jar，release 目录中的旧 JAR 不会随源码修改自动更新

runner 不连接数据库，启动方式不变

## 4. Docker 部署

默认 `compose.yml` 仅运行 app 和 runner，使用已有 MySQL 5.7 及已有 Nginx，按 [部署教程](部署教程.md) 配置 DB_URL、DB_USER、DB_PASSWORD。不需要 DB_ROOT_PASSWORD，也不创建数据库或 Nginx 容器。

容器内部后端端口为 8080，默认发布到宿主机 127.0.0.1:16080，已有 Nginx 代理到该地址。如果 Nginx 位于另一台服务器，APP_BIND 设置为业务服务器内网 IP。

修改 .env 只更新应用连接配置，不会修改现有数据库账号密码。数据库备份、恢复及账号授权沿用现有运维流程。

## 5. 数据说明

旧 H2 文件继续保留，但切换到 MySQL 后不会自动读取或迁移旧数据。需要保留旧配置时应另行安排迁移，不要删除原 data 目录

没有连接实际 MySQL 时，Maven 编译和单元测试通过不能证明数据库初始化与真实读写已经通过验收
