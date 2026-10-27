# MockLab 内网 Mock 平台

独立的 Java/Spring Boot 单体应用，用于模拟 ERP、WMS 等第三方系统接口。通过网页配置方法、路径和返回规则，发布后即可被内网服务器直接通过 HTTP 调用。

## 已实现

- 第三方系统与接口管理、归档恢复、接口复制
- UTF-8 原样报文、状态码、Content-Type、自定义响应头、异步延迟
- 单条件场景、优先级、默认场景、场景启停
- `main(request, mock)` JavaScript 动态响应
- 草稿与真实发布隔离、乐观锁、原子发布、历史查看与回滚
- 真实 HTTP 入口、服务端调试、调用日志、操作记录
- 开发与生产统一使用 MySQL
- 独立脚本进程、超时销毁、队列限制、内存上限和进程补充
- 仅部署业务应用及 runner 的 Linux Docker Compose、离线镜像准备

首版免登录，不包含用户/权限体系，不依赖 pack-mom，也不兼容 Apifox 脚本 API。

## 项目结构

```text
backend/       Java 管理与 Mock HTTP 服务
runner/        GraalJS 工作进程及独立监督服务
frontend/      Vue 3 + Vite 前端源码
prototype/     已审核的交互原型，保留原样
scripts/       构建、启动、验收、离线打包及历史备份脚本
deploy/        业务镜像构建、环境变量及现有 Nginx 代理参考片段
docs/          需求、开发说明、部署教程、验收记录
release/       已构建的本机启动产物，不含 Docker 镜像
compose.yml    Linux 部署入口
```

## 本机快速体验

需要 JDK 21。系统默认 Java 如果是 8，请先在当前 PowerShell 指定 JDK 21，不必更改系统配置。

```powershell
cd G:\project\mock-platform
$env:JAVA_HOME='E:\new-software\jdk\jdk21'
$env:PATH="$env:JAVA_HOME\bin;$env:PATH"
```

先按 [MySQL 本机配置](docs/MySQL本机配置.md) 创建数据库和账号，并在启动后端的窗口设置 `DB_PASSWORD`（必填），需要时设置 `DB_URL`、`DB_USER`。开发与生产均使用 MySQL。

先从源码重新构建，再打开两个 PowerShell 窗口，在同一目录分别运行：

```powershell
# 窗口一：脚本执行服务
java -cp 'runner/target/classes;runner/target/lib/*' dev.mocklab.runner.Supervisor tcp:127.0.0.1:9091
```

```powershell
# 窗口二：Web 与 HTTP 服务
java -jar backend/target/backend-1.0.0.jar --server.address=127.0.0.1
```

浏览器打开 `http://127.0.0.1:16080`。首次可点击“创建演示系统”，或手动创建系统和接口。数据保存在 MySQL 中，旧 `data/` 中的 H2 数据不会自动迁移。

本机模式用于开发验收。正式部署使用下方 Docker 方案的无网络执行容器，不能将本机执行进程当成具有容器隔离的生产环境。

## 从源码构建

需要 JDK 21、Maven 3.8+、Node.js 22.12+ 或 24。

```powershell
.\scripts\build.ps1
```

构建后使用 `scripts/start-runner.ps1` 和 `scripts/start-app.ps1`，或直接启动 `backend/target/backend-1.0.0.jar` 与 `runner/target` 中的运行依赖。

前端编译结果被打包进后端 JAR，运行服务器不需要 Node.js、Maven、npm 或公网资源。

## 正式部署

默认 `compose.yml` 只部署 `app`（后端及已打包前端）和 `runner`，直接连接现有 MySQL 5.7，由已有 Nginx 代理，不再部署数据库或 Nginx 容器。

后端默认映射 `127.0.0.1:16080`，容器内部端口为 `8080`。同机 Nginx 代理到该地址；远程 Nginx 使用业务服务器内网 IP，并相应设置 `APP_BIND`。

先根据 `deploy/env.example` 准备 `.env`，配置 `DB_URL`、`DB_USER`、`DB_PASSWORD` 和实际 Nginx 入口 `MOCK_BASE_URL`，再运行 `docker compose up -d --build`。不需要数据库 root 密码。

完整步骤见 [Linux Docker 部署教程](docs/部署教程.md)。`compose.external-mysql.yml` 同步为相同的两服务配置，仅为保留已有命令兼容，后续统一使用默认 `compose.yml`。

## 脚本示例

```javascript
function main(request, mock) {
  return {
    status: 200,
    body: {
      code: "0000",
      data: request.body,
      requestId: mock.uuid()
    }
  };
}
```

字符串 body 原样返回，对象/数组序列化为 JSON，null 返回 `null` 文本，空字符串返回空正文。脚本不能访问文件、网络、数据库、宿主 Java 类或安装 npm 包。

## 测试

```powershell
mvn -B test
# 对可丢弃的本机验收实例执行，会新建并归档一个测试系统
python scripts/integration_test.py
```

测试脚本不删除已有系统或调用记录。详情及未验证范围见 [验收记录](docs/验收记录.md)。
