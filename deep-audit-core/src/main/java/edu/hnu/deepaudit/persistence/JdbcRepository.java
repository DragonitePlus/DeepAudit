package edu.hnu.deepaudit.persistence;

import edu.hnu.deepaudit.model.SysAuditLog;
import edu.hnu.deepaudit.model.SysUserRiskProfile;
import edu.hnu.deepaudit.model.SysSensitiveTable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.sql.DataSource;
import java.sql.*;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * JDBC Repository for handling DB operations without MyBatis/Spring
 */
public class JdbcRepository {
    private static final Logger log = LoggerFactory.getLogger(JdbcRepository.class);
    
    private final DataSource dataSource;

    public JdbcRepository(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    /**
     * 加载所有敏感表配置
     */
    public List<SysSensitiveTable> getAllSensitiveTables() {
        List<SysSensitiveTable> list = new ArrayList<>();
        String sql = "SELECT * FROM sys_sensitive_table";

        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {

            while (rs.next()) {
                SysSensitiveTable table = new SysSensitiveTable();
                table.setId(rs.getLong("id"));
                table.setTableName(rs.getString("table_name"));
                table.setSensitivityLevel(rs.getInt("sensitivity_level"));
                table.setCoefficient(rs.getDouble("coefficient"));
                list.add(table);
            }
        } catch (SQLException e) {
            log.error("Failed to load sensitive tables: {}", e.getMessage());
        }
        return list;
    }

    public void saveAuditLog(SysAuditLog logEntry) {
        // Schema compliant INSERT
        String sql = "INSERT INTO sys_audit_log (trace_id, app_user_id, sql_template, table_names, risk_score, " +
                "result_count, action_taken, create_time, client_ip, execution_time, extra_info, " +
                "feedback_status, sql_hash, affected_rows, error_code, client_app) " +
                "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";
        
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            
            ps.setString(1, logEntry.getTraceId());
            ps.setString(2, logEntry.getAppUserId());
            ps.setString(3, logEntry.getSqlTemplate());
            ps.setString(4, logEntry.getTableNames());
            ps.setObject(5, logEntry.getRiskScore());
            ps.setObject(6, logEntry.getResultCount());
            ps.setString(7, logEntry.getActionTaken());
            ps.setObject(8, logEntry.getCreateTime());
            ps.setString(9, logEntry.getClientIp());
            ps.setObject(10, logEntry.getExecutionTime());
            
            // Enrich extra_info with AST stats for potential future use or debugging
            String extraInfo = logEntry.getExtraInfo();
            if (extraInfo == null || extraInfo.trim().isEmpty()) {
                extraInfo = "{}";
            }
            // Robust JSON append
            if (logEntry.getConditionCount() != null) {
                String astJson = String.format("\"ast\": {\"cond\": %d, \"join\": %d, \"nest\": %d, \"true\": %b}", 
                        logEntry.getConditionCount(), logEntry.getJoinCount(), logEntry.getNestedLevel(), logEntry.getHasAlwaysTrue());
                
                String trimmed = extraInfo.trim();
                if ("{}".equals(trimmed)) {
                    extraInfo = "{" + astJson + "}";
                } else if (trimmed.endsWith("}")) {
                     // Insert before the last '}'
                     int lastBrace = extraInfo.lastIndexOf("}");
                     extraInfo = extraInfo.substring(0, lastBrace) + ", " + astJson + "}";
                }
            }
            ps.setString(11, extraInfo);
            
            // New Schema Columns
            ps.setObject(12, logEntry.getFeedbackStatus() != null ? logEntry.getFeedbackStatus() : 0);
            ps.setString(13, logEntry.getSqlHash());
            ps.setObject(14, logEntry.getAffectedRows() != null ? logEntry.getAffectedRows() : 0);
            ps.setObject(15, logEntry.getErrorCode() != null ? logEntry.getErrorCode() : 0);
            ps.setString(16, logEntry.getClientApp());
            
            ps.executeUpdate();
        } catch (SQLException e) {
            log.error("Failed to save audit log: {}", e.getMessage(), e);
        }
    }

    public SysUserRiskProfile getRiskProfile(String userId) {
        String sql = "SELECT * FROM sys_user_risk_profile WHERE app_user_id = ?";
        
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            
            ps.setString(1, userId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    SysUserRiskProfile profile = new SysUserRiskProfile();
                    profile.setAppUserId(rs.getString("app_user_id"));
                    profile.setCurrentScore(rs.getInt("current_score"));
                    profile.setRiskLevel(rs.getString("risk_level"));
                    
                    Timestamp ts = rs.getTimestamp("last_update_time");
                    if (ts != null) {
                        profile.setLastUpdateTime(ts.toLocalDateTime());
                    }
                    profile.setDescription(rs.getString("description"));
                    return profile;
                }
            }
        } catch (SQLException e) {
            log.error("Failed to get risk profile for user {}: {}", userId, e.getMessage());
        }
        return null;
    }

    public void saveOrUpdateRiskProfile(SysUserRiskProfile profile) {
        // Upsert syntax for MySQL
        String sql = "INSERT INTO sys_user_risk_profile (app_user_id, current_score, risk_level, last_update_time, description) " +
                "VALUES (?, ?, ?, ?, ?) " +
                "ON DUPLICATE KEY UPDATE current_score = VALUES(current_score), risk_level = VALUES(risk_level), " +
                "last_update_time = VALUES(last_update_time), description = VALUES(description)";

        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setString(1, profile.getAppUserId());
            ps.setObject(2, profile.getCurrentScore());
            ps.setString(3, profile.getRiskLevel());
            ps.setObject(4, profile.getLastUpdateTime());
            ps.setString(5, profile.getDescription());

            ps.executeUpdate();
        } catch (SQLException e) {
            log.error("Failed to save risk profile for user {}: {}", profile.getAppUserId(), e.getMessage());
        }
    }
}
