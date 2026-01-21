package edu.hnu.deepaudit.control;

import edu.hnu.deepaudit.config.RiskProperties;
import edu.hnu.deepaudit.mapper.SysUserRiskProfileMapper;
import edu.hnu.deepaudit.model.SysUserRiskProfile;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.scheduling.annotation.Async;
import org.springframework.scripting.support.ResourceScriptSource;
import org.springframework.stereotype.Component;

import jakarta.annotation.PostConstruct;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;

/**
 * 风险状态机
 * 基于 RAdAC (风险自适应访问控制) 理念
 * 使用 Redis Lua 脚本实现原子性状态流转
 */
@Component
public class RiskStateMachine {

    private static final Logger log = LoggerFactory.getLogger(RiskStateMachine.class);

    @Autowired
    private StringRedisTemplate redisTemplate;

    @Autowired
    private RiskProperties riskProperties;
    
    @Autowired
    private SysUserRiskProfileMapper sysUserRiskProfileMapper;

    private DefaultRedisScript<List> riskScript;

    private static final String PROFILE_KEY_PREFIX = "audit:risk:";
    private static final String WINDOW_KEY_PREFIX = "audit:window:";

    @PostConstruct
    public void init() {
        riskScript = new DefaultRedisScript<>();
        riskScript.setScriptSource(new ResourceScriptSource(new ClassPathResource("scripts/risk_control.lua")));
        riskScript.setResultType(List.class);
    }

    /**
     * 检查用户状态并执行状态流转
     * @param userId 应用层用户ID
     * @param currentRiskScore 当前操作的瞬时风险分
     * @return 动作 (ALLOW, WARNING, BLOCK)
     */
    public String checkStatus(String userId, int currentRiskScore) {
        String profileKey = PROFILE_KEY_PREFIX + userId;
        String windowKey = WINDOW_KEY_PREFIX + userId;
        long now = System.currentTimeMillis() / 1000;

        // Lua 脚本参数
        List<String> result = redisTemplate.execute(riskScript, 
                Arrays.asList(profileKey, windowKey),
                String.valueOf(currentRiskScore),
                String.valueOf(now),
                String.valueOf(riskProperties.getDecayRate()),
                String.valueOf(riskProperties.getObservationThreshold()),
                String.valueOf(riskProperties.getBlockThreshold()),
                String.valueOf(riskProperties.getWindowTtl())
        );

        if (result != null && result.size() >= 3) {
            String state = result.get(0);
            String score = result.get(1);
            String action = result.get(2);
            
            // 异步持久化到 MySQL
            persistRiskProfile(userId, Integer.parseInt(score), state);

            return action;
        }

        return "ALLOW"; // 默认放行
    }

    /**
     * 异步持久化风险画像到数据库
     * 避免影响主链路性能
     */
    @Async
    public void persistRiskProfile(String userId, int score, String state) {
        try {
            SysUserRiskProfile profile = new SysUserRiskProfile();
            profile.setAppUserId(userId);
            profile.setCurrentScore(score);
            profile.setRiskLevel(state);
            profile.setLastUpdateTime(LocalDateTime.now());
            
            // 使用 saveOrUpdate 语义 (MySQL ON DUPLICATE KEY UPDATE 效果在 MyBatis Plus 中通常需要自定义或先查后插)
            // 这里为了简化，我们先查询是否存在
            SysUserRiskProfile existing = sysUserRiskProfileMapper.selectById(userId);
            if (existing != null) {
                sysUserRiskProfileMapper.updateById(profile);
            } else {
                sysUserRiskProfileMapper.insert(profile);
            }
        } catch (Exception e) {
            log.error("Failed to persist risk profile for user: " + userId, e);
        }
    }
}
