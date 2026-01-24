package edu.hnu.deepaudit.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * Configuration properties for DeepAudit filtering.
 * Allows users to configure excluded tables and system tables.
 */
@Component
@ConfigurationProperties(prefix = "deepaudit.filter")
public class AuditProperties {

    /**
     * Tables that should NOT be audited.
     * Includes system audit logs (to prevent infinite loop) and user-defined exclusions.
     */
    private List<String> excludedTables = new ArrayList<>();

    /**
     * System tables that might require special handling or are internal to the framework.
     */
    private List<String> systemTables = new ArrayList<>();

    public List<String> getExcludedTables() {
        return excludedTables;
    }

    public void setExcludedTables(List<String> excludedTables) {
        this.excludedTables = excludedTables;
    }

    public List<String> getSystemTables() {
        return systemTables;
    }

    public void setSystemTables(List<String> systemTables) {
        this.systemTables = systemTables;
    }
}
