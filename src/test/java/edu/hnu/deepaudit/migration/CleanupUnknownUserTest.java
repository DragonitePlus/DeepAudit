package edu.hnu.deepaudit.migration;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

import javax.sql.DataSource;

@SpringBootTest
public class CleanupUnknownUserTest {

    @Autowired
    @Qualifier("systemDataSource")
    private DataSource systemDataSource;

    @Test
    public void deleteUnknownUser() {
        System.out.println(">>> 开始清理 unknown 用户数据...");
        
        JdbcTemplate sysJdbc = new JdbcTemplate(systemDataSource);
        
        try {
            int riskProfilesDeleted = sysJdbc.update("DELETE FROM sys_user_risk_profile WHERE app_user_id = 'unknown'");
            System.out.println("已删除 sys_user_risk_profile 中的 unknown 记录: " + riskProfilesDeleted + " 条");
            
            int auditLogsDeleted = sysJdbc.update("DELETE FROM sys_audit_log WHERE app_user_id = 'unknown'");
            System.out.println("已删除 sys_audit_log 中的 unknown 记录: " + auditLogsDeleted + " 条");
            
        } catch (Exception e) {
            System.err.println("清理失败: " + e.getMessage());
        }

        System.out.println(">>> 清理完成!");
    }
}
