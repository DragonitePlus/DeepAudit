package edu.hnu.deepaudit.interception;

import edu.hnu.deepaudit.config.AuditProperties;
import edu.hnu.deepaudit.control.RiskStateMachine;
import edu.hnu.deepaudit.exception.RiskControlException;
import edu.hnu.deepaudit.service.AuditRequest;
import org.apache.ibatis.cache.CacheKey;
import org.apache.ibatis.executor.Executor;
import org.apache.ibatis.mapping.BoundSql;
import org.apache.ibatis.mapping.MappedStatement;
import org.apache.ibatis.plugin.*;
import org.apache.ibatis.session.ResultHandler;
import org.apache.ibatis.session.RowBounds;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.Properties;
import java.util.concurrent.CompletableFuture;

/**
 * Perception Layer: SQL Audit Interceptor
 * * 核心拦截器：负责两件事
 * 1. Active Defense (主动防御): 在 SQL 执行前检查用户状态，如果是 BLOCK 则抛异常中断。
 * 2. Passive Audit (被动审计): 在 SQL 执行后（无论成功失败）异步记录日志。
 */
@Component
@Intercepts({
        @Signature(type = Executor.class, method = "query", args = {MappedStatement.class, Object.class, RowBounds.class, ResultHandler.class}),
        @Signature(type = Executor.class, method = "query", args = {MappedStatement.class, Object.class, RowBounds.class, ResultHandler.class, CacheKey.class, BoundSql.class}),
        @Signature(type = Executor.class, method = "update", args = {MappedStatement.class, Object.class})
})
public class SqlAuditInterceptor implements Interceptor {

    private static final Logger log = LoggerFactory.getLogger(SqlAuditInterceptor.class);

    @Autowired
    @Lazy
    private AuditSink auditSink;

    @Autowired
    private AuditProperties auditProperties;

    @Autowired
    @Lazy
    private RiskStateMachine riskStateMachine;

    @Override
    public Object intercept(Invocation invocation) throws Throwable {
        long start = System.currentTimeMillis();

        // 1. Early Parsing: 获取 SQL 信息
        // 必须尽早获取，以便进行白名单判断
        BoundSql boundSql = getBoundSql(invocation);
        String sql = (boundSql != null && boundSql.getSql() != null) ? boundSql.getSql() : "";

        // =================================================================================
        // 🛡️ CRITICAL SAFETY GUARD (递归熔断保护)
        // =================================================================================
        if (isInternalSystemTable(sql)) {
            return invocation.proceed();
        }

        // 2. 获取用户上下文
        String currentUserId = UserContext.getUserId();
        
        // 3. 注入 SQL Hint (ShardingSphere Proxy 适配)
        // 如果我们连接的是 Proxy，这一步至关重要，它告诉 Proxy 是谁在执行 SQL
        if (StringUtils.hasText(currentUserId) && !"unknown".equals(currentUserId)) {
            try {
                // 修改 BoundSql 中的 SQL 语句，注入 Hint
                // 注意：修改 BoundSql 的 sql 字段可能需要反射，或者根据 MyBatis 版本不同有不同做法
                // 这里采用一种更通用的方式：如果可能，替换参数。
                // 但 MyBatis 插件机制修改 SQL 最直接的方式是反射修改 BoundSql 的 sql 字段。
                
                String sqlWithHint = "/* user_id:" + currentUserId + " */ " + sql;
                
                // 反射修改 sql 字段
                java.lang.reflect.Field sqlField = BoundSql.class.getDeclaredField("sql");
                sqlField.setAccessible(true);
                sqlField.set(boundSql, sqlWithHint);
                
                // 更新局部变量 sql 以便后续日志记录使用带 Hint 的版本（可选，或者保留原始 SQL）
                // sql = sqlWithHint; 
            } catch (Exception e) {
                log.warn("Failed to inject SQL Hint for user: {}", currentUserId, e);
            }
        }

        // =================================================================================
        // 🛑 ACTIVE DEFENSE (主动防御 - 阻断逻辑)
        // =================================================================================
        // ... (保持原有逻辑)
        // 仅当包含有效用户身份时才检查，避免阻断系统启动时的初始化 SQL
        if (StringUtils.hasText(currentUserId) && !"unknown".equals(currentUserId)) {
            try {
                // 使用 score=0 调用 checkStatus，只读状态，不增加风险分
                // 目的是查询 Redis/DB 中该用户当前是否处于 BLOCK 状态
                String action = riskStateMachine.checkStatus(currentUserId, 0);

                if ("BLOCK".equals(action)) {
                    log.warn("⛔ ACCESS DENIED: User [{}] is BLOCKED. Blocking SQL: {}", currentUserId, sql.trim().substring(0, Math.min(sql.length(), 50)));
                    // 抛出特定异常，终止 MyBatis 执行链
                    throw new RiskControlException("您的账号已被系统风控冻结，禁止执行数据库操作。");
                }
            } catch (RiskControlException e) {
                // 必须显式捕获并重新抛出，防止被下方的 catch (Exception) 吞掉
                throw e;
            } catch (Exception e) {
                // 风控服务挂了怎么办？通常遵循 "Fail Open" (放行) 原则以保证业务可用性，
                // 或者 "Fail Closed" (阻断) 以保证安全性。这里选择记录日志并放行。
                log.error("⚠️ Risk check failed (Fail Open). User: {}, Error: {}", currentUserId, e.getMessage());
            }
        }

        // 3. 执行原始 SQL (Proceed)
        Object result = null;
        Throwable executionException = null;
        try {
            result = invocation.proceed();
            return result;
        } catch (Throwable t) {
            executionException = t;
            throw t; // 必须抛出异常让上层感知
        } finally {
            // =================================================================================
            // 📝 ASYNC AUDIT (异步审计)
            // =================================================================================
            // 无论 SQL 执行成功、失败、还是被阻断（阻断在上面 throw 了，这里 finally 依然会走吗？
            // 注意：如果在上面 Active Defense 阶段直接 throw RiskControlException，
            // 这里 finally 块会被执行，但是 invocation.proceed() 没有执行，
            // 此时记录的日志能反映出“试图执行但被阻断”。
            long duration = System.currentTimeMillis() - start;
            submitAuditAsync(currentUserId, sql, duration, result, executionException);
        }
    }

    /**
     * 判断是否为系统内部表，防止递归
     */
    private boolean isInternalSystemTable(String sql) {
        if (!StringUtils.hasText(sql)) return true;
        String lowerSql = sql.toLowerCase().replace(" ", "").replace("\n", "");

        // 核心表硬编码保护
        return lowerSql.contains("sys_audit_log") ||
                lowerSql.contains("sys_user_risk_profile") ||
                lowerSql.contains("sys_risk_rule");
    }

    /**
     * 异步提交审计日志
     */
    private void submitAuditAsync(String userId, String sql, long duration, Object result, Throwable ex) {
        CompletableFuture.runAsync(() -> {
            try {
                // 二次过滤（基于配置文件的表黑名单）
                if (shouldExcludeByConfig(sql)) {
                    return;
                }

                AuditRequest request = AuditRequest.builder()
                        .traceId(java.util.UUID.randomUUID().toString())
                        .appUserId(userId == null ? "unknown" : userId)
                        .sql(sql)
                        .executionTime(duration)
                        .result(result)
                        .extraInfo(ex != null ? "Error: " + ex.getMessage() : null)
                        // 如果有异常，且是我们抛出的 RiskControlException，标记动作为 BLOCK
                        .actionTaken((ex instanceof RiskControlException) ? "BLOCK" : "PASS")
                        .source("MYBATIS_LEGACY")
                        .build();

                auditSink.submit(request);
            } catch (Exception e) {
                log.error("Failed to submit audit log asynchronously", e);
            }
        });
    }

    private boolean shouldExcludeByConfig(String sql) {
        if (!StringUtils.hasText(sql)) return true;
        String lowerSql = sql.toLowerCase();

        if (auditProperties != null && auditProperties.getExcludedTables() != null) {
            for (String table : auditProperties.getExcludedTables()) {
                if (lowerSql.contains(table.toLowerCase())) {
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * 兼容不同版本 MyBatis 插件签名的 BoundSql 获取方法
     */
    private BoundSql getBoundSql(Invocation invocation) {
        Object[] args = invocation.getArgs();
        MappedStatement ms = (MappedStatement) args[0];
        Object parameter = args[1];

        // 针对 pagehelper 等插件可能修改了 args 签名的情况
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