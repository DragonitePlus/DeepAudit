package edu.hnu.deepaudit.simulation;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import edu.hnu.deepaudit.interception.UserContext;
import edu.hnu.deepaudit.mapper.biz.SysUserMapper;
import edu.hnu.deepaudit.mapper.sys.SysAuditLogMapper;
import edu.hnu.deepaudit.mapper.sys.SysRiskRuleMapper;
import edu.hnu.deepaudit.mapper.sys.SysUserRiskProfileMapper;
import edu.hnu.deepaudit.model.SysAuditLog;
import edu.hnu.deepaudit.model.SysRiskRule;
import edu.hnu.deepaudit.model.SysUser;
import edu.hnu.deepaudit.model.SysUserRiskProfile;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DuplicateKeyException;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.UUID;

@SpringBootTest
public class TestDataGeneratorTest {

    @Autowired
    private SysUserMapper sysUserMapper;

    @Autowired
    private SysUserRiskProfileMapper sysUserRiskProfileMapper;

    @Autowired
    private SysAuditLogMapper sysAuditLogMapper;

    @Autowired
    private SysRiskRuleMapper sysRiskRuleMapper;

    private final Random random = new Random();

    @Test
    public void generateTestData() {
        System.out.println(">>> 开始生成测试数据...");
        
        // 设置系统管理员上下文，避免被记录为 unknown 用户
        UserContext.setUserId("system_admin");

        try {
            // 1. 生成基础规则
            generateRiskRules();

            // 2. 生成 1000+ 用户
            List<String> userIds = generateUsers(1000);

            // 3. 为每个用户生成风险画像 (确保 1:1 对应)
            generateRiskProfiles(userIds);

            // 4. 生成模拟审计日志 (用于趋势图)
            generateAuditLogs(userIds, 2000);

            System.out.println(">>> 测试数据生成完成！");
        } finally {
            // 清理 system_admin 产生的初始化审计日志
            cleanSystemAdminLogs();
            UserContext.clear();
        }
    }
    
    private void cleanSystemAdminLogs() {
        System.out.println(">>> 正在清理系统生成操作产生的审计日志...");
        QueryWrapper<SysAuditLog> wrapper1 = new QueryWrapper<>();
        wrapper1.eq("app_user_id", "system_admin");
        int deleted = sysAuditLogMapper.delete(wrapper1);
        System.out.println(">>> 已删除 " + deleted + " 条系统日志。");
        System.out.println(">>> 正在清理系统生成操作产生的系统用户...");
        QueryWrapper<SysUserRiskProfile> wrapper2 = new QueryWrapper<>();
        wrapper2.eq("app_user_id", "system_admin");
        deleted = sysUserRiskProfileMapper.delete(wrapper2);
        System.out.println(">>> 已删除 " + deleted + " 个系统用户。");
    }

    private void generateRiskRules() {
        System.out.println("正在初始化风险规则...");
        createRule("Phone Number", "1[3-9]\\d{9}", 10);
        createRule("Email", "[a-zA-Z0-9._%+-]+@[a-zA-Z0-9.-]+\\.[a-zA-Z]{2,}", 5);
        createRule("ID Card", "\\d{17}[\\d|x]|\\d{15}", 20);
        createRule("Credit Card", "\\d{4}-?\\d{4}-?\\d{4}-?\\d{4}", 15);
    }

    private void createRule(String name, String regex, int score) {
        SysRiskRule rule = new SysRiskRule();
        rule.setRuleName(name);
        rule.setRegex(regex);
        rule.setScore(score);
        try {
            sysRiskRuleMapper.insert(rule);
        } catch (DuplicateKeyException e) {
            System.out.println("规则已存在，跳过: " + name);
        }
    }

    private List<String> generateUsers(int count) {
        System.out.println("正在生成 " + count + " 个用户...");
        List<String> userIds = new ArrayList<>();
        
        for (int i = 0; i < count; i++) {
            String suffix = String.format("%04d", i);
            SysUser user = new SysUser();
            user.setUsername("user_" + suffix);
            user.setEmail("user" + suffix + "@example.com");
            user.setPhone("1380000" + suffix);
            
            try {
                sysUserMapper.insert(user);
                // 使用 username 作为 appUserId
                userIds.add(user.getUsername());
            } catch (DuplicateKeyException e) {
                // 如果用户已存在，也添加到列表中用于后续数据生成
                userIds.add("user_" + suffix);
            }
        }
        return userIds;
    }

    private void generateRiskProfiles(List<String> userIds) {
        System.out.println("正在生成用户风险画像 (确保全量覆盖)...");
        for (String userId : userIds) {
            SysUserRiskProfile profile = new SysUserRiskProfile();
            profile.setAppUserId(userId);
            profile.setLastUpdateTime(LocalDateTime.now());
            
            // 随机风险分布
            // 80% Normal, 15% Observation, 5% Blocked
            int rand = random.nextInt(100);
            if (rand < 80) {
                profile.setRiskLevel("NORMAL");
                profile.setCurrentScore(random.nextInt(40));
                profile.setDescription("正常用户");
            } else if (rand < 95) {
                profile.setRiskLevel("OBSERVATION");
                profile.setCurrentScore(40 + random.nextInt(60)); // 40-99
                profile.setDescription("风险观察中 - 频繁查询");
            } else {
                profile.setRiskLevel("BLOCKED");
                profile.setCurrentScore(100 + random.nextInt(100)); // 100-199
                profile.setDescription("已阻断 - 疑似数据泄露");
            }
            
            try {
                sysUserRiskProfileMapper.insert(profile);
            } catch (DuplicateKeyException e) {
                sysUserRiskProfileMapper.updateById(profile);
            }
        }
    }

    private void generateAuditLogs(List<String> userIds, int count) {
        System.out.println("正在生成 " + count + " 条模拟审计日志...");
        for (int i = 0; i < count; i++) {
            SysAuditLog log = new SysAuditLog();
            log.setTraceId(UUID.randomUUID().toString());
            String userId = userIds.get(random.nextInt(userIds.size()));
            log.setAppUserId(userId);
            
            // 随机时间 (过去 24 小时内)
            log.setCreateTime(LocalDateTime.now().minusMinutes(random.nextInt(24 * 60)));
            
            // 随机操作
            int type = random.nextInt(3);
            if (type == 0) {
                log.setSqlTemplate("SELECT * FROM sys_user WHERE id = ?");
                log.setTableNames("sys_user");
                log.setRiskScore(random.nextInt(20));
            } else if (type == 1) {
                log.setSqlTemplate("SELECT * FROM sys_order WHERE user_id = ?");
                log.setTableNames("sys_order");
                log.setRiskScore(random.nextInt(10));
            } else {
                log.setSqlTemplate("SELECT * FROM sys_sensitive_table"); // 高危
                log.setTableNames("sys_sensitive_table");
                log.setRiskScore(50 + random.nextInt(50));
            }
            
            log.setResultCount(random.nextInt(100));
            log.setActionTaken(log.getRiskScore() > 80 ? "BLOCK" : "PASS");
            log.setClientIp("192.168.1." + random.nextInt(255));
            log.setExecutionTime((long) random.nextInt(500));
            
            sysAuditLogMapper.insert(log);
        }
    }
}
