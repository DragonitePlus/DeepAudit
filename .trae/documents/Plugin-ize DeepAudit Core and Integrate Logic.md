Based on your requirements, I have developed a comprehensive plan to "plugin-ize" `deep-audit-core` and integrate its functionality into `deep-audit-shardingsphere-plugin`.

### Objective
Transform `deep-audit-core` into a pure Java library (non-Spring Boot dependent) that provides core auditing logic (Risk Control, Anomaly Detection, DLP, Persistence). The `deep-audit-shardingsphere-plugin` will consume this library via Factory patterns, ensuring complete decoupling from the `backend`.

### Current Functionalities & Migration Plan

| Functionality | Current Logic (Core) | Adaptation Strategy for Plugin |
| :--- | :--- | :--- |
| **SQL Interception** | `SqlAuditInterceptor` (MyBatis) | **Removed**. Plugin uses ShardingSphere's `SQLExecutionHook` natively. |
| **User Identity** | `UserContext` / `DeepAuditHook` | **Unified**. `DeepAuditHook` extracts User ID from SQL comments (e.g., `/* user_id:123 */`). |
| **Risk Control (RAdAC)** | `RiskStateMachine` (Spring Bean) | **Refactor**. Remove `@Autowired`. Use `RiskStateMachineFactory` to inject Redis/DB dependencies manually. |
| **Anomaly Detection** | `AnomalyDetectionService` (RestTemplate) | **Refactor**. Remove `RestTemplate` bean. Use standard `HttpURLConnection` or manual `RestTemplate`. |
| **Persistence** | `AuditPersistenceService` (MyBatis Mapper) | **Refactor**. Already uses `AuditPersistenceServiceFactory`. Ensure it works without Spring Context. |
| **DLP Analysis** | `DlpEngine` (Empty Shell) | **Refactor**. Make it a POJO. Hook it into `DeepAuditHook` (limited to SQL analysis or simulated result scoring). |

### Detailed Execution Steps

1.  **Refactor `deep-audit-core` (The "Plugin Lib")**:
    -   **Remove Spring Dependency**: Remove `spring-boot-starter` and `spring-boot-starter-web` from `pom.xml`. Keep `spring-context` (optional) or remove entirely.
    -   **Refactor Services**:
        -   `RiskStateMachine`: Add setters for `StringRedisTemplate`, `RiskProperties`, `SysUserRiskProfileMapper`. Remove `@Autowired`, `@Component`, `@PostConstruct`.
        -   `AnomalyDetectionService`: Replace `RestTemplate` injection with manual instantiation or standard Java HTTP client. Remove `@Async` (handle async in Hook).
        -   `DlpEngine`: Remove `@Component`.
        -   `AuditPersistenceService`: Remove `@Service`.
    -   **Clean Up**: Delete `SqlAuditInterceptor`, `P6Spy` listeners, `UserContext` (if only used for thread-local passing which Plugin doesn't need across methods same way), and `CoreAuditService` (logic moves to Hook).

2.  **Enhance `deep-audit-shardingsphere-plugin` (The "Plugin Impl")**:
    -   **Factories**: Update `RiskStateMachineFactory` and `AuditPersistenceServiceFactory` to correctly instantiate the refactored Core POJOs with all dependencies (Redis, DB Mappers).
    -   **`DeepAuditHook` Integration**:
        -   **Start**: Parse SQL -> Extract User -> Call `RiskStateMachine.checkStatus`.
        -   **Async**: Trigger `AnomalyDetectionService.detectAnomaly` (using a simple ThreadPool).
        -   **Finish**: Call `AuditPersistenceService.saveLog`.

3.  **Dependency Isolation**:
    -   Verify `backend` and `core` share NO code dependencies.
    -   `backend` depends on its own copy of Models/Mappers.
    -   `plugin` depends on `core`.

### Outcome
A standalone `deep-audit-shardingsphere-plugin` JAR that contains all auditing logic, running within ShardingSphere Proxy, communicating with `deep-audit-backend` solely via the MySQL database.