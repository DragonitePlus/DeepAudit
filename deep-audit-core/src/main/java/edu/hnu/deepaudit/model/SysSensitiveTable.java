package edu.hnu.deepaudit.model;

import java.util.Objects;

/**
 * 敏感表配置实体
 * 用于定义数据库表的敏感级别和风险系数
 */
public class SysSensitiveTable {
    private Long id;
    
    /**
     * 表名
     */
    private String tableName;
    
    /**
     * 敏感级别 (1-4)
     */
    private Integer sensitivityLevel;
    
    /**
     * 风险系数 (e.g. 1.5, 2.0)
     */
    private Double coefficient;

    public SysSensitiveTable() {
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getTableName() {
        return tableName;
    }

    public void setTableName(String tableName) {
        this.tableName = tableName;
    }

    public Integer getSensitivityLevel() {
        return sensitivityLevel;
    }

    public void setSensitivityLevel(Integer sensitivityLevel) {
        this.sensitivityLevel = sensitivityLevel;
    }

    public Double getCoefficient() {
        return coefficient;
    }

    public void setCoefficient(Double coefficient) {
        this.coefficient = coefficient;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        SysSensitiveTable that = (SysSensitiveTable) o;
        return Objects.equals(id, that.id) &&
                Objects.equals(tableName, that.tableName) &&
                Objects.equals(sensitivityLevel, that.sensitivityLevel) &&
                Objects.equals(coefficient, that.coefficient);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id, tableName, sensitivityLevel, coefficient);
    }

    @Override
    public String toString() {
        return "SysSensitiveTable{" +
                "id=" + id +
                ", tableName='" + tableName + '\'' +
                ", sensitivityLevel=" + sensitivityLevel +
                ", coefficient=" + coefficient +
                '}';
    }
}
