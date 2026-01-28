I will now execute the plan to merge `deep-audit-shardingsphere-plugin` into `deep-audit-core`, fix the tests, and clean up the codebase.

### Task 1: Merge Plugin to Core
1.  **Move Code**: Move `edu.hnu.deepaudit.proxy` package from plugin to core.
2.  **Move Resources**: Move `META-INF/services` from plugin to core.
3.  **Update POM**: Add ShardingSphere dependencies to `deep-audit-core/pom.xml`.
4.  **Remove Module**: Delete `deep-audit-shardingsphere-plugin` and update root POM.

### Task 2: Fix and Annotate Tests
I will systematically go through `src/test/java` in `deep-audit-core`:
1.  **Refactor**: Convert Spring Boot tests to pure JUnit 5 tests using the new Factory classes (`RiskStateMachineFactory`, etc.).
2.  **Deprecate/Delete**: Remove tests that rely on deleted components (like P6Spy, Controllers).
3.  **Annotate**: Add Chinese comments explaining the test purpose and philosophy.
    *   *Example*: `RiskStateMachineTest` will test the state transitions using a mocked Redis/DB.

### Task 3: Cleanup
1.  **Delete Redundant Files**:
    *   `RiskWindowExpirationListener` (belongs in backend).
    *   `CoreAuditService` (logic moved to Hook).
    *   `UserContext` (logic moved to Hook/Backend).
    *   `AuditSink` (if only used by deleted services).
    *   `p6spy` config files.
2.  **Documentation**:
    *   Create `PROJECT_STRUCTURE.md` explaining the new single-module plugin structure.
    *   Create `USER_MANUAL.md` explaining how to build and install the JAR into ShardingSphere.

I will start by moving the files and updating the POM.