package edu.hnu.deepaudit.model;

import java.time.LocalDateTime;
import java.util.Objects;

public class SysAuditLog {
    private String traceId;
    
    /**
     * Application User ID (Identity Gap Solution)
     */
    private String appUserId;
    
    /**
     * Desensitized SQL Template
     */
    private String sqlTemplate;
    
    /**
     * Accessed Tables (comma separated)
     */
    private String tableNames;
    
    /**
     * DLP Risk Score
     */
    private Integer riskScore;
    
    /**
     * Result Set Row Count
     */
    private Long resultCount;

    /**
     * Action Taken (PASS/BLOCK)
     */
    private String actionTaken;

    private LocalDateTime createTime;

    /**
     * Client IP Address
     */
    private String clientIp;

    /**
     * SQL Execution Time (ms)
     */
    private Long executionTime;

    /**
     * JSON/Text for dynamic extension data
     */
    private String extraInfo;

    /**
     * Feedback Status: 0-Unmarked, 1-False Positive(Normal), 2-True Positive(Anomaly)
     */
    private Integer feedbackStatus;

    // --- AST Features ---
    private String sqlHash;
    private Long affectedRows;
    private Integer errorCode;
    private String clientApp;

    // Detailed AST stats
    private Integer conditionCount;
    private Integer joinCount;
    private Integer nestedLevel;
    private Boolean hasAlwaysTrue;

    public SysAuditLog() {
    }

    // Getters and Setters
    public Integer getFeedbackStatus() { return feedbackStatus; }
    public void setFeedbackStatus(Integer feedbackStatus) { this.feedbackStatus = feedbackStatus; }

    public String getSqlHash() { return sqlHash; }
    public void setSqlHash(String sqlHash) { this.sqlHash = sqlHash; }

    public Long getAffectedRows() { return affectedRows; }
    public void setAffectedRows(Long affectedRows) { this.affectedRows = affectedRows; }

    public Integer getErrorCode() { return errorCode; }
    public void setErrorCode(Integer errorCode) { this.errorCode = errorCode; }

    public String getClientApp() { return clientApp; }
    public void setClientApp(String clientApp) { this.clientApp = clientApp; }

    public Integer getConditionCount() { return conditionCount; }
    public void setConditionCount(Integer conditionCount) { this.conditionCount = conditionCount; }

    public Integer getJoinCount() { return joinCount; }
    public void setJoinCount(Integer joinCount) { this.joinCount = joinCount; }

    public Integer getNestedLevel() { return nestedLevel; }
    public void setNestedLevel(Integer nestedLevel) { this.nestedLevel = nestedLevel; }

    public void setResultCount(Long resultCount) { this.resultCount = resultCount; }

    public Boolean getHasAlwaysTrue() { return hasAlwaysTrue; }
    public void setHasAlwaysTrue(Boolean hasAlwaysTrue) { this.hasAlwaysTrue = hasAlwaysTrue; }

    public String getTraceId() {
        return traceId;
    }

    public void setTraceId(String traceId) {
        this.traceId = traceId;
    }

    public String getAppUserId() {
        return appUserId;
    }

    public void setAppUserId(String appUserId) {
        this.appUserId = appUserId;
    }

    public String getSqlTemplate() {
        return sqlTemplate;
    }

    public void setSqlTemplate(String sqlTemplate) {
        this.sqlTemplate = sqlTemplate;
    }

    public String getTableNames() {
        return tableNames;
    }

    public void setTableNames(String tableNames) {
        this.tableNames = tableNames;
    }

    public Integer getRiskScore() {
        return riskScore;
    }

    public void setRiskScore(Integer riskScore) {
        this.riskScore = riskScore;
    }

    public Long getResultCount() {
        return resultCount;
    }

    public String getActionTaken() {
        return actionTaken;
    }

    public void setActionTaken(String actionTaken) {
        this.actionTaken = actionTaken;
    }

    public LocalDateTime getCreateTime() {
        return createTime;
    }

    public void setCreateTime(LocalDateTime createTime) {
        this.createTime = createTime;
    }

    public String getClientIp() {
        return clientIp;
    }

    public void setClientIp(String clientIp) {
        this.clientIp = clientIp;
    }

    public Long getExecutionTime() {
        return executionTime;
    }

    public void setExecutionTime(Long executionTime) {
        this.executionTime = executionTime;
    }

    public String getExtraInfo() {
        return extraInfo;
    }

    public void setExtraInfo(String extraInfo) {
        this.extraInfo = extraInfo;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        SysAuditLog that = (SysAuditLog) o;
        return Objects.equals(traceId, that.traceId) &&
                Objects.equals(appUserId, that.appUserId) &&
                Objects.equals(sqlTemplate, that.sqlTemplate) &&
                Objects.equals(tableNames, that.tableNames) &&
                Objects.equals(riskScore, that.riskScore) &&
                Objects.equals(resultCount, that.resultCount) &&
                Objects.equals(actionTaken, that.actionTaken) &&
                Objects.equals(createTime, that.createTime) &&
                Objects.equals(clientIp, that.clientIp) &&
                Objects.equals(executionTime, that.executionTime) &&
                Objects.equals(extraInfo, that.extraInfo);
    }

    @Override
    public int hashCode() {
        return Objects.hash(traceId, appUserId, sqlTemplate, tableNames, riskScore, resultCount, actionTaken, createTime, clientIp, executionTime, extraInfo);
    }

    @Override
    public String toString() {
        return "SysAuditLog{" +
                "traceId='" + traceId + '\'' +
                ", appUserId='" + appUserId + '\'' +
                ", sqlTemplate='" + sqlTemplate + '\'' +
                ", tableNames='" + tableNames + '\'' +
                ", riskScore=" + riskScore +
                ", resultCount=" + resultCount +
                ", actionTaken='" + actionTaken + '\'' +
                ", createTime=" + createTime +
                ", clientIp='" + clientIp + '\'' +
                ", executionTime=" + executionTime +
                ", extraInfo='" + extraInfo + '\'' +
                '}';
    }
}
