package edu.hnu.deepaudit.persistence;

import edu.hnu.deepaudit.model.SysAuditLog;
import edu.hnu.deepaudit.persistence.mapper.SysAuditLogMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

/**
 * Persistence Module Service.
 * Responsible for saving audit logs to storage.
 */
@Service
public class AuditPersistenceService {

    @Autowired
    private SysAuditLogMapper sysAuditLogMapper;

    /**
     * Save audit log to database.
     * @param log The audit log entity.
     */
    public void saveLog(SysAuditLog log) {
        if (log == null) {
            return;
        }
        sysAuditLogMapper.insert(log);
    }
}
