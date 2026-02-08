package edu.hnu.deepaudit.proxy;

import edu.hnu.deepaudit.analysis.DlpEngine;
import edu.hnu.deepaudit.analysis.SqlDeepAnalyzer;
import edu.hnu.deepaudit.control.RiskStateMachine;
import edu.hnu.deepaudit.model.SysAuditLog;
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
 * DeepAudit Hook - Real-time Blocking Enabled
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

        long startTime = System.currentTimeMillis();

        try {
            // 0. 黑名单检查 (极速)
            String action = riskStateMachine.checkStatus(userId, 0);
            if ("BLOCK".equals(action)) {
                throw new RuntimeException("DeepAudit Risk Control: User " + userId + " is blocked!");
            }

            // 1. AST 解析 (事前)
            SqlDeepAnalyzer.SqlFeatures astFeatures = SqlDeepAnalyzer.analyze(sql);

            // 2. 实时 AI 风控检测 (Pre-Execution Check)
            // 注意：此时 duration=0，行数未知，完全依赖 SQL 结构和频率特征
            int aiRiskScore = (int) anomalyDetectionService.detectRisk(
                    userId, LocalDateTime.now(), sql, astFeatures
            );

            // 3. 敏感表检查 (DLP)
            int dlpRiskScore = dlpEngine.calculateRiskScore(astFeatures.tableNames);

            // 4. 综合评分 & 阻断决策
            int finalRiskScore = Math.max(dlpRiskScore, aiRiskScore);

            // 🔥 阻断阈值：80分 (Block Threshold)
            if (finalRiskScore >= 80) {
                // 记录阻断日志
                String tableNames = String.join(",", astFeatures.tableNames);
                logAudit(userId, sql, "BLOCK", "Risk Score: " + finalRiskScore, finalRiskScore, astFeatures, tableNames);

                System.err.println(String.format("🛑 BLOCKING SQL [User: %s] Risk: %d | SQL: %s", userId, finalRiskScore, sql));
                throw new RuntimeException("DeepAudit Risk Control: High Risk Action Detected (Score: " + finalRiskScore + ")");
            }

            // 保存上下文供 finishSuccess 使用 (避免重复计算)
            AUDIT_CONTEXT.set(new AuditContext(userId, sql, startTime, finalRiskScore, astFeatures));

        } catch (RuntimeException e) {
            AUDIT_CONTEXT.remove();
            throw e; // 抛出异常以阻断执行
        } catch (Exception e) {
            // 兜底：如果检测过程报错，原则上放行但记录错误，或者选择阻断
            System.err.println("DeepAudit Detection Error: " + e.getMessage());
            AUDIT_CONTEXT.set(new AuditContext(userId, sql, startTime, 0, null));
        }
    }

    @Override
    public void finishSuccess() {
        try {
            AuditContext context = AUDIT_CONTEXT.get();
            if (context != null) {
                // 如果 start 中发生异常被阻断，不会走到这里
                // 能走到这里说明是 PASS 的请求

                String tableNames = "unknown";
                SqlDeepAnalyzer.SqlFeatures ast = context.astFeatures;

                if (ast != null && ast.tableNames != null) {
                    tableNames = String.join(",", ast.tableNames);
                }

                // 打印通过日志
                System.out.println(String.format(
                        "✅ PASS [User: %s] Risk: %d | SQL: %s",
                        context.userId, context.riskScore, context.sql
                ));

                logAudit(context.userId, context.sql, "PASS", null, context.riskScore, ast, tableNames);
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
                // 即使执行失败，也要记录日志 (可能包含未遂攻击信息)
                String tableNames = "unknown";
                if (context.astFeatures != null && context.astFeatures.tableNames != null) {
                    tableNames = String.join(",", context.astFeatures.tableNames);
                }

                logAudit(context.userId, context.sql, "PASS", "Error: " + e.getMessage(),
                        context.riskScore, context.astFeatures, tableNames);
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
                log.setTableNames(tableNames);
                log.setRiskScore(riskScore);
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
        int riskScore;
        SqlDeepAnalyzer.SqlFeatures astFeatures;

        public AuditContext(String userId, String sql, long startTime, int riskScore, SqlDeepAnalyzer.SqlFeatures astFeatures) {
            this.userId = userId;
            this.sql = sql;
            this.startTime = startTime;
            this.riskScore = riskScore;
            this.astFeatures = astFeatures;
        }
    }
}