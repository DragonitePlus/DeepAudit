package edu.hnu.deepaudit.service;

import ai.onnxruntime.OnnxTensor;
import ai.onnxruntime.OrtEnvironment;
import ai.onnxruntime.OrtSession;
import edu.hnu.deepaudit.analysis.FeatureExtractor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisPool;

import java.io.File;
import java.nio.FloatBuffer;
import java.time.LocalDateTime;
import java.util.Collections;

/**
 * Anomaly Detection Service (ONNX Runtime Version)
 * Performs In-Process Inference using Isolation Forest model.
 */
public class AnomalyDetectionService {

    private static final Logger log = LoggerFactory.getLogger(AnomalyDetectionService.class);

    private JedisPool jedisPool;
    private OrtSession session;
    private OrtEnvironment env;

    // Hardcoded model path as per user instruction (could be config driven)
    // private static final String MODEL_PATH = "D:/Code/DeepAudit/models/deep_audit_iso_forest.onnx";
    
    private String currentModelPath;

    public AnomalyDetectionService() {
        // Initial load will be triggered by reloadModel or manual call if needed
        // For now, we defer loading or use a default if passed in constructor
    }
    
    public synchronized void reloadModel(String modelPath) {
        if (modelPath == null || modelPath.equals(currentModelPath)) {
            return;
        }
        
        log.info("Reloading AI Model from: {}", modelPath);
        try {
            // Close existing
            if (session != null) {
                session.close();
                session = null;
            }
            if (env != null) {
                env.close();
                env = null;
            }

            // Init new
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
     * Synchronous Risk Detection using ONNX
     */
    public double detectRisk(String userId, LocalDateTime eventTime, String sql, edu.hnu.deepaudit.analysis.SqlDeepAnalyzer.SqlFeatures astFeatures) {
        if (session == null) {
            return 0.0; // Fail-safe
        }

        try {
            // 1. Get Real Frequency from Redis
            int freq = 1;
            if (jedisPool != null) {
                try (Jedis jedis = jedisPool.getResource()) {
                    // Simple sliding window counter: key by minute
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
            
            // 2. SQL Type Weight (Simple heuristic)
            int sqlTypeWeight = 1;
            String lowerSql = sql.toLowerCase();
            if (lowerSql.contains("drop ") || lowerSql.contains("truncate ") || lowerSql.contains("grant ")) sqlTypeWeight = 5;
            else if (lowerSql.contains("update ") || lowerSql.contains("delete ") || lowerSql.contains("insert ")) sqlTypeWeight = 3;

            // 3. Feature Engineering (Java) - 8 Dimensions
            float[] features = FeatureExtractor.extractFeatures(
                eventTime, freq, sqlTypeWeight,
                astFeatures.conditionCount, astFeatures.joinCount, astFeatures.nestedLevel, astFeatures.hasAlwaysTrueCondition
            );

            // 4. Create Tensor [1, 8]
            long[] shape = new long[]{1, features.length};
            
            try (OnnxTensor tensor = OnnxTensor.createTensor(env, FloatBuffer.wrap(features), shape)) {
                
                // 5. Run Inference
                // Input name 'float_input' must match Python export script
                OrtSession.Result result = session.run(Collections.singletonMap("float_input", tensor));
                
                // 6. Get Scores
                // IsolationForest ONNX output: index 0 = label, index 1 = scores (decision function)
                // We use score for granular risk
                float[][] scores = (float[][]) result.get(1).getValue();
                float rawScore = scores[0][0];

                // 7. Score Normalization
                // Isolation Forest decision_function: 
                // positive -> normal, negative -> anomaly
                // We convert negative scores to positive risk (0-100)
                double riskScore = 0.0;
                
                if (rawScore < 0) {
                    // Lower score means more anomalous
                    // E.g. -0.1 -> Risk 5, -0.5 -> Risk 25
                    // Scaling factor 50 is heuristic
                    riskScore = Math.abs(rawScore) * 50.0;
                    riskScore = Math.min(riskScore, 100.0);
                    
                    log.info("AI Anomaly Detected - User: {}, RawScore: {}, Risk: {}", userId, rawScore, riskScore);
                    
                    if (riskScore > 0) {
                        updateRiskState(userId, riskScore);
                    }
                }
                
                return riskScore;
            }
        } catch (Exception e) {
            log.error("AI Inference Failed", e);
            return 0.0; // Fail-safe
        }
    }
    
    private void updateRiskState(String userId, double anomalyRiskScore) {
        if (jedisPool == null) return;
        
        String profileKey = "audit:risk:" + userId;
        try (Jedis jedis = jedisPool.getResource()) {
            jedis.hincrByFloat(profileKey, "score", anomalyRiskScore);
        } catch (Exception e) {
            log.error("Failed to update Redis risk state", e);
        }
    }
    
    private int countOccurrences(String str, String sub) {
        return (str.length() - str.replace(sub, "").length()) / sub.length();
    }
    
    public void close() {
        try {
            if (session != null) session.close();
            if (env != null) env.close();
        } catch (Exception e) {
            log.error("Error closing ONNX session", e);
        }
    }
}
