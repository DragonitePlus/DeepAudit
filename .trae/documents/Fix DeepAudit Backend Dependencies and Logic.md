Based on the analysis, I have identified the following issues in the `deep-audit-backend` module that need to be fixed:

1.  **`MyBatisBizConfig.java` Dependencies**: It imports `SqlAuditInterceptor` from `core`, which is not available (and should not be used) in the backend. The backend should access the business database directly without self-interception.
2.  **Missing `SysUserMapper`**: The `edu.hnu.deepaudit.mapper.biz` package was not successfully copied, causing `SysUserService` to fail compilation (missing `SysUserMapper` dependency).
3.  **Vestigial Logic**: `TestController` uses `UserContext`, which is tied to the interception logic. While harmless if `UserContext` exists, it's misleading since the backend doesn't perform interception.

### Plan to Fix

1.  **Copy Missing Mappers**:
    - Copy the `edu.hnu.deepaudit.mapper.biz` package (containing `SysUserMapper`) from `core` to `backend`.

2.  **Refactor `MyBatisBizConfig`**:
    - Remove the dependency on `SqlAuditInterceptor`.
    - Remove the code that adds `sqlAuditInterceptor` to the `SqlSessionFactory` plugins.
    - Keep `MybatisPlusInterceptor` (pagination) as it is useful for the management console.

3.  **Verify Service Layer**:
    - Ensure `SysUserService` compiles once `SysUserMapper` is present.
    - (Optional) Clean up `TestController` if necessary, but primarily ensure it compiles.

4.  **Final Verification**:
    - Ensure all imports in `backend` point to classes that exist within the `backend` module.

I will proceed with these fixes immediately.