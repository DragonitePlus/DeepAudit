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
    public double detectRisk(String userId, LocalDateTime eventTime, long rows, long timeMs, String sql) {
        if (session == null) {
            return 0.0; // Fail-safe
        }

        try {
            // 1. Simple SQL Feature Extraction (Mock implementation)
            // Ideally, use a proper SQL parser or pass down parsed info
            int sqlLen = sql.length();
            int numTables = countOccurrences(sql.toLowerCase(), " from ") + countOccurrences(sql.toLowerCase(), " join ");
            if (numTables == 0) numTables = 1;
            int numJoins = countOccurrences(sql.toLowerCase(), " join ");
            
            // Mock freq (Real impl would query Redis sliding window)
            int freq = 1; 

            // 2. Feature Engineering (Java)
            float[] features = FeatureExtractor.extractFeatures(
                eventTime, rows, timeMs, sqlLen, numTables, numJoins, freq
            );

            // 3. Create Tensor [1, 8]
            long[] shape = new long[]{1, features.length};
            
            try (OnnxTensor tensor = OnnxTensor.createTensor(env, FloatBuffer.wrap(features), shape)) {
                
                // 4. Run Inference
                // Input name 'float_input' must match Python export script
                OrtSession.Result result = session.run(Collections.singletonMap("float_input", tensor));
                
                // 5. Get Scores
                // IsolationForest ONNX output: index 0 = label, index 1 = scores (decision function)
                // We use score for granular risk
                float[][] scores = (float[][]) result.get(1).getValue();
                float rawScore = scores[0][0];

                // 6. Score Normalization
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
