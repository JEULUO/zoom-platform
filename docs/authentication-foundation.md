# 认证与权限基础设计

## 会话模型

- 访问令牌使用 HS256 JWT，默认有效期 15 分钟，只保存在前端内存中。
- 刷新令牌是 256 位随机值，默认有效期 7 天，只通过 `HttpOnly`、`SameSite=Strict` Cookie 传递。
- Redis 的键只保存刷新令牌 SHA-256 摘要，不保存浏览器持有的原始令牌。
- 每次刷新都会原子消费旧刷新令牌并签发一组新令牌，旧令牌不能重放。
- 注销会删除刷新令牌，并按 JWT `jti` 将当前访问令牌加入 Redis 撤销集合直至过期。
- 前端刷新页面时通过刷新 Cookie 恢复会话，不使用 `localStorage` 或 `sessionStorage` 保存令牌。

## 接口

| 方法 | 路径 | 认证要求 | 用途 |
| --- | --- | --- | --- |
| `POST` | `/api/v1/auth/login` | 公开 | 校验用户名和密码并创建会话 |
| `POST` | `/api/v1/auth/refresh` | 刷新 Cookie | 轮换刷新令牌并返回新访问令牌 |
| `POST` | `/api/v1/auth/logout` | Bearer JWT | 撤销当前访问令牌和刷新令牌 |
| `GET` | `/api/v1/auth/me` | Bearer JWT | 从数据库读取最新用户权限上下文 |

JWT 和登录响应中的用户上下文包括角色、权限点、校区 ID 和合并后的数据范围。Spring Security 将角色转换为 `ROLE_<角色编码>` 权限，将权限点编码直接转换为方法级授权 authority。

## 账户保护

- 用户名统一去除首尾空格并按小写查询。
- 连续 5 次密码错误后将账户锁定 15 分钟。
- 锁定到期后的下一次登录会自动恢复账户，再继续验证密码。
- 登录、刷新和注销的成功或失败结果写入 `sys_login_audit`，记录请求 ID、IP 和 User-Agent。
- 未认证请求返回 JSON `401`，权限不足返回 JSON `403`，已撤销访问令牌返回 JSON `401`。

## 本地管理员

`local` Profile 会幂等创建一个 `SUPER_ADMIN` 用户并确保角色关联存在：

- 用户名：`admin`
- 默认密码：`ZoomDev@2026!`
- 环境变量：`BOOTSTRAP_ADMIN_ENABLED`、`BOOTSTRAP_ADMIN_USERNAME`、`BOOTSTRAP_ADMIN_PASSWORD`

该默认密码只用于本地开发。非 `local` Profile 不会创建管理员，且应用必须通过 `JWT_SECRET` 提供至少 32 个字符的随机密钥。生产环境还应启用 HTTPS，并保持 `SECURE_AUTH_COOKIES=true`。

## 验证范围

后端集成测试使用 H2 执行认证数据查询，并使用 Redis 7.4 Testcontainer 验证刷新轮换和 JWT 撤销；MySQL 8.4 Testcontainer 单独验证迁移兼容性。前端测试覆盖登录、Cookie 会话恢复、失败状态、注销和登录页反馈。
