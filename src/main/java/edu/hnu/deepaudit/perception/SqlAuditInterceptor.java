package edu.hnu.deepaudit.perception;

import org.apache.ibatis.cache.CacheKey;
import org.apache.ibatis.executor.Executor;
import org.apache.ibatis.mapping.BoundSql;
import org.apache.ibatis.mapping.MappedStatement;
import org.apache.ibatis.plugin.*;
import org.apache.ibatis.session.ResultHandler;
import org.apache.ibatis.session.RowBounds;
import edu.hnu.deepaudit.analysis.DlpEngine;
import edu.hnu.deepaudit.control.RiskStateMachine;
import org.springframework.beans.factory.annotation.Autowired;
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
    private DlpEngine dlpEngine;

    @Autowired
    private RiskStateMachine riskStateMachine;

    @Override
    public Object intercept(Invocation invocation) throws Throwable {
        long start = System.currentTimeMillis();
        
        // 1. Pre-execution logic (optional)
        
        // 2. Execute original query
        Object result = invocation.proceed();
        
        long end = System.currentTimeMillis();
        
        // 3. Asynchronous Analysis (Non-blocking)
        CompletableFuture.runAsync(() -> {
            try {
                analyze(invocation, result, start, end);
            } catch (Exception e) {
                // Ensure auditing does not affect business logic
                System.err.println("DeepAudit Error: " + e.getMessage());
                e.printStackTrace();
            }
        });

        return result;
    }

    private void analyze(Invocation invocation, Object result, long start, long end) {
        // Placeholder for Analysis Logic
        // TODO: A. Parse SQL to extract table names (using Druid Parser)
        
        // B. Scan ResultSet for sensitive data (DLP)
        int riskScore = dlpEngine.calculateRiskScore(result);
        
        // C. Check Redis State Machine
        // Get real User ID from Session/ThreadLocal
        String userId = UserContext.getUserId();
        if (userId == null) {
            userId = "unknown"; // Or "system"
        }
        String action = riskStateMachine.checkStatus(userId, riskScore);
        
        // TODO: D. Persist Audit Log
        
        System.out.println("DeepAudit: Analyzed SQL execution in " + (end - start) + "ms. Risk Score: " + riskScore + ", Action: " + action);
    }

    @Override
    public Object plugin(Object target) {
        return Plugin.wrap(target, this);
    }

    @Override
    public void setProperties(Properties properties) {
    }
}
