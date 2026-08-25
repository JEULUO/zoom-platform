# zoom-platform

面向多校区教育培训机构的统一管理平台。项目当前处于工程初始化阶段。

## 技术栈

- 后端：Java 17、Spring Boot 3.5、MyBatis-Plus、Flyway、Maven Wrapper
- 前端：Vue 3、TypeScript、Vite、Element Plus、Pinia、Vue Router
- 基础设施：MySQL 8.4、Redis 7.4、Docker Compose

## 目录结构

```text
zoom-platform/
|- server/   # Spring Boot 后端
|- web/      # Vue 3 响应式 Web
`- deploy/   # 本地基础设施与后续部署配置
```

## 环境要求

- JDK 17，且 `JAVA_HOME` 指向该 JDK
- Node.js 22 与 pnpm 11
- Docker Desktop；Windows 首次启用 WSL 2 后需要重启系统
- 本机 Docker 程序与 Linux 容器磁盘位于 `D:\Docker`，不要迁回 C 盘

## 本地开发

1. 将 `.env.example` 复制为 `.env`，按需修改本地密码和端口。
2. 启动基础设施：`docker compose --env-file .env -f deploy/compose.dev.yml up -d`。
3. 启动后端：`cd server && .\mvnw.cmd spring-boot:run`。
4. 安装并启动前端：`pnpm install && pnpm dev:web`。
5. 打开 `http://localhost:5173`，后端状态接口为 `http://localhost:8080/api/v1/system/status`。

> 后端要求 `JAVA_HOME` 指向 JDK 17 或更高版本；执行 `.\mvnw.cmd -version` 可确认 Maven 实际使用的 Java 版本。
> 项目 MySQL 默认映射到宿主机 `13306`，避免与本机 `MySQL80` 的 `3306` 端口冲突。

## 数据库迁移

- 后端启动时由 Flyway 自动执行通用迁移。
- 默认 `local` Profile 会额外写入四个示例校区，生产环境不加载本地种子数据。
- 表关系、数据范围与迁移约定见 [`docs/database-foundation.md`](docs/database-foundation.md)。

## 质量检查

```powershell
cd server
.\mvnw.cmd test
cd ..
pnpm type-check:web
pnpm test:web
pnpm build:web
```
