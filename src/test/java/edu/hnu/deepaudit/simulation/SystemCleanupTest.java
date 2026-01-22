package edu.hnu.deepaudit.simulation;

import edu.hnu.deepaudit.mapper.sys.SysAuditLogMapper;
import edu.hnu.deepaudit.mapper.sys.SysRiskRuleMapper;
import edu.hnu.deepaudit.mapper.biz.SysUserMapper;
import edu.hnu.deepaudit.mapper.sys.SysUserRiskProfileMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest
public class SystemCleanupTest {

    @Autowired
    private SysUserMapper sysUserMapper;

    @Autowired
    private SysUserRiskProfileMapper sysUserRiskProfileMapper;

    @Autowired
    private SysAuditLogMapper sysAuditLogMapper;

    @Autowired
    private SysRiskRuleMapper sysRiskRuleMapper;

    @Test
    public void cleanAll() {
        System.out.println(">>> 开始系统数据清理...");
        
        // 删除用户风险画像
        sysUserRiskProfileMapper.delete(null);
        System.out.println("- 已清空 sys_user_risk_profile");

        // 删除审计日志
        sysAuditLogMapper.delete(null);
        System.out.println("- 已清空 sys_audit_log");

        // 删除风险规则
        sysRiskRuleMapper.delete(null);
        System.out.println("- 已清空 sys_risk_rule");

        // 删除系统用户 (注意外键约束，这里放在最后或根据依赖关系调整)
        sysUserMapper.delete(null);
        System.out.println("- 已清空 sys_user");

        System.out.println(">>> 系统清理完成，数据库已重置。");
    }
}
