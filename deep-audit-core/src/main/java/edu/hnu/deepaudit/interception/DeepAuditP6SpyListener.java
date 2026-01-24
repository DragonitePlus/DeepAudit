package edu.hnu.deepaudit.interception;

import com.p6spy.engine.common.StatementInformation;
import com.p6spy.engine.event.JdbcEventListener;
import edu.hnu.deepaudit.config.AuditProperties;
import edu.hnu.deepaudit.service.AuditRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;

import java.sql.SQLException;

/**
 * JDBC Layer Listener based on P6Spy
 * Captures all SQL executed via JDBC driver.
 */
@Component
public class DeepAuditP6SpyListener extends JdbcEventListener {

    private static AuditSink auditSink;
    private static AuditProperties auditProperties;

    // Static injection workaround because P6Spy creates instances outside Spring container
    @Autowired
    public void setDependencies(@Lazy AuditSink sink, AuditProperties properties) {
        DeepAuditP6SpyListener.auditSink = sink;
        DeepAuditP6SpyListener.auditProperties = properties;
    }

    @Override
    public void onAfterExecute(StatementInformation statementInformation, long timeElapsedNanos, String category, SQLException e) {
        handleExecution(statementInformation, timeElapsedNanos, e);
    }

    @Override
    public void onAfterExecuteQuery(StatementInformation statementInformation, long timeElapsedNanos, String category, SQLException e) {
        handleExecution(statementInformation, timeElapsedNanos, e);
    }

    @Override
    public void onAfterExecuteUpdate(StatementInformation statementInformation, long timeElapsedNanos, String category, int rowCount, SQLException e) {
        handleExecution(statementInformation, timeElapsedNanos, e);
    }

    private void handleExecution(StatementInformation statementInformation, long timeElapsedNanos, SQLException e) {
        if (auditSink == null) {
            return;
        }
        
        String sql = statementInformation.getSqlWithValues();
        if (sql == null || sql.isEmpty()) {
            return;
        }

        // Filtering: Check against excluded tables (prevent infinite loop)
        String lowerSql = sql.toLowerCase();
        if (lowerSql.contains("sys_audit_log")) {
            return;
        }
        
        if (auditProperties != null && auditProperties.getExcludedTables() != null) {
            for (String table : auditProperties.getExcludedTables()) {
                if (lowerSql.contains(table.toLowerCase())) {
                    return;
                }
            }
        }

        // Get User Context (ThreadLocal works here because JDBC executes in the same thread)
        String userId = UserContext.getUserId();

        AuditRequest request = AuditRequest.builder()
                .traceId(java.util.UUID.randomUUID().toString())
                .appUserId(userId)
                .sql(sql)
                .executionTime(timeElapsedNanos / 1_000_000) // ns to ms
                .source("JDBC_P6SPY")
                // Note: Result extraction in P6Spy is harder than MyBatis. 
                // We might need to implement onAfterResultSetNext if we want full DLP.
                // For now, we focus on SQL capture.
                .build();

        auditSink.submit(request);
    }
}
