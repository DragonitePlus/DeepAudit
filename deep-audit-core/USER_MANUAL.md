# DeepAudit 用户手册 (User Manual)

## 1. 简介
DeepAudit 是一个集成在 ShardingSphere 中的智能数据库审计与风控插件。它能够实时拦截 SQL，分析用户行为，检测敏感数据泄露，并利用 AI 模型识别异常访问。

## 2. 构建 (Build)
在项目根目录下执行 Maven 命令：

```bash
mvn clean package -DskipTests
```

构建成功后，在 `deep-audit-core/target` 目录下会生成 `deep-audit-core-0.0.1-SNAPSHOT.jar`。

## 3. 安装 (Installation)
将生成的 JAR 包复制到 ShardingSphere Proxy 的 `ext-lib` 目录中：

```bash
cp deep-audit-core/target/deep-audit-core-0.0.1-SNAPSHOT.jar /path/to/shardingsphere-proxy/ext-lib/
```

## 4. 配置 (Configuration)
插件运行需要依赖 MySQL 和 Redis。
- **MySQL**: 存储审计日志 (`sys_audit_log`) 和风控规则。
- **Redis**: 存储用户实时风险分和状态机窗口数据。
- **AI Service**: 需要启动 Python AI 服务端。

目前连接配置硬编码在 `RiskStateMachineFactory` 和 `AuditPersistenceServiceFactory` 中（开发环境默认 localhost），生产环境建议修改代码读取外部配置文件。

## 5. 验证 (Verification)
启动 ShardingSphere Proxy，观察日志。如果看到 DeepAudit 相关的初始化日志，说明插件加载成功。

执行带有用户身份注释的 SQL：
```sql
/* user_id:admin_001 */ SELECT * FROM sys_user;
```
该操作将被记录到 `sys_audit_log` 表中。
