package edu.hnu.deepaudit.proxy;

import edu.hnu.deepaudit.analysis.DlpEngine;
import edu.hnu.deepaudit.analysis.SqlDeepAnalyzer;
import edu.hnu.deepaudit.control.RiskStateMachine;
import edu.hnu.deepaudit.model.SysAuditLog;
import edu.hnu.deepaudit.model.dto.AuditLogFeatureDTO;
import edu.hnu.deepaudit.persistence.JdbcRepository;
import edu.hnu.deepaudit.proxy.factory.DeepAuditFactory;
import edu.hnu.deepaudit.service.AnomalyDetectionService;
import org.apache.shardingsphere.infra.database.core.connector.ConnectionProperties;
import org.apache.shardingsphere.infra.executor.sql.hook.SQLExecutionHook;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * DeepAudit Hook Adapted for ShardingSphere 5.4.1 (No-Spring Version)
 */
public class DeepAuditHook implements SQLExecutionHook {

    private final DeepAuditFactory factory = DeepAuditFactory.getInstance();
    
    private final RiskStateMachine riskStateMachine = factory.getRiskStateMachine();
    private final AnomalyDetectionService anomalyDetectionService = factory.getAnomalyDetectionService();
    private final DlpEngine dlpEngine = factory.getDlpEngine();
    private final JdbcRepository jdbcRepository = factory.getJdbcRepository();
    
    private static final Pattern USER_ID_HINT_PATTERN = Pattern.compile("/\\* user_id:(.*?) \\*/");
    
    private static final ThreadLocal<AuditContext> AUDIT_CONTEXT = new ThreadLocal<>();

    @Override
    public void start(String dataSourceName, String sql, List<Object> parameters,
                      ConnectionProperties connectionProperties, boolean isTrunkThread) {

        AUDIT_CONTEXT.remove(); // 防止之前的线程没清理干净

        String userId = resolveUserId(sql);
        if (userId == null) {
            userId = "unknown";
        }

        // 1. 设置上下文
        AUDIT_CONTEXT.set(new AuditContext(userId, sql, System.currentTimeMillis()));

        try {
            // 2. 风控检查
            // 传入 0 分只是为了检查状态 (是否已在黑名单)
            String action = riskStateMachine.checkStatus(userId, 0);

            if ("BLOCK".equals(action)) {
                // 3. 记录阻断日志
                SqlDeepAnalyzer.SqlFeatures astFeatures = SqlDeepAnalyzer.analyze(sql);
                logAudit(userId, sql, 0, "BLOCK", "Risk Control: Access Denied", 0, astFeatures, 0, 0, 1);

                // 🔥🔥 关键修复：在抛出异常前，必须清理 ThreadLocal！🔥🔥
                // 因为一旦抛出异常，finish 方法就不会被调用了！
                AUDIT_CONTEXT.remove();

                // 4. 阻断执行
                throw new RuntimeException("DeepAudit Risk Control: Access Denied for user " + userId);
            }

        } catch (Exception e) {
            // 双重保险：如果是 checkStatus 内部报错，也要清理
            // 如果是上面抛出的 RuntimeException，也会走到这里，清理后再次抛出
            AUDIT_CONTEXT.remove();
            throw e;
        }
    }

    @Override
    public void finishSuccess() {
        try {
            AuditContext context = AUDIT_CONTEXT.get();
            if (context != null) {
                long duration = System.currentTimeMillis() - context.startTime;
                
                // 3. AI Anomaly Detection (Async) & DLP Analysis (Simulated)
                int riskScore = dlpEngine.calculateRiskScore(null); // Placeholder
                
                // AST Analysis
                SqlDeepAnalyzer.SqlFeatures astFeatures = SqlDeepAnalyzer.analyze(context.sql);
                
                // AI Detection
                int aiRiskScore = (int) anomalyDetectionService.detectRisk(
                    context.userId, 
                    LocalDateTime.now(), 
                    0, // rowCount not available
                    0, // affectedRows not available
                    duration, 
                    0, // errorCode
                    context.sql,
                    astFeatures
                );
                
                // Combine scores (max of DLP and AI)
                int finalRiskScore = Math.max(riskScore, aiRiskScore);
                
                // 4. Log Audit with Features
                logAudit(context.userId, context.sql, duration, "PASS", null, finalRiskScore, astFeatures, 0, 0, 0);
            }
        } finally {
            AUDIT_CONTEXT.remove();
        }
    }

    @Override
    public void finishFailure(Exception e) {
        try {
            AuditContext context = AUDIT_CONTEXT.get();
            if (context != null) {
                long duration = System.currentTimeMillis() - context.startTime;
                // Still analyze AST for failure cases (might be injection attempts)
                SqlDeepAnalyzer.SqlFeatures astFeatures = SqlDeepAnalyzer.analyze(context.sql);
                
                logAudit(context.userId, context.sql, duration, "PASS", "Error: " + e.getMessage(), 0, astFeatures, 0, 0, 1);
            }
        } finally {
            AUDIT_CONTEXT.remove();
        }
    }

    private void logAudit(String userId, String sql, long duration, String action, String extraInfo, int riskScore, 
                          SqlDeepAnalyzer.SqlFeatures ast, long rowCount, long affectedRows, int errorCode) {
        // Use async thread or CompletableFuture to avoid blocking main SQL thread
        // JdbcRepository is thread-safe (uses HikariCP)
        new Thread(() -> {
            try {
                SysAuditLog log = new SysAuditLog();
                log.setTraceId(UUID.randomUUID().toString());
                log.setAppUserId(userId);
                log.setSqlTemplate(sql);
                log.setTableNames("unknown"); 
                log.setRiskScore(riskScore);
                log.setActionTaken(action);
                log.setCreateTime(LocalDateTime.now());
                log.setExecutionTime(duration);
                // Initialize extraInfo as JSON object if null
                log.setExtraInfo(extraInfo != null ? extraInfo : "{}");
                
                // AST Features
                if (ast != null) {
                    log.setConditionCount(ast.conditionCount);
                    log.setJoinCount(ast.joinCount);
                    log.setNestedLevel(ast.nestedLevel);
                    log.setHasAlwaysTrue(ast.hasAlwaysTrueCondition);
                    // Generate hash (simple md5 or hashCode)
                    log.setSqlHash(String.valueOf(sql.hashCode())); 
                }
                
                log.setResultCount((long) rowCount);
                log.setAffectedRows((long) affectedRows);
                log.setErrorCode(errorCode);
                log.setClientApp("Unknown"); // Placeholder
                
                jdbcRepository.saveAuditLog(log);
            } catch (Exception ex) {
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
