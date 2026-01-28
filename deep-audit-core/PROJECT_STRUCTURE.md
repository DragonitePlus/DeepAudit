# DeepAudit Core 项目结构说明

## 1. 概述
`deep-audit-core` 是 DeepAudit 项目的核心插件模块，旨在作为 ShardingSphere Proxy 的 SPI 插件运行。它实现了基于 SQL 的审计、DLP 敏感数据扫描、RAdAC 动态风控以及 AI 异常检测功能。

## 2. 核心组件
- **Proxy Hook**: `edu.hnu.deepaudit.proxy.DeepAuditHook`
    - ShardingSphere 的 `SQLExecutionHook` 实现。
    - 拦截 SQL 执行前（`start`）进行身份解析和风控阻断。
    - 拦截 SQL 执行后（`finishSuccess/finishFailure`）进行日志审计、DLP 分析和 AI 异步检测。

- **Risk Control**: `edu.hnu.deepaudit.control.RiskStateMachine`
    - 基于 Redis Lua 脚本的 RAdAC 状态机。
    - 负责计算用户当前的风险状态（NORMAL, WARNING, BLOCK）。
    - 这是一个纯 POJO，通过 Factory 注入依赖。

- **AI Service**: `edu.hnu.deepaudit.service.AnomalyDetectionService`
    - 使用 Java HttpClient 异步调用外部 AI 服务（Python）。
    - 将 SQL 特征发送给 AI 模型，若发现异常则更新 Redis 风险分。

- **Persistence**: `edu.hnu.deepaudit.persistence.AuditPersistenceService`
    - 负责将审计日志写入 MySQL 数据库。

## 3. 依赖关系
- **ShardingSphere**: `5.4.1` (Provided scope)
- **Spring Data Redis**: 仅用于复用 `StringRedisTemplate` 和 `Jedis` 连接逻辑，去除了自动配置。
- **MyBatis Plus**: 仅用于 Mapper 接口定义。
- **MySQL Connector**: 用于审计日志持久化。

## 4. 为什么没有 Controller?
本模块运行在 ShardingSphere Proxy 内部，不提供 REST API。所有管理和查询功能由 `deep-audit-backend` 工程提供。
