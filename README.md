# zoom-platform

面向多校区教育培训机构的统一管理平台。当前已完成工程、数据库、认证权限和校区管理基础模块。

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

1. 可将 `.env.example` 复制为 `.env` 并设置自己的开发密码；没有 `.env` 时使用 Compose 和 `local` Profile 的开发默认值。
2. 启动基础设施：`docker compose -f deploy/compose.dev.yml up -d`。
3. 启动后端：进入 `server` 后执行 `.\mvnw.cmd spring-boot:run`。
4. 安装并启动前端：`pnpm install`、`pnpm dev:web`。
5. 打开 `http://localhost:5173`，使用本地管理员登录。

> 后端要求 `JAVA_HOME` 指向 JDK 17 或更高版本；执行 `.\mvnw.cmd -version` 可确认 Maven 实际使用的 Java 版本。
> 项目 MySQL 默认映射到宿主机 `13306`，避免与本机 `MySQL80` 的 `3306` 端口冲突。

当前机器的 Docker CLI 位于 D 盘。如果终端中的 `docker` 命令尚未更新 PATH，可以直接执行：

```powershell
& 'D:\Docker\Docker\resources\bin\docker.exe' compose -f deploy\compose.dev.yml up -d
```

本地默认账户仅用于开发环境：

| 用途 | 账户 | 默认密码 |
| --- | --- | --- |
| 平台管理员 | `admin` | `ZoomDev@2026!` |
| MySQL 应用连接 | `zoom` | `zoom_dev_password` |
| MySQL root | `root` | `zoom_root_password` |

如创建了 `.env`，以其中的 `BOOTSTRAP_ADMIN_PASSWORD`、`MYSQL_PASSWORD` 和 `MYSQL_ROOT_PASSWORD` 为准。

### Navicat 连接

- 连接类型：MySQL
- 主机：`127.0.0.1`
- 端口：`13306`
- 用户名：`zoom`
- 密码：`zoom_dev_password`，或 `.env` 中的 `MYSQL_PASSWORD`
- 默认数据库：`zoom_platform`

先确认 `zoom-platform-mysql` 容器状态为 `healthy`，再在 Navicat 中测试连接。MySQL 容器内部仍使用 `3306`，Navicat 必须填写宿主机映射端口 `13306`。

## 数据库迁移

- 后端启动时由 Flyway 自动执行通用迁移。
- 默认 `local` Profile 会额外写入四个示例校区，生产环境不加载本地种子数据。
- 表关系、数据范围与迁移约定见 [`docs/database-foundation.md`](docs/database-foundation.md)。
- 登录、令牌、账户锁定与权限上下文见 [`docs/authentication-foundation.md`](docs/authentication-foundation.md)。
- 校区接口、数据范围、乐观锁和操作审计见 [`docs/campus-management.md`](docs/campus-management.md)。

## 当前功能

- 管理员登录、刷新会话、注销、账户锁定和 JWT 撤销。
- 校区分页查询，可按名称、编码、城市和启停状态筛选。
- 校区新建、编辑、启用和停用，不提供物理删除。
- `campus.read`、`campus.manage` 权限与 `ALL`、`ASSIGNED_CAMPUSES` 数据范围联合校验。
- 校区写操作使用 `version` 乐观锁，并写入 `sys_operation_audit`。
- 桌面表格与移动端卡片布局，共用响应式管理后台导航。

## 质量检查

```powershell
cd server
.\mvnw.cmd test
cd ..
pnpm type-check:web
pnpm test:web
pnpm build:web
```
