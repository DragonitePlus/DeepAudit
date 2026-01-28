根据您的要求，我将把 `backend` 部分完全独立出来，并且**不依赖** `deep-audit-core`。`backend` 将作为一个独立的 Spring Boot 工程，专注于读取审计数据和展示。

### 1. 创建 `deep-audit-backend` 模块
在根目录下创建一个新的 Maven 模块 `deep-audit-backend`。
- **定位**: 独立的后端服务，用于管理台展示。
- **依赖**: 仅引入 `spring-boot-starter-web`, `mybatis-plus`, `mysql-connector`, `druid` 等基础组件，**绝不引入** `deep-audit-core`。

### 2. 代码拆分与迁移 (Split & Move)
我们将按照“完全解耦”的原则进行文件操作：

#### A. 移动 (Move) - 仅属于后端的部分
这些文件将从 `core` 中**移除**并放入 `backend`：
- **Controllers**: 将 `edu.hnu.deepaudit.controller` 包下的所有文件（`AuditLogController`, `RiskController` 等）移动到 `backend`。
- **Spring 配置**: 将 `edu.hnu.deepaudit.config` 下的 Spring Bean 配置移动到 `backend`（因为 `core` 作为插件库主要依靠工厂模式手动创建，不依赖 Spring 容器）：
    - `DataSourceConfig.java` (数据源配置)
    - `MyBatisSysConfig.java` (MyBatis 系统表配置)
    - `RedisConfig.java` (Redis 配置)
    - `AuditConfig.java` (异步线程池等)
- **启动类**: 将 `DeepAuditApplication.java` 移动到 `backend`。

#### B. 复制 (Copy) - 需要共用的部分
由于不能依赖 `core`，我们需要将数据访问层（DAO）复制一份到 `backend`，以便后端能读取数据库：
- **实体类 (Models)**: 将 `edu.hnu.deepaudit.model` 下的系统表实体（`SysAuditLog`, `SysUserRiskProfile`, `SysSensitiveTable` 等）复制到 `backend`。
- **Mappers**: 将 `edu.hnu.deepaudit.mapper.sys` 下的 Mapper 接口复制到 `backend`。
- **属性配置**: 将 `AuditProperties.java` 和 `RiskProperties.java` 复制到 `backend`，以便 Controller 使用。
- **资源文件**: 将 `application.properties` 复制到 `backend`。

### 3. 清理 `deep-audit-core`
- 删除 `controller` 包。
- 删除 `DeepAuditApplication`。
- 移除 `pom.xml` 中的 `spring-boot-starter-web` 依赖。
- 保留核心业务逻辑（`service`, `interception`, `analysis` 等）供插件使用。

### 4. 调整与适配
- **ExternalAuditController**: 该控制器原本依赖核心的 `AuditSink` 和 `CoreAuditService` 进行复杂分析。在 `backend` 中，我们将简化其实现，使其仅负责接收数据并存储（使用复制过来的 Mapper），去除对核心复杂逻辑的依赖。
- **包路径**: `backend` 将保持相同的包结构 `edu.hnu.deepaudit`，以减少代码修改量，但它在物理上是完全隔离的项目。

### 5. 验证
- 确保 `deep-audit-backend` 可以独立启动，连接数据库，并提供 REST 接口。
- 确保 `deep-audit-core` 编译通过，作为纯净的类库供插件使用。

这个方案既满足了“后端独立且不依赖 Core”的要求，又保留了插件所需的核心功能。