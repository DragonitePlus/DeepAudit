package edu.hnu.deepaudit.control;

import edu.hnu.deepaudit.config.DeepAuditConfig;
import edu.hnu.deepaudit.model.SysUserRiskProfile;
import edu.hnu.deepaudit.persistence.JdbcRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisPool;
import redis.clients.jedis.JedisPubSub;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

/**
 * Risk State Machine (POJO Version)
 * Uses Jedis for Redis operations.
 */
public class RiskStateMachine {

    private static final Logger log = LoggerFactory.getLogger(RiskStateMachine.class);

    private JedisPool jedisPool;
    private DeepAuditConfig config;
    private JdbcRepository jdbcRepository;
    // Callback to reload model when config changes
    private Runnable onConfigUpdate; 

    private String luaScriptSha;

    private static final String PROFILE_KEY_PREFIX = "audit:risk:";
    private static final String WINDOW_KEY_PREFIX = "audit:window:";
    private static final String CONFIG_CHANNEL = "deepaudit:config:update";

    public void setJedisPool(JedisPool jedisPool) {
        this.jedisPool = jedisPool;
    }

    public void setConfig(DeepAuditConfig config) {
        this.config = config;
    }

    public void setJdbcRepository(JdbcRepository jdbcRepository) {
        this.jdbcRepository = jdbcRepository;
    }
    
    public void setOnConfigUpdate(Runnable onConfigUpdate) {
        this.onConfigUpdate = onConfigUpdate;
    }

    public void init() {
        // 1. Load Lua script
        loadLuaScript();
        
        // 2. Start Redis Subscriber for Config Updates
        startConfigSubscriber();
    }
    
    private void loadLuaScript() {
        try (InputStream is = getClass().getClassLoader().getResourceAsStream("scripts/risk_control.lua")) {
            if (is == null) {
                log.error("Lua script not found at scripts/risk_control.lua");
                return;
            }
            String script = new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8))
                    .lines().collect(Collectors.joining("\n"));
            
            try (Jedis jedis = jedisPool.getResource()) {
                this.luaScriptSha = jedis.scriptLoad(script);
                log.info("Loaded risk control Lua script, SHA: {}", luaScriptSha);
            }
        } catch (IOException e) {
            log.error("Failed to load Lua script", e);
        }
    }
    
    private void startConfigSubscriber() {
        new Thread(() -> {
            while (true) {
                try (Jedis jedis = jedisPool.getResource()) {
                    jedis.subscribe(new JedisPubSub() {
                        @Override
                        public void onMessage(String channel, String message) {
                            if (CONFIG_CHANNEL.equals(channel)) {
                                log.info("Received config update: {}", message);
                                boolean updated = config.updateFromJson(message);
                                if (updated && onConfigUpdate != null) {
                                    onConfigUpdate.run();
                                }
                            }
                        }
                    }, CONFIG_CHANNEL);
                } catch (Exception e) {
                    log.error("Redis Subscriber failed, retrying in 5s...", e);
                    try { Thread.sleep(5000); } catch (InterruptedException ignored) {}
                }
            }
        }, "DeepAudit-Config-Subscriber").start();
    }

    public String checkStatus(String userId, int currentRiskScore) {
        if (jedisPool == null || config == null) {
            log.warn("RiskStateMachine not initialized. Defaulting to ALLOW.");
            return "ALLOW";
        }

        String profileKey = PROFILE_KEY_PREFIX + userId;
        String windowKey = WINDOW_KEY_PREFIX + userId;
        long now = System.currentTimeMillis() / 1000;

        try (Jedis jedis = jedisPool.getResource()) {
            if (luaScriptSha == null) {
                // Retry loading script if SHA is missing (e.g. Redis restart)
                init();
                if (luaScriptSha == null) return "ALLOW";
            }
            
            Object resultObj = jedis.evalsha(luaScriptSha, 
                Arrays.asList(profileKey, windowKey), 
                Arrays.asList(
                    String.valueOf(currentRiskScore),
                    String.valueOf(now),
                    String.valueOf(config.getDecayRate()),
                    String.valueOf(config.getObservationThreshold()),
                    String.valueOf(config.getBlockThreshold()),
                    String.valueOf(config.getWindowTtl())
                )
            );

            if (resultObj instanceof List) {
                List<?> result = (List<?>) resultObj;
                if (result.size() >= 3) {
                    String state = (String) result.get(0);
                    String scoreStr = (String) result.get(1);
                    String action = (String) result.get(2);
                    
                    double score = Double.parseDouble(scoreStr);
                    
                    // Async persist
                    CompletableFuture.runAsync(() -> persistRiskProfile(userId, (int)score, state));
                    
                    return action;
                }
            }
        } catch (Exception e) {
            log.error("Error executing risk script", e);
            // On error, fail open (ALLOW) to avoid blocking legitimate traffic due to infrastructure issues
        }

        return "ALLOW";
    }

    private void persistRiskProfile(String userId, int score, String state) {
        if (jdbcRepository == null) return;
        
        try {
            SysUserRiskProfile profile = new SysUserRiskProfile();
            profile.setAppUserId(userId);
            profile.setCurrentScore(score);
            profile.setRiskLevel(state);
            profile.setLastUpdateTime(LocalDateTime.now());
            
            jdbcRepository.saveOrUpdateRiskProfile(profile);
        } catch (Exception e) {
            log.error("Failed to async persist risk profile", e);
        }
    }
}
