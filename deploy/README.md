# 部署文件说明

项目根目录 compose.yml 仅部署两个业务容器：app 和 runner。数据库使用现有 MySQL 5.7，反向代理使用现有 Nginx，不部署数据库容器，不新增 Nginx 容器。

## 文件

| 文件 | 用途 | 是否自动使用 |
| --- | --- | --- |
| Dockerfile.app | 构建 Spring Boot JAR，不包含前端静态资源 | Compose 构建 app 时使用 |
| Dockerfile.runner | 构建脚本执行服务 | Compose 构建 runner 时使用 |
| nginx.conf | 现有 Nginx 的 location 参考片段：前端静态目录 + /api、/m/、/actuator 反代 | 手动合入现有 Nginx，不会被 Compose 加载 |
| env.example | MySQL 连接与 app 容器发布端口的模板 | 手动复制为根目录 .env |
| init-mysql.sql | 数据库建库参考 | 数据库已有时不需要 |

## 前端构建

```bash
cd frontend
npm ci
VITE_BACKEND_URL= npm run build
```

构建产物输出到项目根目录 `dist/`，需要手工复制到现有 Nginx webroot。生产环境 VITE_BACKEND_URL 留空时前端请求走浏览器当前 origin，由 Nginx 分发到静态目录和后端接口。

## 现有 Nginx 配置

将 `deploy/nginx.conf` 的 location 片段合入现有 Nginx server。静态资源 `location /` 指向 dist 复制到的 webroot；`location /api`、`location /m/` 反代到 app 容器发布地址。

- 同机 Nginx：`http://127.0.0.1:16080`
- 远程 Nginx：`http://<APP_BIND>:<APP_PORT>`，APP_BIND 使用业务服务器内网 IP

## 启动

```bash
cd 项目根目录
cp deploy/env.example .env
# 按需修改 .env 中 MySQL 与端口配置

docker compose config --quiet
docker compose up -d --build
docker compose logs -f app runner
```

访问入口仍为现有 Nginx 的域名/地址，不新增业务入口端口。
