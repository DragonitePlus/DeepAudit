# DeepAudit 用户手册 (User Manual)

## 1. 简介
DeepAudit 是一个集成在 ShardingSphere 中的智能数据库审计与风控插件。它能够实时拦截 SQL，分析用户行为，检测敏感数据泄露，并利用 AI 模型识别异常访问。

## 2. 环境要求 (Prerequisites)
- **Java**: JDK 11 或更高版本 (DeepAudit 使用 Java 11 HttpClient 及 ONNX Runtime)。
- **ShardingSphere-Proxy**: 5.4.1 版本。
- **Redis**: 必须可用 (用于风控状态机)。
- **MySQL**: 必须可用 (用于审计日志持久化)。

## 3. 构建 (Build)
在项目根目录下执行 Maven 命令：

```bash
mvn clean package -DskipTests
```

构建成功后，在 `deep-audit-core/target` 目录下会生成 `deep-audit-core-0.0.1-SNAPSHOT.jar`。
**注意**: 该 Jar 包已包含所需依赖 (Redis, Mybatis, ONNX Runtime 等)，体积较大 (Fat Jar)，可直接放入 ShardingSphere 使用。

## 4. 安装 (Installation)
1. 将生成的 JAR 包复制到 ShardingSphere Proxy 的 `ext-lib` 目录中：

```bash
cp deep-audit-core/target/deep-audit-core-0.0.1-SNAPSHOT.jar /path/to/shardingsphere-proxy/ext-lib/
```

2. **AI 模型安装**:
   - 确保 `D:/Code/DeepAudit/models/` 目录存在。
   - 运行 Python 脚本生成模型文件：`python ai_module/train_isolation_forest.py`。
   - 生成的 `deep_audit_iso_forest.onnx` 文件会被自动保存到 Python 脚本当前目录，请将其移动到 `D:/Code/DeepAudit/models/` (或者修改 Service 代码中的路径)。

## 5. 配置 (Configuration)
插件使用 classpath 下的 `deepaudit.properties` 进行配置，默认配置已打包在 jar 中。如需修改，可替换 Jar 包中的 `deepaudit.properties` 文件。

## 6. 验证 (Verification)
启动 ShardingSphere Proxy，观察日志。
- 看到 `DeepAuditFactory initialized successfully` 表示插件加载成功。
- 看到 `ONNX Model loaded successfully` 表示 AI 模型加载成功。

执行带有用户身份注释的 SQL：
```sql
/* user_id:admin_001 */ SELECT * FROM sys_user;
```
如果 SQL 执行被记录到 `sys_audit_log` 表，且日志中无报错，说明插件工作正常。
如果 AI 模型未找到，插件会输出警告并自动降级（不执行 AI 检测），不影响主流程。
