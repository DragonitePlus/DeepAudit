package edu.hnu.deepaudit.proxy;

import edu.hnu.deepaudit.control.RiskStateMachine;
import edu.hnu.deepaudit.model.SysAuditLog;
import edu.hnu.deepaudit.persistence.AuditPersistenceService;
import edu.hnu.deepaudit.proxy.factory.AuditPersistenceServiceFactory;
import edu.hnu.deepaudit.proxy.factory.RiskStateMachineFactory;
import org.apache.shardingsphere.infra.database.core.connector.ConnectionProperties;
import org.apache.shardingsphere.infra.executor.sql.hook.SQLExecutionHook;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * DeepAudit Hook Strictly Adapted for ShardingSphere 5.4.1 specific build
 */
public class DeepAuditHook implements SQLExecutionHook {

    private final RiskStateMachine riskStateMachine = RiskStateMachineFactory.getInstance();
    private final AuditPersistenceService auditPersistenceService = AuditPersistenceServiceFactory.getInstance();
    private static final Pattern USER_ID_HINT_PATTERN = Pattern.compile("/\\* user_id:(.*?) \\*/");
    
    private static final ThreadLocal<AuditContext> AUDIT_CONTEXT = new ThreadLocal<>();

    /**
     * ✅ 严格匹配你提供的 start 方法签名
     *
     * @param dataSourceName       数据源名称 (String s)
     * @param sql                  执行的 SQL (String s1)
     * @param parameters           SQL 参数列表 (List<Object> list)
     * @param connectionProperties 连接属性 (ConnectionProperties connectionProperties)
     * @param isTrunkThread        是否主线程 (boolean b)
     */
    @Override
    public void start(String dataSourceName, String sql, List<Object> parameters,
                      ConnectionProperties connectionProperties, boolean isTrunkThread) {

        // 1. 解析用户身份
        String userId = resolveUserId(sql);
        if (userId == null) {
            userId = "unknown";
        }

        // Store context for finish() phase
        AUDIT_CONTEXT.set(new AuditContext(userId, sql, System.currentTimeMillis()));

        // 2. 风控检查
        // 传入 0 分，仅检查状态，不修改状态
        String action = riskStateMachine.checkStatus(userId, 0);

        if ("BLOCK".equals(action)) {
            // Log BLOCK event immediately before throwing exception
            logAudit(userId, sql, 0, "BLOCK", "Risk Control: Access Denied");
            // 抛出异常中断执行
            throw new RuntimeException("DeepAudit Risk Control: Access Denied for user " + userId);
        }
    }

    /**
     * ✅ 严格匹配你提供的 finishSuccess 方法签名 (无参)
     */
    @Override
    public void finishSuccess() {
        try {
            AuditContext context = AUDIT_CONTEXT.get();
            if (context != null) {
                long duration = System.currentTimeMillis() - context.startTime;
                logAudit(context.userId, context.sql, duration, "PASS", null);
            }
        } finally {
            AUDIT_CONTEXT.remove();
        }
    }

    /**
     * ✅ 严格匹配你提供的 finishFailure 方法签名 (Exception)
     */
    @Override
    public void finishFailure(Exception e) {
        try {
            AuditContext context = AUDIT_CONTEXT.get();
            if (context != null) {
                long duration = System.currentTimeMillis() - context.startTime;
                logAudit(context.userId, context.sql, duration, "PASS", "Error: " + e.getMessage());
            }
        } finally {
            AUDIT_CONTEXT.remove();
        }
    }

    private void logAudit(String userId, String sql, long duration, String action, String extraInfo) {
        // Run asynchronously to avoid blocking SQL execution
        // Note: In a real high-concurrency scenario, use a thread pool or message queue
        new Thread(() -> {
            try {
                SysAuditLog log = new SysAuditLog();
                log.setTraceId(UUID.randomUUID().toString());
                log.setAppUserId(userId);
                log.setSqlTemplate(sql);
                // Simple parsing for table names could be added here if needed
                log.setTableNames("unknown"); 
                log.setRiskScore(0); // Score calculation requires DLP engine
                log.setActionTaken(action);
                log.setCreateTime(LocalDateTime.now());
                log.setExecutionTime(duration);
                log.setExtraInfo(extraInfo != null ? extraInfo : "Proxy Audit");
                
                auditPersistenceService.saveLog(log);
            } catch (Exception ex) {
                // Fail-safe: don't crash proxy if logging fails
                System.err.println("DeepAudit: Failed to save audit log: " + ex.getMessage());
            }
        }).start();
    }

    private String resolveUserId(String sql) {
        if (sql == null) return null;
        Matcher matcher = USER_ID_HINT_PATTERN.matcher(sql);
        if (matcher.find()) {
            return matcher.group(1).trim();
        }
        return null;
    }

    private static class AuditContext {
        String userId;
        String sql;
        long startTime;

        public AuditContext(String userId, String sql, long startTime) {
            this.userId = userId;
            this.sql = sql;
            this.startTime = startTime;
        }
    }
}