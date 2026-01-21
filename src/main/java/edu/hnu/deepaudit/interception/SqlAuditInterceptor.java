package edu.hnu.deepaudit.interception;

import org.apache.ibatis.cache.CacheKey;
import org.apache.ibatis.executor.Executor;
import org.apache.ibatis.mapping.BoundSql;
import org.apache.ibatis.mapping.MappedStatement;
import org.apache.ibatis.plugin.*;
import org.apache.ibatis.session.ResultHandler;
import org.apache.ibatis.session.RowBounds;
import edu.hnu.deepaudit.config.AuditProperties;
import edu.hnu.deepaudit.service.AuditRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;

import java.util.Properties;
import java.util.concurrent.CompletableFuture;

/**
 * Perception Layer: SQL Audit Interceptor
 * Intercepts MyBatis Executor to capture SQL execution.
 */
@Component
@Intercepts({
        @Signature(type = Executor.class, method = "query", args = {MappedStatement.class, Object.class, RowBounds.class, ResultHandler.class}),
        @Signature(type = Executor.class, method = "query", args = {MappedStatement.class, Object.class, RowBounds.class, ResultHandler.class, CacheKey.class, BoundSql.class}),
        @Signature(type = Executor.class, method = "update", args = {MappedStatement.class, Object.class})
})
public class SqlAuditInterceptor implements Interceptor {

    @Autowired
    @Lazy
    private AuditSink auditSink;

    @Autowired
    private AuditProperties auditProperties;

    @Override
    public Object intercept(Invocation invocation) throws Throwable {
        long start = System.currentTimeMillis();
        
        // Capture User Context in Main Thread
        String currentUserId = UserContext.getUserId();
        
        // 1. Pre-execution logic (optional)
        
        // 2. Execute original query
        Object result = invocation.proceed();
        
        long end = System.currentTimeMillis();
        
        // 3. Asynchronous Analysis (Non-blocking)
        CompletableFuture.runAsync(() -> {
            try {
                // Legacy support: still extract info here but delegate to AuditSink
                BoundSql boundSql = getBoundSql(invocation);
                String sql = boundSql.getSql();
                
                // Filtering
                if (shouldExclude(sql)) {
                    return;
                }
                
                AuditRequest request = AuditRequest.builder()
                        .traceId(java.util.UUID.randomUUID().toString())
                        .appUserId(currentUserId)
                        .sql(sql)
                        .executionTime(end - start)
                        .result(result) // MyBatis interceptor can capture result easily
                        .source("MYBATIS_LEGACY")
                        .build();

                auditSink.submit(request);
                
            } catch (Exception e) {
                // Ensure auditing does not affect business logic
                System.err.println("DeepAudit Error: " + e.getMessage());
                e.printStackTrace();
            }
        });

        return result;
    }

    private boolean shouldExclude(String sql) {
        if (sql == null) return true;
        String lowerSql = sql.toLowerCase();
        
        // Hardcoded safety check
        if (lowerSql.contains("sys_audit_log")) {
            return true;
        }
        
        // Configurable check
        if (auditProperties != null && auditProperties.getExcludedTables() != null) {
            for (String table : auditProperties.getExcludedTables()) {
                if (lowerSql.contains(table.toLowerCase())) {
                    return true;
                }
            }
        }
        return false;
    }

    private BoundSql getBoundSql(Invocation invocation) {
        Object[] args = invocation.getArgs();
        MappedStatement ms = (MappedStatement) args[0];
        Object parameter = args[1];
        // For the method signature with CacheKey and BoundSql (args length 6)
        if (args.length == 6 && args[5] instanceof BoundSql) {
            return (BoundSql) args[5];
        }
        return ms.getBoundSql(parameter);
    }

    @Override
    public Object plugin(Object target) {
        return Plugin.wrap(target, this);
    }

    @Override
    public void setProperties(Properties properties) {
    }
}
