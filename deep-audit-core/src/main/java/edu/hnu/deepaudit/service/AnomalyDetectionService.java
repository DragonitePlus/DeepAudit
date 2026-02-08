package edu.hnu.deepaudit.service;

import ai.onnxruntime.OnnxTensor;
import ai.onnxruntime.OrtEnvironment;
import ai.onnxruntime.OrtSession;
import edu.hnu.deepaudit.analysis.FeatureExtractor;
import edu.hnu.deepaudit.analysis.SqlDeepAnalyzer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisPool;

import java.io.File;
import java.nio.FloatBuffer;
import java.time.LocalDateTime;
import java.util.Collections;

/**
 * Anomaly Detection Service (Hybrid Scoring Version)
 */
public class AnomalyDetectionService {

    private static final Logger log = LoggerFactory.getLogger(AnomalyDetectionService.class);

    private JedisPool jedisPool;
    private OrtSession session;
    private OrtEnvironment env;
    private String currentModelPath;

    public AnomalyDetectionService() {
    }

    public synchronized void reloadModel(String modelPath) {
        if (modelPath == null || modelPath.equals(currentModelPath)) {
            return;
        }
        log.info("Reloading AI Model from: {}", modelPath);
        try {
            if (session != null) {
                session.close();
                session = null;
            }
            if (env != null) {
                env.close();
                env = null;
            }
            this.env = OrtEnvironment.getEnvironment();
            File modelFile = new File(modelPath);
            if (modelFile.exists()) {
                this.session = env.createSession(modelPath, new OrtSession.SessionOptions());
                this.currentModelPath = modelPath;
                log.info("DeepAudit: ONNX Model reloaded successfully.");
            } else {
                log.warn("DeepAudit: ONNX Model NOT found at {}. AI detection disabled.", modelPath);
            }
        } catch (Exception e) {
            log.error("Failed to reload AI model", e);
        }
    }

    public void setJedisPool(JedisPool jedisPool) {
        this.jedisPool = jedisPool;
    }

    /**
     * Synchronous Risk Detection using Hybrid Scoring (AI + Rules)
     */
    public double detectRisk(String userId, LocalDateTime eventTime, String sql, SqlDeepAnalyzer.SqlFeatures astFeatures) {
        if (session == null) {
            // 如果模型未加载，依靠规则兜底
            return calculateRuleBasedRisk(sql, astFeatures, 0.0);
        }

        try {
            // 1. Get Real Frequency from Redis
            int freq = 1;
            if (jedisPool != null) {
                try (Jedis jedis = jedisPool.getResource()) {
                    long currentMinute = System.currentTimeMillis() / 60000;
                    String key = "freq:" + userId + ":" + currentMinute;
                    freq = (int) jedis.incr(key);
                    if (freq == 1) {
                        jedis.expire(key, 60);
                    }
                } catch (Exception e) {
                    log.warn("Redis freq check failed: {}", e.getMessage());
                }
            }

            // 2. SQL Type Weight
            int sqlTypeWeight = 1;
            String lowerSql = sql.toLowerCase();
            if (lowerSql.contains("drop ") || lowerSql.contains("truncate ") || lowerSql.contains("grant ")) sqlTypeWeight = 5;
            else if (lowerSql.contains("update ") || lowerSql.contains("delete ") || lowerSql.contains("insert ")) sqlTypeWeight = 3;

            // 3. Feature Engineering (8 Dimensions)
            float[] features = FeatureExtractor.extractFeatures(
                    eventTime, freq, sqlTypeWeight,
                    astFeatures.conditionCount, astFeatures.joinCount, astFeatures.nestedLevel, astFeatures.hasAlwaysTrueCondition
            );

            // 4. Run Inference
            long[] shape = new long[]{1, features.length};
            try (OnnxTensor tensor = OnnxTensor.createTensor(env, FloatBuffer.wrap(features), shape)) {
                OrtSession.Result result = session.run(Collections.singletonMap("float_input", tensor));
                float[][] scores = (float[][]) result.get(1).getValue();
                float rawScore = scores[0][0]; // Isolation Forest Score (typically -0.5 to 0.5)

                // =========================================================
                // 🔥 核心修正：混合评分逻辑 (Hybrid Scoring)
                // =========================================================

                double finalRisk = 0.0;

                // A. AI 模型打分 (大幅增强敏感度)
                if (rawScore < 0) {
                    // 只要是负数(异常)，起步就是 40 分。越负越危险。
                    // 例如 -0.03 -> 40 + 9 = 49 (观察)
                    // 例如 -0.20 -> 40 + 60 = 100 (阻断)
                    finalRisk = 40.0 + (Math.abs(rawScore) * 300.0);
                } else if (rawScore < 0.2) {
                    // 0 到 0.2 之间可能是边界
                    finalRisk = 20.0 + (0.2 - rawScore) * 100.0;
                }

                // B. 硬规则兜底 (Safety Net)
                finalRisk = calculateRuleBasedRisk(sql, astFeatures, finalRisk);

                // 归一化到 0-100
                finalRisk = Math.min(100.0, Math.max(0.0, finalRisk));

                log.info("AI Anomaly Detected - User: {}, RawScore: {}, CalculatedRisk: {}", userId, rawScore, finalRisk);

                if (finalRisk > 0) {
                    updateRiskState(userId, finalRisk);
                }

                return finalRisk;
            }
        } catch (Exception e) {
            log.error("AI Inference Failed", e);
            return calculateRuleBasedRisk(sql, astFeatures, 0.0); // Fallback to rules
        }
    }

    /**
     * 规则引擎兜底：确保高危特征必死
     */
    private double calculateRuleBasedRisk(String sql, SqlDeepAnalyzer.SqlFeatures ast, double currentRisk) {
        String lowerSql = sql.toLowerCase();

        // 规则 1: 注入必杀 (1=1)
        if (ast.hasAlwaysTrueCondition) {
            return 100.0; // 直接拉满
        }

        // 规则 2: DDL 高危操作 (DROP/TRUNCATE)
        if (lowerSql.contains("drop ") || lowerSql.contains("truncate ") || lowerSql.contains("grant ")) {
            return Math.max(currentRisk, 80.0); // 至少 80 分
        }

        // 规则 3: 深度嵌套 (疑似自动化攻击)
        if (ast.nestedLevel >= 3) {
            currentRisk = Math.max(currentRisk, 60.0);
        }

        // 规则 4: 复杂关联 (疑似 DoS)
        if (ast.joinCount >= 3) {
            currentRisk = Math.max(currentRisk, 50.0);
        }

        return currentRisk;
    }

    private void updateRiskState(String userId, double riskScore) {
        if (jedisPool == null) return;
        try (Jedis jedis = jedisPool.getResource()) {
            jedis.hincrByFloat("audit:risk:" + userId, "score", riskScore);
        } catch (Exception e) { /* ignore */ }
    }

    public void close() {
        try {
            if (session != null) session.close();
            if (env != null) env.close();
        } catch (Exception e) { /* ignore */ }
    }
}