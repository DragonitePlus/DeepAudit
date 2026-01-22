package edu.hnu.deepaudit.control;

import edu.hnu.deepaudit.config.RiskProperties;
import edu.hnu.deepaudit.model.SysAuditLog;
import edu.hnu.deepaudit.model.SysUserRiskProfile;
import edu.hnu.deepaudit.persistence.AuditPersistenceService;
import edu.hnu.deepaudit.mapper.sys.SysUserRiskProfileMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.connection.Message;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.listener.KeyExpirationEventMessageListener;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * 4.3 “风险观察窗”的被动触发机制（Redis Key-space Notifications）
 * 监听 Redis 键过期事件，处理观察窗结束时的逻辑。
 */
@Component
public class RiskWindowExpirationListener extends KeyExpirationEventMessageListener {

    private static final Logger log = LoggerFactory.getLogger(RiskWindowExpirationListener.class);

    @Autowired
    private StringRedisTemplate redisTemplate;

    @Autowired
    private RiskProperties riskProperties;

    @Autowired
    private AuditPersistenceService auditPersistenceService;

    @Autowired
    private SysUserRiskProfileMapper sysUserRiskProfileMapper;

    public RiskWindowExpirationListener(RedisMessageListenerContainer listenerContainer) {
        super(listenerContainer);
    }

    @Override
    public void onMessage(Message message, byte[] pattern) {
        String expiredKey = message.toString();
        // 仅处理观察窗 Key: "audit:window:{userId}"
        if (expiredKey.startsWith("audit:window:")) {
            String userId = expiredKey.substring("audit:window:".length());
            handleWindowExpiration(userId);
        }
    }

    private void handleWindowExpiration(String userId) {
        log.info("Risk observation window expired for user: {}", userId);

        // 重新检查用户分数
        String profileKey = "audit:risk:" + userId;
        Object scoreObj = redisTemplate.opsForHash().get(profileKey, "score");
        
        if (scoreObj == null) {
            log.warn("Risk profile not found for user: {} upon window expiration", userId);
            return;
        }

        double score;
        try {
            score = Double.parseDouble(scoreObj.toString());
        } catch (NumberFormatException e) {
            log.error("Invalid score format for user: {}", userId, e);
            return;
        }

        // 如果窗口结束时，分数依然高于观察阈值，说明整改失败
        if (score >= riskProperties.getObservationThreshold()) {
            log.warn("User {} failed to reduce risk score ({}) within observation window. Blocking user.", userId, score);
            
            // 执行被动阻断逻辑：升级状态为 BLOCKED
            redisTemplate.opsForHash().put(profileKey, "state", "BLOCKED");
            
            // 同时更新数据库中的状态 (持久化)
            updateDbStatus(userId, "BLOCKED", (int)score);

            // 记录审计日志：观察期整改失败
            logAudit(userId, "Window_Expired_Risk_High", "BLOCK", (int)score);
        } else {
            log.info("User {} successfully reduced risk score ({}) within observation window. Returning to NORMAL.", userId, score);
            // 可以选择显式设为 NORMAL，或者让其自然保持（如果当前状态不是 BLOCKED）
            redisTemplate.opsForHash().put(profileKey, "state", "NORMAL");
            updateDbStatus(userId, "NORMAL", (int)score);
        }
    }

    private void updateDbStatus(String userId, String status, int score) {
        try {
            SysUserRiskProfile profile = sysUserRiskProfileMapper.selectById(userId);
            if (profile == null) {
                profile = new SysUserRiskProfile();
                profile.setAppUserId(userId);
            }
            profile.setRiskLevel(status);
            profile.setCurrentScore(score);
            profile.setLastUpdateTime(LocalDateTime.now());
            profile.setDescription(status.equals("BLOCKED") ? "Passive Block: Risk window expired with high score" : "Risk window expired, returned to normal");
            
            if (sysUserRiskProfileMapper.updateById(profile) == 0) {
                sysUserRiskProfileMapper.insert(profile);
            }
        } catch (Exception e) {
            log.error("Failed to update DB status for user: {}", userId, e);
        }
    }

    private void logAudit(String userId, String operation, String action, int score) {
        SysAuditLog auditLog = new SysAuditLog();
        auditLog.setTraceId(UUID.randomUUID().toString());
        auditLog.setAppUserId(userId);
        auditLog.setSqlTemplate("SYSTEM_EVENT: " + operation);
        auditLog.setTableNames("sys_user_risk_profile");
        auditLog.setRiskScore(score);
        auditLog.setActionTaken(action);
        auditLog.setCreateTime(LocalDateTime.now());
        auditLog.setClientIp("127.0.0.1");
        auditLog.setExecutionTime(0L);
        auditLog.setExtraInfo("Passive trigger via Redis Key Expiration");
        
        auditPersistenceService.saveLog(auditLog);
    }
}
