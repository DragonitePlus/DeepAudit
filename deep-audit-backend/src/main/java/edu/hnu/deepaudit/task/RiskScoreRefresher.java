package edu.hnu.deepaudit.task;

import edu.hnu.deepaudit.config.RiskProperties;
import edu.hnu.deepaudit.mapper.sys.SysUserRiskProfileMapper;
import edu.hnu.deepaudit.model.SysUserRiskProfile;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.Cursor;
import org.springframework.data.redis.core.ScanOptions;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.scripting.support.ResourceScriptSource;
import org.springframework.stereotype.Component;

import jakarta.annotation.PostConstruct;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Active Risk Score Decay Task
 * Periodically scans Redis for active risk profiles and triggers decay calculation.
 * Ensures MySQL data is eventually consistent with time-decayed scores.
 */
@Component
public class RiskScoreRefresher {

    private static final Logger log = LoggerFactory.getLogger(RiskScoreRefresher.class);

    @Autowired
    private StringRedisTemplate redisTemplate;

    @Autowired
    private SysUserRiskProfileMapper sysUserRiskProfileMapper;

    @Autowired
    private RiskProperties riskProperties;

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
     * Run every 10 minutes (600,000 ms)
     * Adjust frequency based on system load and real-time requirements.
     */
    @Scheduled(fixedRate = 600000)
    public void refreshRiskScores() {
        log.info("Starting active risk score decay refresh...");
        long start = System.currentTimeMillis();
        int processedCount = 0;
        int updatedCount = 0;

        // Use SCAN to iterate over keys safely
        ScanOptions options = ScanOptions.scanOptions().match(PROFILE_KEY_PREFIX + "*").count(100).build();
        
        try (Cursor<String> cursor = redisTemplate.scan(options)) {
            while (cursor.hasNext()) {
                String key = cursor.next();
                String userId = key.replace(PROFILE_KEY_PREFIX, "");
                
                try {
                    boolean updated = processUser(userId);
                    if (updated) updatedCount++;
                    processedCount++;
                } catch (Exception e) {
                    log.error("Failed to process decay for user: " + userId, e);
                }
            }
        } catch (Exception e) {
            log.error("Error scanning Redis keys", e);
        }

        long duration = System.currentTimeMillis() - start;
        log.info("Risk refresh completed in {} ms. Processed: {}, Updated DB: {}", duration, processedCount, updatedCount);
    }

    /**
     * Execute decay logic for a single user
     * @return true if DB was updated
     */
    private boolean processUser(String userId) {
        String profileKey = PROFILE_KEY_PREFIX + userId;
        String windowKey = WINDOW_KEY_PREFIX + userId;
        long now = System.currentTimeMillis() / 1000;

        // Execute Lua script with 0 incoming score to trigger pure decay
        List<String> result = redisTemplate.execute(riskScript,
                Arrays.asList(profileKey, windowKey),
                "0", // incoming_score = 0
                String.valueOf(now),
                String.valueOf(riskProperties.getDecayRate()),
                String.valueOf(riskProperties.getObservationThreshold()),
                String.valueOf(riskProperties.getBlockThreshold()),
                String.valueOf(riskProperties.getWindowTtl())
        );

        if (result != null && result.size() >= 3) {
            String state = result.get(0);
            String scoreStr = result.get(1);
            // String action = result.get(2); // Action irrelevant for background refresh

            int score = (int) Double.parseDouble(scoreStr);

            // Optimization: Only update DB if score is non-zero or if we need to clear a high score
            // For efficiency, we assume Redis is source of truth, so we always sync active keys
            // But if score is 0 and state is NORMAL, we might want to skip or delete from DB?
            // Here we stick to syncing to keep DB view accurate.
            
            updateDb(userId, score, state);
            
            // Cleanup: If score is 0 and NORMAL, maybe remove from Redis to save memory?
            // (Optional optimization, let TTL handle it or keep for history)
            if (score == 0 && "NORMAL".equals(state)) {
                // redisTemplate.delete(profileKey); // Uncomment if aggressive cleanup is needed
            }
            
            return true;
        }
        return false;
    }

    private void updateDb(String userId, int score, String state) {
        SysUserRiskProfile profile = new SysUserRiskProfile();
        profile.setAppUserId(userId);
        profile.setCurrentScore(score);
        profile.setRiskLevel(state);
        profile.setLastUpdateTime(LocalDateTime.now());
        
        // Use MyBatis Plus Insert or Update
        // Note: This is not batch optimized in this simple impl, but sufficient for background task
        SysUserRiskProfile existing = sysUserRiskProfileMapper.selectById(userId);
        if (existing != null) {
            // Only update if changed significantly? 
            // For now, update timestamp at least to show freshness
            sysUserRiskProfileMapper.updateById(profile);
        } else {
            sysUserRiskProfileMapper.insert(profile);
        }
    }
}
