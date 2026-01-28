package edu.hnu.deepaudit.persistence;

import edu.hnu.deepaudit.model.SysAuditLog;
import edu.hnu.deepaudit.mapper.sys.SysAuditLogMapper;

/**
 * Persistence Module Service.
 * Responsible for saving audit logs to storage.
 */
public class AuditPersistenceService {

    private SysAuditLogMapper sysAuditLogMapper;
    
    public void setSysAuditLogMapper(SysAuditLogMapper sysAuditLogMapper) {
        this.sysAuditLogMapper = sysAuditLogMapper;
    }

    /**
     * Save audit log to database.
     * @param log The audit log entity.
     */
    public void saveLog(SysAuditLog log) {
        if (log == null || sysAuditLogMapper == null) {
            return;
        }
        sysAuditLogMapper.insert(log);
    }
}
