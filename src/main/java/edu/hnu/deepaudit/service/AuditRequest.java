package edu.hnu.deepaudit.service;

import java.util.Objects;

public class AuditRequest {
    private String traceId;
    private String appUserId;
    private String sql;
    private long executionTime;
    private Object result; // Optional, might be too large
    private String source; // "JDBC", "MyBatis", "External"
    private String clientIp;
    private String extraInfo;

    public AuditRequest() {
    }

    public AuditRequest(String traceId, String appUserId, String sql, long executionTime, Object result, String source, String clientIp, String extraInfo) {
        this.traceId = traceId;
        this.appUserId = appUserId;
        this.sql = sql;
        this.executionTime = executionTime;
        this.result = result;
        this.source = source;
        this.clientIp = clientIp;
        this.extraInfo = extraInfo;
    }

    public static AuditRequestBuilder builder() {
        return new AuditRequestBuilder();
    }


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

    public String getSql() {
        return sql;
    }

    public void setSql(String sql) {
        this.sql = sql;
    }

    public long getExecutionTime() {
        return executionTime;
    }

    public void setExecutionTime(long executionTime) {
        this.executionTime = executionTime;
    }

    public Object getResult() {
        return result;
    }

    public void setResult(Object result) {
        this.result = result;
    }

    public String getSource() {
        return source;
    }

    public void setSource(String source) {
        this.source = source;
    }

    public String getClientIp() {
        return clientIp;
    }

    public void setClientIp(String clientIp) {
        this.clientIp = clientIp;
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
        AuditRequest that = (AuditRequest) o;
        return executionTime == that.executionTime &&
                Objects.equals(traceId, that.traceId) &&
                Objects.equals(appUserId, that.appUserId) &&
                Objects.equals(sql, that.sql) &&
                Objects.equals(result, that.result) &&
                Objects.equals(source, that.source) &&
                Objects.equals(clientIp, that.clientIp) &&
                Objects.equals(extraInfo, that.extraInfo);
    }

    @Override
    public int hashCode() {
        return Objects.hash(traceId, appUserId, sql, executionTime, result, source, clientIp, extraInfo);
    }

    @Override
    public String toString() {
        return "AuditRequest{" +
                "traceId='" + traceId + '\'' +
                ", appUserId='" + appUserId + '\'' +
                ", sql='" + sql + '\'' +
                ", executionTime=" + executionTime +
                ", result=" + result +
                ", source='" + source + '\'' +
                ", clientIp='" + clientIp + '\'' +
                ", extraInfo='" + extraInfo + '\'' +
                '}';
    }

    public static class AuditRequestBuilder {
        private String traceId;
        private String appUserId;
        private String sql;
        private long executionTime;
        private Object result;
        private String source;
        private String clientIp;
        private String extraInfo;

        AuditRequestBuilder() {
        }

        public AuditRequestBuilder traceId(String traceId) {
            this.traceId = traceId;
            return this;
        }

        public AuditRequestBuilder appUserId(String appUserId) {
            this.appUserId = appUserId;
            return this;
        }

        public AuditRequestBuilder sql(String sql) {
            this.sql = sql;
            return this;
        }

        public AuditRequestBuilder executionTime(long executionTime) {
            this.executionTime = executionTime;
            return this;
        }

        public AuditRequestBuilder result(Object result) {
            this.result = result;
            return this;
        }

        public AuditRequestBuilder source(String source) {
            this.source = source;
            return this;
        }

        public AuditRequestBuilder clientIp(String clientIp) {
            this.clientIp = clientIp;
            return this;
        }

        public AuditRequestBuilder extraInfo(String extraInfo) {
            this.extraInfo = extraInfo;
            return this;
        }

        public AuditRequest build() {
            return new AuditRequest(traceId, appUserId, sql, executionTime, result, source, clientIp, extraInfo);
        }

        @Override
        public String toString() {
            return "AuditRequest.AuditRequestBuilder(traceId=" + this.traceId + ", appUserId=" + this.appUserId + ", sql=" + this.sql + ", executionTime=" + this.executionTime + ", result=" + this.result + ", source=" + this.source + ", clientIp=" + this.clientIp + ", extraInfo=" + this.extraInfo + ")";
        }
    }
}
