package edu.hnu.deepaudit.model.dto;

/**
 * 对应 Python 服务的输入特征
 * 参考论文 5.1
 */
public class AuditLogFeatureDTO {
    // ORDER MUST MATCH: 
    // ['hour_of_day', 'is_workday', 'freq_1min', 'sql_type_weight',
    //  'condition_count', 'join_count', 'nested_level', 'has_always_true']
    private Float hourOfDay;
    private Float isWorkday;
    private Float freq1Min;
    private Float sqlTypeWeight;
    private Float conditionCount;
    private Float joinCount;
    private Float nestedLevel;
    private Float hasAlwaysTrue;

    // 无参构造函数
    public AuditLogFeatureDTO() {}

    public AuditLogFeatureDTO(Float hourOfDay, Float isWorkday, Float freq1Min, Float sqlTypeWeight, Float conditionCount, Float joinCount, Float nestedLevel, Float hasAlwaysTrue) {
        this.hourOfDay = hourOfDay;
        this.isWorkday = isWorkday;
        this.freq1Min = freq1Min;
        this.sqlTypeWeight = sqlTypeWeight;
        this.conditionCount = conditionCount;
        this.joinCount = joinCount;
        this.nestedLevel = nestedLevel;
        this.hasAlwaysTrue = hasAlwaysTrue;
    }

    // Getters and Setters
    public Float getHourOfDay() { return hourOfDay; }
    public void setHourOfDay(Float hourOfDay) { this.hourOfDay = hourOfDay; }

    public Float getIsWorkday() { return isWorkday; }
    public void setIsWorkday(Float isWorkday) { this.isWorkday = isWorkday; }

    public Float getFreq1Min() { return freq1Min; }
    public void setFreq1Min(Float freq1Min) { this.freq1Min = freq1Min; }

    public Float getSqlTypeWeight() { return sqlTypeWeight; }
    public void setSqlTypeWeight(Float sqlTypeWeight) { this.sqlTypeWeight = sqlTypeWeight; }

    public Float getConditionCount() { return conditionCount; }
    public void setConditionCount(Float conditionCount) { this.conditionCount = conditionCount; }

    public Float getJoinCount() { return joinCount; }
    public void setJoinCount(Float joinCount) { this.joinCount = joinCount; }

    public Float getNestedLevel() { return nestedLevel; }
    public void setNestedLevel(Float nestedLevel) { this.nestedLevel = nestedLevel; }

    public Float getHasAlwaysTrue() { return hasAlwaysTrue; }
    public void setHasAlwaysTrue(Float hasAlwaysTrue) { this.hasAlwaysTrue = hasAlwaysTrue; }
}
