package edu.hnu.deepaudit.service;

import edu.hnu.deepaudit.analysis.DlpEngine;
import edu.hnu.deepaudit.config.AuditProperties;
import edu.hnu.deepaudit.control.RiskStateMachine;
import edu.hnu.deepaudit.interception.AuditSink;
import edu.hnu.deepaudit.interception.SqlParserUtils;
import edu.hnu.deepaudit.model.SysAuditLog;
import edu.hnu.deepaudit.persistence.AuditPersistenceService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.Set;
import java.util.concurrent.CompletableFuture;

/**
 * Core Audit Service
 * Decoupled from the capture layer (MyBatis/JDBC/Proxy).
 */
@Service
public class CoreAuditService implements AuditSink {

    private static final Logger log = LoggerFactory.getLogger(CoreAuditService.class);

    @Autowired
    private SqlParserUtils sqlParserUtils;

    @Autowired
    private DlpEngine dlpEngine;

    @Autowired
    private RiskStateMachine riskStateMachine;

    @Autowired
    private AuditPersistenceService auditPersistenceService;

    @Autowired
    private AuditProperties auditProperties;

    /**
     * Process an audit request asynchronously
     */
    @Override
    public void submit(AuditRequest request) {
        CompletableFuture.runAsync(() -> {
            try {
                doAnalyze(request);
            } catch (Exception e) {
                log.error("DeepAudit Analysis Failed", e);
            }
        });
    }

    private void doAnalyze(AuditRequest request) {
        String sql = request.getSql();
        String userId = request.getAppUserId();
        
        // 1. Parse SQL
        Set<String> tables = sqlParserUtils.extractTableNames(sql);
        
        // Precise filtering based on parsed table names
        if (tables.contains("sys_audit_log")) {
            return;
        }
        if (auditProperties != null && auditProperties.getExcludedTables() != null) {
            for (String excluded : auditProperties.getExcludedTables()) {
                if (tables.contains(excluded)) {
                    return;
                }
            }
        }
        
        String operation = sqlParserUtils.getOperationType(sql);
        
        // 2. DLP Scan
        int riskScore = 0;
        if (request.getResult() != null) {
            riskScore = dlpEngine.calculateRiskScore(request.getResult());
        }
        
        // 3. Risk Control
        if (userId == null || userId.isEmpty()) {
            userId = "unknown";
        }
        String action = riskStateMachine.checkStatus(userId, riskScore);
        
        // 4. Log/Persist
        log.info("DeepAudit [Source: {}] [User: {}] [Op: {}] [Tables: {}] [Risk: {}] [Action: {}] [Time: {}ms]",
                request.getSource(), userId, operation, tables, riskScore, action, request.getExecutionTime());

        // 5. Persistence
        SysAuditLog auditLog = new SysAuditLog();
        auditLog.setTraceId(request.getTraceId());
        auditLog.setAppUserId(userId);
        auditLog.setSqlTemplate(sql); // In real scenario, should be desensitized
        auditLog.setTableNames(String.join(",", tables));
        auditLog.setRiskScore(riskScore);
        // auditLog.setResultCount(...) // if available
        auditLog.setActionTaken(action);
        auditLog.setCreateTime(LocalDateTime.now());
        
        // Map extended fields
        auditLog.setClientIp(request.getClientIp());
        auditLog.setExecutionTime(request.getExecutionTime());
        
        // Use extraInfo if available, otherwise use source
        if (request.getExtraInfo() != null) {
            auditLog.setExtraInfo(request.getExtraInfo());
        } else {
            auditLog.setExtraInfo(request.getSource());
        }
        
        auditPersistenceService.saveLog(auditLog);
    }
}
