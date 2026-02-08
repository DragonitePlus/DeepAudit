package edu.hnu.deepaudit.service;

import edu.hnu.deepaudit.interception.AuditSink;
import edu.hnu.deepaudit.mapper.sys.SysAuditLogMapper;
import edu.hnu.deepaudit.model.SysAuditLog;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Simple implementation of AuditSink for the backend.
 * Only responsible for saving external audit logs to the database.
 * No complex risk analysis or DLP scanning.
 */
@Service
public class SimpleAuditService implements AuditSink {

    @Autowired
    private SysAuditLogMapper sysAuditLogMapper;

    @Override
    public void submit(AuditRequest request) {
        SysAuditLog log = new SysAuditLog();
        log.setTraceId(request.getTraceId() != null ? request.getTraceId() : UUID.randomUUID().toString());
        log.setAppUserId(request.getAppUserId() != null ? request.getAppUserId() : "unknown");
        log.setSqlTemplate(request.getSql());
        log.setCreateTime(LocalDateTime.now());
        
        // Backend doesn't do parsing/risk analysis, so we set defaults
        log.setTableNames("external");
        log.setRiskScore(0);
        log.setActionTaken("LOG"); 
        
        if (request.getExtraInfo() != null) {
            log.setExtraInfo(request.getExtraInfo());
        } else {
            log.setExtraInfo(request.getSource());
        }

        sysAuditLogMapper.insert(log);
    }
}
