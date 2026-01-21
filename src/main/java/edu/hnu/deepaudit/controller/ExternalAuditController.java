package edu.hnu.deepaudit.controller;

import edu.hnu.deepaudit.config.AuditProperties;
import edu.hnu.deepaudit.interception.AuditSink;
import edu.hnu.deepaudit.service.AuditRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * External Audit API
 * Allows non-Java systems (e.g., Go modules) to report audit data.
 */
@RestController
@RequestMapping("/api/audit")
public class ExternalAuditController {

    @Autowired
    private AuditSink auditSink;

    @Autowired
    private AuditProperties auditProperties;

    @PostMapping("/report")
    public String reportAudit(@RequestBody AuditRequest request) {
        if (request.getSource() == null) {
            request.setSource("EXTERNAL");
        }
        auditSink.submit(request);
        return "OK";
    }

    // --- Configuration Endpoints ---

    @GetMapping("/config/excluded-tables")
    public List<String> getExcludedTables() {
        return auditProperties.getExcludedTables();
    }

    @PostMapping("/config/excluded-tables")
    public String updateExcludedTables(@RequestBody List<String> tables) {
        // Ensure system tables are always excluded
        if (!tables.contains("sys_audit_log")) {
            tables.add("sys_audit_log");
        }
        auditProperties.setExcludedTables(tables);
        return "Updated excluded tables: " + tables;
    }
}
