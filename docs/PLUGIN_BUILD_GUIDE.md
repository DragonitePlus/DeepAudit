# DeepAudit ShardingSphere Proxy Plugin 构建指南

## 1. 简介
本插件 (`deep-audit-shardingsphere-plugin`) 是 DeepAudit 系统的核心风控模块的 ShardingSphere Proxy 适配版。它作为一个 `ExecutorHook` 运行在 Proxy 内部，拦截所有 SQL 请求，解析其中的 `/* user_id:xxx */` Hint，并执行实时风控检查。

## 2. 环境要求
- JDK 21+
- Maven 3.6+
- Apache ShardingSphere-Proxy 5.4.1+

## 3. 构建步骤

### 3.1 编译整个项目
在项目根目录 (`D:\Code\DeepAudit`) 执行：
```bash
mvn clean package -DskipTests
```

### 3.2 获取插件及依赖 Jar 包
构建成功后，我们需要获取插件本身以及核心依赖库。

**1. 插件本体**
在 `deep-audit-shardingsphere-plugin/target` 目录下：
- `deep-audit-shardingsphere-plugin-1.0.0.jar`

**2. 核心逻辑库**
在 `deep-audit-core/target` 目录下：
- `deep-audit-core-0.0.1-SNAPSHOT.jar`

**3. 第三方依赖**
由于目前的打包方式不包含第三方依赖，您可能还需要手动下载以下 Jar 包（如果 Proxy 环境中不存在）：
- `jedis-5.1.0.jar` (Redis 客户端)
- `mysql-connector-j-8.3.0.jar` (MySQL 驱动)

## 4. 部署指南

### 4.1 准备 ShardingSphere Proxy
下载并解压 ShardingSphere Proxy 5.4.1。

### 4.2 安装插件
将构建好的插件 Jar 包复制到 Proxy 的 `ext-lib` 目录：
```bash
cp deep-audit-shardingsphere-plugin/target/deep-audit-shardingsphere-plugin-1.0.0.jar /path/to/shardingsphere-proxy/ext-lib/
```

### 4.3 依赖补充 (关键步骤)
由于我们不再使用 `maven-shade-plugin` 打包 "Fat Jar"，必须手动将所有运行时依赖复制到 Proxy 的 `ext-lib` 目录。

请务必补充以下 Jar 包：

1.  **DeepAudit Core (必须)**:
    ```bash
    cp deep-audit-core/target/deep-audit-core-0.0.1-SNAPSHOT.jar /path/to/shardingsphere-proxy/ext-lib/
    ```

2.  **Redis 客户端 (必须)**:
    你需要确保 `jedis` 及其依赖存在。通常需要复制：
    - `jedis-5.1.0.jar`
    - `commons-pool2-2.x.jar` (Proxy 可能自带，如无则需补充)
    - `gson-2.x.jar` 或 `json-20xx.jar` (Jedis 依赖，Proxy 通常自带)

3.  **MySQL 驱动 (如果 Proxy 未自带)**:
    - `mysql-connector-j-8.3.0.jar`

**总结：ext-lib 目录下最终应至少包含：**
- `deep-audit-shardingsphere-plugin-1.0.0.jar`
- `deep-audit-core-0.0.1-SNAPSHOT.jar`
- `jedis-5.1.0.jar`
- `mysql-connector-j-8.3.0.jar`

### 4.4 启动 Proxy
执行 `bin/start.sh` (Linux) 或 `bin/start.bat` (Windows)。

## 5. 验证
连接到 Proxy 执行带 Hint 的 SQL：
```sql
/* user_id:test_user */ SELECT * FROM sys_user;
```
如果 `test_user` 处于 `BLOCK` 状态，Proxy 应返回错误信息：
`DeepAudit Risk Control: Access Denied for user test_user`
