package edu.hnu.deepaudit.model.dto;

/**
 * 对应 Python 服务的输入特征
 * 参考论文 5.1
 */
public class AuditLogFeatureDTO {
    private Long timestamp;      // 执行时间戳
    private Integer rowCount;    // 影响行数
    private Long execTime;       // 执行耗时
    private Integer sqlLength;   // SQL 长度
    private Integer numTables;   // 涉及表数量 (通过 Druid 解析获得)
    private Integer numJoins;    // 关联查询数量 (通过 Druid 解析获得)
    private Integer freq1Min;    // 过去1分钟调用频率 (需 Redis 计数器支持)

    // 无参构造函数
    public AuditLogFeatureDTO() {}

    // 全参构造函数
    public AuditLogFeatureDTO(Long timestamp, Integer rowCount, Long execTime, Integer sqlLength, Integer numTables, Integer numJoins, Integer freq1Min) {
        this.timestamp = timestamp;
        this.rowCount = rowCount;
        this.execTime = execTime;
        this.sqlLength = sqlLength;
        this.numTables = numTables;
        this.numJoins = numJoins;
        this.freq1Min = freq1Min;
    }

    // Builder 入口
    public static AuditLogFeatureDTOBuilder builder() {
        return new AuditLogFeatureDTOBuilder();
    }

    // Builder 内部类
    public static class AuditLogFeatureDTOBuilder {
        private Long timestamp;
        private Integer rowCount;
        private Long execTime;
        private Integer sqlLength;
        private Integer numTables;
        private Integer numJoins;
        private Integer freq1Min;

        AuditLogFeatureDTOBuilder() {}

        public AuditLogFeatureDTOBuilder timestamp(Long timestamp) {
            this.timestamp = timestamp;
            return this;
        }

        public AuditLogFeatureDTOBuilder rowCount(Integer rowCount) {
            this.rowCount = rowCount;
            return this;
        }

        public AuditLogFeatureDTOBuilder execTime(Long execTime) {
            this.execTime = execTime;
            return this;
        }

        public AuditLogFeatureDTOBuilder sqlLength(Integer sqlLength) {
            this.sqlLength = sqlLength;
            return this;
        }

        public AuditLogFeatureDTOBuilder numTables(Integer numTables) {
            this.numTables = numTables;
            return this;
        }

        public AuditLogFeatureDTOBuilder numJoins(Integer numJoins) {
            this.numJoins = numJoins;
            return this;
        }

        public AuditLogFeatureDTOBuilder freq1Min(Integer freq1Min) {
            this.freq1Min = freq1Min;
            return this;
        }

        public AuditLogFeatureDTO build() {
            return new AuditLogFeatureDTO(timestamp, rowCount, execTime, sqlLength, numTables, numJoins, freq1Min);
        }
    }

    // Getters and Setters
    public Long getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(Long timestamp) {
        this.timestamp = timestamp;
    }

    public Integer getRowCount() {
        return rowCount;
    }

    public void setRowCount(Integer rowCount) {
        this.rowCount = rowCount;
    }

    public Long getExecTime() {
        return execTime;
    }

    public void setExecTime(Long execTime) {
        this.execTime = execTime;
    }

    public Integer getSqlLength() {
        return sqlLength;
    }

    public void setSqlLength(Integer sqlLength) {
        this.sqlLength = sqlLength;
    }

    public Integer getNumTables() {
        return numTables;
    }

    public void setNumTables(Integer numTables) {
        this.numTables = numTables;
    }

    public Integer getNumJoins() {
        return numJoins;
    }

    public void setNumJoins(Integer numJoins) {
        this.numJoins = numJoins;
    }

    public Integer getFreq1Min() {
        return freq1Min;
    }

    public void setFreq1Min(Integer freq1Min) {
        this.freq1Min = freq1Min;
    }
}
