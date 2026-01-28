package edu.hnu.deepaudit.model;

import java.util.Objects;

public class SysRiskRule {
    private Long id;
    
    private String ruleName;
    
    /**
     * Regular Expression for sensitive data
     */
    private String regex;
    
    /**
     * Risk Score for single match
     */
    private Integer score;

    public SysRiskRule() {
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getRuleName() {
        return ruleName;
    }

    public void setRuleName(String ruleName) {
        this.ruleName = ruleName;
    }

    public String getRegex() {
        return regex;
    }

    public void setRegex(String regex) {
        this.regex = regex;
    }

    public Integer getScore() {
        return score;
    }

    public void setScore(Integer score) {
        this.score = score;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        SysRiskRule that = (SysRiskRule) o;
        return Objects.equals(id, that.id) &&
                Objects.equals(ruleName, that.ruleName) &&
                Objects.equals(regex, that.regex) &&
                Objects.equals(score, that.score);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id, ruleName, regex, score);
    }

    @Override
    public String toString() {
        return "SysRiskRule{" +
                "id=" + id +
                ", ruleName='" + ruleName + '\'' +
                ", regex='" + regex + '\'' +
                ", score=" + score +
                '}';
    }
}
