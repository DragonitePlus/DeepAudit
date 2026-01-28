package edu.hnu.deepaudit.proxy;

import edu.hnu.deepaudit.analysis.DlpEngine;
import edu.hnu.deepaudit.control.RiskStateMachine;
import edu.hnu.deepaudit.model.SysAuditLog;
import edu.hnu.deepaudit.model.dto.AuditLogFeatureDTO;
import edu.hnu.deepaudit.persistence.AuditPersistenceService;
import edu.hnu.deepaudit.proxy.factory.AuditPersistenceServiceFactory;
import edu.hnu.deepaudit.proxy.factory.RiskStateMachineFactory;
import edu.hnu.deepaudit.service.AnomalyDetectionService;
import org.apache.shardingsphere.infra.database.core.connector.ConnectionProperties;
import org.apache.shardingsphere.infra.executor.sql.hook.SQLExecutionHook;
import org.springframework.data.redis.core.StringRedisTemplate;

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
    private final AnomalyDetectionService anomalyDetectionService = new AnomalyDetectionService();
    private final DlpEngine dlpEngine = new DlpEngine();
    
    private static final Pattern USER_ID_HINT_PATTERN = Pattern.compile("/\\* user_id:(.*?) \\*/");
    
    private static final ThreadLocal<AuditContext> AUDIT_CONTEXT = new ThreadLocal<>();

    // Manual init for AnomalyDetectionService (requires RedisTemplate if we want to use it)
    // For now, we assume simple HTTP call without Redis or we need to expose Redis from Factory
    // TODO: Pass RedisTemplate to AnomalyDetectionService via Factory or Singleton

    @Override
    public void start(String dataSourceName, String sql, List<Object> parameters,
                      ConnectionProperties connectionProperties, boolean isTrunkThread) {

        // 1. Resolve User ID
        String userId = resolveUserId(sql);
        if (userId == null) {
            userId = "unknown";
        }

        // Store context for finish() phase
        AUDIT_CONTEXT.set(new AuditContext(userId, sql, System.currentTimeMillis()));

        // 2. Risk Control Check (Synchronous)
        // Pass 0 score to just check current status (e.g. is user already BLOCKED?)
        String action = riskStateMachine.checkStatus(userId, 0);

        if ("BLOCK".equals(action)) {
            // Log BLOCK event immediately
            logAudit(userId, sql, 0, "BLOCK", "Risk Control: Access Denied", 0);
            throw new RuntimeException("DeepAudit Risk Control: Access Denied for user " + userId);
        }
    }

    @Override
    public void finishSuccess() {
        try {
            AuditContext context = AUDIT_CONTEXT.get();
            if (context != null) {
                long duration = System.currentTimeMillis() - context.startTime;
                
                // 3. AI Anomaly Detection (Async) & DLP Analysis (Simulated)
                // Since we don't have result set here, we simulate DLP or analyze SQL structure
                int riskScore = dlpEngine.calculateRiskScore(null); // Placeholder
                
                // Construct Features for AI
                AuditLogFeatureDTO features = AuditLogFeatureDTO.builder()
                        .timestamp(System.currentTimeMillis())
                        .execTime(duration)
                        .sqlLength(context.sql.length())
                        .build();
                        
                anomalyDetectionService.detectAnomalyAsync(context.userId, features);
                
                // 4. Log Audit
                logAudit(context.userId, context.sql, duration, "PASS", null, riskScore);
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
                logAudit(context.userId, context.sql, duration, "PASS", "Error: " + e.getMessage(), 0);
            }
        } finally {
            AUDIT_CONTEXT.remove();
        }
    }

    private void logAudit(String userId, String sql, long duration, String action, String extraInfo, int riskScore) {
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
                log.setExtraInfo(extraInfo != null ? extraInfo : "Proxy Audit");
                
                auditPersistenceService.saveLog(log);
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