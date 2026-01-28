package edu.hnu.deepaudit.model;

import java.time.LocalDateTime;
import java.util.Objects;

/**
 * 用户风险画像实体
 * 存储用户的实时风险分和状态
 */
public class SysUserRiskProfile {
    private String appUserId;
    
    private Integer currentScore;
    
    private String riskLevel; // NORMAL, OBSERVATION, BLOCKED
    
    private LocalDateTime lastUpdateTime;
    
    private String description;

    public SysUserRiskProfile() {
    }

    public String getAppUserId() {
        return appUserId;
    }

    public void setAppUserId(String appUserId) {
        this.appUserId = appUserId;
    }

    public Integer getCurrentScore() {
        return currentScore;
    }

    public void setCurrentScore(Integer currentScore) {
        this.currentScore = currentScore;
    }

    public String getRiskLevel() {
        return riskLevel;
    }

    public void setRiskLevel(String riskLevel) {
        this.riskLevel = riskLevel;
    }

    public LocalDateTime getLastUpdateTime() {
        return lastUpdateTime;
    }

    public void setLastUpdateTime(LocalDateTime lastUpdateTime) {
        this.lastUpdateTime = lastUpdateTime;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    @Override
    public String toString() {
        return "SysUserRiskProfile{" +
                "appUserId='" + appUserId + '\'' +
                ", currentScore=" + currentScore +
                ", riskLevel='" + riskLevel + '\'' +
                ", lastUpdateTime=" + lastUpdateTime +
                '}';
    }
}
