# 部署文件说明

项目根目录 compose.yml 仅部署两个业务容器：app 和 runner。直接连接现有 MySQL 5.7，由现有 Nginx 代理，不部署或修改数据库、Nginx 服务。

| 文件 | 用途 | 是否自动使用 |
| --- | --- | --- |
| Dockerfile.app | 构建 Vue 前端并打包进 Spring Boot JAR，运行时仅需 Java | Compose 构建 app 时使用 |
| Dockerfile.runner | 打包普通 Java runner 及 lib 依赖 | Compose 构建 runner 时使用 |
| runner-entrypoint.sh | 启动 Supervisor，通过 Unix Socket 与 app 通信，自动管理 Worker | runner 容器启动时执行 |
| env.example | 现有数据库、Nginx 入口及宿主端口的环境变量模板 | 手动复制到根目录 .env 后读取 |
| env.external-mysql.example | 兼容先前操作习惯的同义模板，新部署统一使用 env.example | 可选，不重复配置 |
| nginx.conf | 合并到现有 Nginx server 内的 location 参考片段 | 不自动加载，不是独立主配置 |
| admin-allow.conf | 可选的管理入口网段参考，按实际网段调整 | 不自动加载 |
| init-mysql.sql | 现有实例尚未分配项目库时的手动建库参考 | 不自动执行，已有库无需执行 |

## 端口和代理

app 镜像内部固定监听 8080，默认通过 Compose 发布到宿主机 127.0.0.1:16080。端口映射与数据库连接均在项目根目录 .env 配置，不需要修改 Dockerfile。前端访问地址独立在 frontend 的环境配置文件管理，页面请求和复制 Mock 链接复用同一个地址。

- Nginx 在同一台宿主机：proxy_pass 使用 http://127.0.0.1:16080
- Nginx 在另一台服务器：APP_BIND 使用业务宿主机内网 IP，proxy_pass 指向该 IP 和 APP_PORT
- Nginx 也在容器中时，127.0.0.1 指向 Nginx 自身，应由现有部署环境提供可达的业务地址
- 前端开发统一在 frontend/.env.development 配置 VITE_BACKEND_URL，生产在 frontend/.env.production 默认留空，自动复用当前页面入口，后端不再配置调用基础地址
- runner 不暴露网络端口，通过专用 runner-socket 卷通信

前端页面由 app 直接提供，已有 Nginx 只需反向代理，无需单独部署前端静态目录。请将 nginx.conf 中需要的 location 合并到已有站点，保留现有域名、TLS 和访问控制。

## 数据库

DB_URL、DB_USER、DB_PASSWORD 使用已有数据库连接。数据库地址必须能从 app 容器访问。无需数据库 root 密码。

默认 DB_INIT_MODE=always，后端启动时在目标库执行 backend/src/main/resources/schema.sql 创建缺失的表。如果 DBA 已建表，设为 never。init-mysql.sql 只负责可选建库，不是建表脚本，也不创建账号。

## 启动

在项目根目录准备 .env，然后执行：

```sh
docker compose config --quiet
docker compose up -d --build
docker compose logs --tail=100 app runner
```

完整配置及离线部署见 [部署教程](../docs/部署教程.md)。这些文件不会直接修改现有 Nginx 或 MySQL，实际构建与启动需在目标 Linux Docker 环境验证。
