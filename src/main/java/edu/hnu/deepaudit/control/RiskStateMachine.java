package edu.hnu.deepaudit.control;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;

@Component
public class RiskStateMachine {

    @Autowired
    private RedisTemplate<String, Object> redisTemplate;

    private static final String STATUS_PREFIX = "sec:status:";

    /**
     * Check user status and determine action
     * @param userId App User ID
     * @param riskScore Current Risk Score
     * @return Action (PASS/BLOCK)
     */
    public String checkStatus(String userId, int riskScore) {
        // TODO: Implement RAdAC Logic with Redis
        // 1. Get status from Redis
        // String key = STATUS_PREFIX + userId;
        // 2. State transition logic
        return "PASS";
    }
}
