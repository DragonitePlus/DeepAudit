package edu.hnu.deepaudit.service;

import edu.hnu.deepaudit.model.dto.AuditLogFeatureDTO;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.concurrent.CompletableFuture;

/**
 * Anomaly Detection Service (POJO)
 * Uses Java 11+ HttpClient to communicate with AI Service.
 */
public class AnomalyDetectionService {

    private static final Logger log = LoggerFactory.getLogger(AnomalyDetectionService.class);

    private String aiServiceUrl = "http://localhost:5000/predict_risk";
    
    private StringRedisTemplate redisTemplate;

    private final ObjectMapper objectMapper = new ObjectMapper();
    
    private final HttpClient httpClient;

    public AnomalyDetectionService() {
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(2))
                .build();
    }
    
    public void setRedisTemplate(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    public void setAiServiceUrl(String aiServiceUrl) {
        this.aiServiceUrl = aiServiceUrl;
    }

    /**
     * Async call to AI Model
     */
    public CompletableFuture<Double> detectAnomalyAsync(String userId, AuditLogFeatureDTO features) {
        return CompletableFuture.supplyAsync(() -> {
            log.info("Sending features to AI model for user: {}", userId);

            try {
                String jsonBody = objectMapper.writeValueAsString(features);
                
                HttpRequest request = HttpRequest.newBuilder()
                        .uri(URI.create(aiServiceUrl))
                        .header("Content-Type", "application/json")
                        .POST(HttpRequest.BodyPublishers.ofString(jsonBody))
                        .timeout(Duration.ofSeconds(3))
                        .build();

                HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

                if (response.statusCode() == 200) {
                    JsonNode root = objectMapper.readTree(response.body());
                    if (root != null && "success".equals(root.get("status").asText())) {
                        double riskScore = root.get("normalized_risk_score").asDouble();
                        boolean isAnomaly = root.get("is_anomaly").asBoolean();

                        log.info("AI Analysis Result - User: {}, Score: {}, IsAnomaly: {}", userId, riskScore, isAnomaly);

                        if (isAnomaly && riskScore > 0) {
                            updateRiskState(userId, riskScore);
                        }
                        return riskScore;
                    }
                }
            } catch (Exception e) {
                log.error("Failed to call AI service", e);
            }
            return 0.0;
        });
    }

    private void updateRiskState(String userId, double anomalyRiskScore) {
        if (redisTemplate == null) return;
        
        String profileKey = "audit:risk:" + userId;
        try {
            redisTemplate.opsForHash().increment(profileKey, "score", anomalyRiskScore);
            log.warn("AI Model triggered risk increase for user {}: +{}", userId, anomalyRiskScore);
        } catch (Exception e) {
            log.error("Failed to update Redis risk state", e);
        }
    }
}
