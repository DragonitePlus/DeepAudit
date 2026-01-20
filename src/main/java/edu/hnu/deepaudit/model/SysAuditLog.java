package edu.hnu.deepaudit.model;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@TableName("sys_audit_log")
public class SysAuditLog {
    @TableId(type = IdType.ASSIGN_UUID)
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
    private Integer resultCount;
    
    /**
     * Action Taken (PASS/BLOCK)
     */
    private String actionTaken;
    
    private LocalDateTime createTime;
}
