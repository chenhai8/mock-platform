# MockLab 内网 Mock 平台

独立的 Java/Spring Boot 单体应用，用于模拟 ERP、WMS 等第三方系统接口。通过网页配置方法、路径和返回规则，发布后即可被内网服务器直接通过 HTTP 调用。

![MockLab 管理页面](docs/images/运行截图.png)

项目当前不提供公开演示站点，可按下方说明在受控环境中自行部署。

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

首版免登录，不包含用户/权限体系，也不兼容 Apifox 脚本 API。

> 安全边界：本项目面向受控内网。管理页面、`/api` 和 `/actuator` 不应无访问控制地暴露到公网；请通过现有 Nginx、网络访问策略或认证网关限制访问。脚本隔离不等于绝对安全，不应将此实例作为对任意匿名用户开放的脚本执行服务。

## 项目结构

```text
backend/       Java 管理与 Mock HTTP 服务
runner/        GraalJS 工作进程及独立监督服务
frontend/      Vue 3 + Vite 前端源码
scripts/       构建、启动、测试和离线部署脚本
deploy/        业务镜像构建、环境变量及现有 Nginx 代理参考片段
docs/          开发、数据库配置和部署说明
dist/          前端构建生成的静态文件，不提交 Git
compose.yml    Linux 部署入口，仅管理 app 和 runner
```

## 本机快速体验

需要 JDK 21、Maven 3.8+、Node.js 22.12+ 或 24，以及已准备好的 MySQL 数据库。以下命令在项目根目录执行，使用 PowerShell；确认 `java -version` 输出 Java 21。

```powershell
mvn -B package
npm --prefix frontend ci
```

先按 [MySQL 本机配置](docs/MySQL本机配置.md) 创建数据库和账号，并在启动后端的窗口设置 `DB_PASSWORD`（必填），需要时设置 `DB_URL`、`DB_USER`。开发与生产均使用 MySQL。

先完成上述依赖安装和编译，再打开三个 PowerShell 窗口，在项目根目录分别运行：

```powershell
# 窗口一：脚本执行服务
java -cp 'runner/target/classes;runner/target/lib/*' dev.mocklab.runner.Supervisor tcp:127.0.0.1:9091
```

```powershell
# 窗口二：Web 与 HTTP 服务
java -jar backend/target/backend-1.0.0.jar --server.address=127.0.0.1
```

```powershell
# 窗口三：前端开发服务器，代理管理请求到本机后端
$env:VITE_BACKEND_URL='http://127.0.0.1:16080'
npm --prefix frontend run dev
```

浏览器打开 Vite 输出的地址，通常为 `http://127.0.0.1:5173`，不是后端的 16080。首次可点击“创建演示系统”，或手动创建系统和接口。数据保存在 MySQL 中，旧 `data/` 中的 H2 数据不会自动迁移。

本机模式用于开发验收。正式部署使用下方 Docker 方案的无网络执行容器，不能将本机执行进程当成具有容器隔离的生产环境。

## 从源码构建

需要 JDK 21、Maven 3.8+、Node.js 22.12+ 或 24。

```powershell
.\scripts\build.ps1
```

构建后使用 `scripts/start-runner.ps1` 和 `scripts/start-app.ps1`，或直接启动 `backend/target/backend-1.0.0.jar` 与 `runner/target` 中的运行依赖。

前端编译结果输出到根目录 `dist/`，独立部署到现有 Nginx，不再打包进后端 JAR。仅运行已构建镜像和静态文件的服务器不需要 Node.js、Maven 或 npm。

## 前端访问后端的地址

本机前后端分别启动时，在 `frontend/.env.development` 设置 `VITE_BACKEND_URL=http://127.0.0.1:16080`，然后运行 `npm run dev`。管理请求的开发代理与页面展示、复制的 Mock 链接共用此值。后端修改端口时只需同步这里并重启前端开发服务。

正式构建可通过 `frontend/.env.production` 配置 `VITE_BACKEND_URL`，留空时管理请求和复制链接均使用浏览器当前访问的 Nginx 地址，无需在后端或 Docker 环境变量中配置地址。环境文件不会自动提供身份认证，不要在任何 `VITE_` 变量中放入秘密；这些值会进入浏览器端产物。修改地址后只需重新构建、部署前端，不需要重建 app 镜像。

## 正式部署

默认 `compose.yml` 只部署 `app`（后端 HTTP 服务）和 `runner`，直接连接现有 MySQL。前端 `dist/` 放入现有 Nginx 静态目录，由 Nginx 反代 `/api`、`/m/`；不创建数据库或 Nginx 容器，也不需要前端镜像。

后端默认映射 `127.0.0.1:16080`，容器内部端口为 `8080`。同机 Nginx 代理到该地址；远程 Nginx 使用业务服务器内网 IP，并相应设置 `APP_BIND`。

### 在线构建部署

在 Linux Docker 环境中，根据 `deploy/env.example` 准备 `.env`，配置 `DB_URL`、`DB_USER`、`DB_PASSWORD`，然后执行：

```sh
# 已有 .env 不覆盖，数据库需预先创建并授权
if [ ! -e .env ]; then cp deploy/env.example .env; fi
# 编辑 .env 后再运行下面命令
docker compose config --quiet
docker compose up -d --build
curl -f http://127.0.0.1:16080/actuator/health
```

前端单独执行 `npm --prefix frontend ci` 和 `npm --prefix frontend run build`，将 `dist/` 内容部署到 Nginx。前端可以在开发机或 GitHub 构建，运行服务器不必安装 Node。`deploy/nginx.conf` 是合入现有站点的参考片段，不能直接覆盖 Nginx 主配置。

### GitHub Actions 构建与离线部署

进入仓库 **Actions → Build Offline Package → Run workflow**，手动构建 `linux/amd64` 的两个镜像和前端静态包。成功后下载 `mocklab-offline-linux-amd64` artifact；当前保留 7 天，ARM64 服务器不能直接使用此镜像包。

将 ZIP 和 `scripts/prepare-offline.sh` 单独上传到同一部署目录：

```sh
bash prepare-offline.sh
# 首次部署填写 .env 中数据库参数，再启动
docker compose config --quiet
docker compose up -d --no-build --pull never
docker compose ps
curl -f http://127.0.0.1:16080/actuator/health
```

脚本校验归档并导入镜像；已有 `.env`、`compose.yml` 不覆盖，成功后 ZIP 改为 `.zip.bak`，最新 `mocklab-dist.tar.gz` 放在同级目录。脚本不自动启动容器。

前端可手动部署，或将 `scripts/deploy-frontend.sh` 放到前端包同级目录，修改顶部 `NGINX_HTML_DIR` 后执行。该脚本会在确认后清理专用 `mocklab` 目录再复制新文件，存在短暂停机且不会自动备份，请先阅读脚本并保留旧版本。不要指向共享站点目录。

完整步骤见 [Linux Docker 部署教程](docs/部署教程.md)。浏览器访问 Nginx 的页面入口，不是仅提供后端接口的 16080。

### Release 离线包

维护者可将一次成功 Actions 运行下载的 ZIP 作为 Release 附件长期发布，保留文件名 `mocklab-offline-linux-amd64.zip` 以兼容准备脚本。Release 标签应指向该次构建的实际提交，不能将旧构建包标记为不一致的新源码版本。ZIP 内包含镜像包、前端包、配置包和 `SHA256SUMS`；发布前还需完成运行验证与第三方许可证核对。当前工作流不会自动创建 Release。

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

测试脚本不删除已有系统或调用记录。仅对独立测试实例运行集成测试；测试通过不代替目标服务器的部署和安全验证。

## 许可证

项目自有代码采用 [MIT License](LICENSE)。第三方依赖继续适用各自许可证，尤其 MySQL Connector/J 的 GPLv2 + FOSS 例外及 GraalJS/ICU、前端内嵌依赖的通知要求。详见 [第三方依赖与分发注意事项](THIRD_PARTY_NOTICES.md)；项目 MIT 许可不等于整个镜像及其全部组件都采用 MIT。

## 参与项目

目前暂不通过 Issues 受理反馈，也没有公开演示站点。欢迎 Fork 后提交范围明确的 Pull Request，说明用途、验证方法及对部署方式的影响；不要提交数据库凭据、真实业务报文或内部系统信息。发现潜在安全问题时，不要通过公开 PR 附带真实秘密或可利用细节，应先与维护者建立私下沟通渠道。
