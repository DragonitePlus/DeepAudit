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

        AUDIT_CONTEXT.remove();
        String userId = resolveUserId(sql);
        if (userId == null) userId = "unknown";
        AUDIT_CONTEXT.set(new AuditContext(userId, sql, System.currentTimeMillis()));

        try {
            String action = riskStateMachine.checkStatus(userId, 0);
            if ("BLOCK".equals(action)) {
                SqlDeepAnalyzer.SqlFeatures astFeatures = SqlDeepAnalyzer.analyze(sql);
                String tableNames = String.join(",", astFeatures.tableNames);
                logAudit(userId, sql, "BLOCK", "", 0, astFeatures, tableNames);
                AUDIT_CONTEXT.remove();
                throw new RuntimeException("DeepAudit Risk Control: Access Denied for user " + userId);
            }
        } catch (Exception e) {
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

                // 1. AST Analysis
                SqlDeepAnalyzer.SqlFeatures astFeatures = SqlDeepAnalyzer.analyze(context.sql);

                // 2. DLP Analysis (敏感表检查) 🔥🔥
                // 传入解析出的表名
                int dlpRiskScore = dlpEngine.calculateRiskScore(astFeatures.tableNames);

                // 3. AI Detection
                int aiRiskScore = (int) anomalyDetectionService.detectRisk(
                        context.userId, LocalDateTime.now(), context.sql, astFeatures
                );

                // 4. Combine scores
                int finalRiskScore = Math.max(dlpRiskScore, aiRiskScore);
                
                System.out.println(String.format(
                        "DeepAudit Report [User: %s] | SQL: %s | DLP Score: %d | AI Score: %d | Final Risk: %d",
                        context.userId, context.sql, dlpRiskScore, aiRiskScore, finalRiskScore
                ));

                // 5. 构造表名字符串 (用于日志)
                String tableNames = String.join(",", astFeatures.tableNames);
                if (tableNames.isEmpty()) tableNames = "unknown";

                logAudit(context.userId, context.sql, "PASS", null, finalRiskScore, astFeatures, tableNames);
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
                // 尝试解析表名（即使失败了，AST 信息也可能部分有用）
                SqlDeepAnalyzer.SqlFeatures astFeatures = null;
                String tableNames = "unknown";

                try {
                    astFeatures = SqlDeepAnalyzer.analyze(context.sql);
                    if (astFeatures != null && astFeatures.tableNames != null && !astFeatures.tableNames.isEmpty()) {
                        tableNames = String.join(",", astFeatures.tableNames);
                    }
                } catch (Exception ex) {
                    // 解析失败忽略
                }

                // 修正参数：logAudit(userId, sql, action, extraInfo, riskScore, ast, tableNames)
                logAudit(context.userId, context.sql, "PASS", "Error: " + e.getMessage(), 0,
                        astFeatures, tableNames);
            }
        } finally {
            AUDIT_CONTEXT.remove();
        }
    }

    private void logAudit(String userId, String sql, String action, String extraInfo, int riskScore,
                          SqlDeepAnalyzer.SqlFeatures ast, String tableNames) {
        new Thread(() -> {
            try {
                SysAuditLog log = new SysAuditLog();
                log.setTraceId(UUID.randomUUID().toString());
                log.setAppUserId(userId);
                log.setSqlTemplate(sql);
                log.setTableNames(tableNames); // 🔥 设置解析出的表名
                log.setRiskScore(riskScore);
                // ... (其他设置保持不变)
                log.setActionTaken(action);
                log.setCreateTime(LocalDateTime.now());
                log.setExtraInfo(extraInfo != null ? extraInfo : "{}");
                if (ast != null) {
                    log.setConditionCount(ast.conditionCount);
                    log.setJoinCount(ast.joinCount);
                    log.setNestedLevel(ast.nestedLevel);
                    log.setHasAlwaysTrue(ast.hasAlwaysTrueCondition);
                    log.setSqlHash(String.valueOf(sql.hashCode()));
                }
                
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
