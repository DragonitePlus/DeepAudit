package edu.hnu.deepaudit.control;

import edu.hnu.deepaudit.config.RiskProperties;
import edu.hnu.deepaudit.mapper.sys.SysUserRiskProfileMapper;
import edu.hnu.deepaudit.model.SysUserRiskProfile;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.scripting.support.ResourceScriptSource;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.CompletableFuture;

/**
 * Risk State Machine
 * Based on RAdAC (Risk-Adaptive Access Control)
 * Uses Redis Lua scripts for atomic state transitions.
 * Refactored to be a POJO for Plugin use.
 */
public class RiskStateMachine {

    private static final Logger log = LoggerFactory.getLogger(RiskStateMachine.class);

    private StringRedisTemplate redisTemplate;
    private RiskProperties riskProperties;
    private SysUserRiskProfileMapper sysUserRiskProfileMapper;

    private DefaultRedisScript<List> riskScript;

    private static final String PROFILE_KEY_PREFIX = "audit:risk:";
    private static final String WINDOW_KEY_PREFIX = "audit:window:";

    // Manual setter injection
    public void setRedisTemplate(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    public void setRiskProperties(RiskProperties riskProperties) {
        this.riskProperties = riskProperties;
    }

    public void setSysUserRiskProfileMapper(SysUserRiskProfileMapper mapper) {
        this.sysUserRiskProfileMapper = mapper;
    }

    public void init() {
        riskScript = new DefaultRedisScript<>();
        riskScript.setScriptSource(new ResourceScriptSource(new ClassPathResource("scripts/risk_control.lua")));
        riskScript.setResultType(List.class);
    }

    /**
     * Check user status and perform state transition
     * @param userId App User ID
     * @param currentRiskScore Current instantaneous risk score
     * @return Action (ALLOW, WARNING, BLOCK)
     */
    public String checkStatus(String userId, int currentRiskScore) {
        if (redisTemplate == null || riskProperties == null) {
            log.warn("RiskStateMachine not initialized properly. Allowing by default.");
            return "ALLOW";
        }

        String profileKey = PROFILE_KEY_PREFIX + userId;
        String windowKey = WINDOW_KEY_PREFIX + userId;
        long now = System.currentTimeMillis() / 1000;

        // Lua Script Execution
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
            
            // Async persist to MySQL (Simulated Async)
            CompletableFuture.runAsync(() -> persistRiskProfile(userId, Integer.parseInt(score), state));

            return action;
        }

        return "ALLOW"; // Default ALLOW
    }

    /**
     * Persist risk profile to DB
     */
    private void persistRiskProfile(String userId, int score, String state) {
        if (sysUserRiskProfileMapper == null) return;
        
        try {
            SysUserRiskProfile profile = new SysUserRiskProfile();
            profile.setAppUserId(userId);
            profile.setCurrentScore(score);
            profile.setRiskLevel(state);
            profile.setLastUpdateTime(LocalDateTime.now());
            
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
