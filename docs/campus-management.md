# 校区管理模块

## 功能边界

校区是组织和业务数据隔离的基础维度。当前模块负责校区档案、查询筛选和启停状态，不提供物理删除，也不在此阶段处理校区下的用户分配。

## 接口

| 方法 | 路径 | 权限 | 用途 |
| --- | --- | --- | --- |
| `GET` | `/api/v1/campuses` | `campus.read` | 分页查询校区 |
| `GET` | `/api/v1/campuses/{id}` | `campus.read` | 查询单个校区详情 |
| `POST` | `/api/v1/campuses` | `campus.manage` | 新建校区，仅 `ALL` 数据范围可用 |
| `PUT` | `/api/v1/campuses/{id}` | `campus.manage` | 更新校区档案 |
| `PATCH` | `/api/v1/campuses/{id}/status` | `campus.manage` | 启用或停用校区 |

列表接口支持以下参数：

| 参数 | 默认值 | 约束 | 说明 |
| --- | --- | --- | --- |
| `keyword` | 空 | 可选 | 匹配校区编码、名称或城市 |
| `status` | 空 | `ACTIVE` 或 `INACTIVE` | 按状态筛选 |
| `page` | `1` | `1` 至 `1000000` | 页码 |
| `pageSize` | `20` | `1` 至 `100` | 每页记录数 |

## 权限与数据范围

- `ALL`：可读取和管理所有校区，也可创建新校区。
- `ASSIGNED_CAMPUSES`：只可读取和管理 JWT `campusIds` 中的校区，不可创建新校区。
- `SELF`：当前没有可管理校区，列表返回空结果。
- 无权访问的校区详情和写请求返回 `404 CAMPUS_NOT_FOUND`，避免泄露记录是否存在。

接口权限点与数据范围同时生效。只有 `campus.manage` 而没有 `campus.read` 的用户不能调用查询接口。

## 数据规则

- 校区编码创建时转换为大写，创建后不可修改，并受数据库唯一约束保护。
- 国家代码转换为两位大写字母，联系邮箱转换为小写。
- 时区必须是有效 IANA Zone ID，例如 `Europe/London` 或 `Asia/Shanghai`。
- 空白可选字段统一保存为 `NULL`。
- 写请求携带当前 `version`；更新成功后版本加一，旧版本请求返回 `409 CAMPUS_VERSION_CONFLICT`。
- 停用校区只改变 `status`，不删除历史数据。

## 错误响应

| HTTP 状态 | 错误码 | 场景 |
| --- | --- | --- |
| `400` | `VALIDATION_FAILED` | 字段格式、分页或枚举参数无效 |
| `400` | `INVALID_TIMEZONE` | 时区不是有效 IANA Zone ID |
| `403` | Spring Security 权限错误 | 缺少权限点，或非 `ALL` 范围尝试创建校区 |
| `404` | `CAMPUS_NOT_FOUND` | 记录不存在或超出数据范围 |
| `409` | `CAMPUS_CODE_EXISTS` | 校区编码重复 |
| `409` | `CAMPUS_VERSION_CONFLICT` | 乐观锁版本冲突 |

## 操作审计

创建、资料更新和状态变更分别写入 `CAMPUS_CREATE`、`CAMPUS_UPDATE`、`CAMPUS_STATUS_CHANGE`。审计记录包含用户、资源 ID、请求路径、请求 ID、IP、结果和 JSON 明细，存储在 `sys_operation_audit`。

## 前端交互

- `/campuses` 路由要求 `campus.read`，没有权限时返回系统概览。
- `ALL` 范围管理员可看到新建入口；具备 `campus.manage` 的分配范围管理员可编辑已分配校区。
- 桌面端使用可扫描表格，移动端切换为校区卡片。
- 启停操作必须经过确认弹窗；创建和编辑成功后自动刷新当前列表。

## 验证

后端 `CampusManagementTest` 覆盖完整生命周期、权限、数据范围、参数校验、重复编码、无效时区、乐观锁和审计。前端 store 与页面测试覆盖请求契约、错误状态、筛选、新建、状态确认及创建入口的数据范围限制。
