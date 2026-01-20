package edu.hnu.deepaudit.model;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

@Data
@TableName("sys_risk_rule")
public class SysRiskRule {
    @TableId(type = IdType.AUTO)
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
}
