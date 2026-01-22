package edu.hnu.deepaudit.service;

import edu.hnu.deepaudit.model.dto.AuditLogFeatureDTO;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.concurrent.CompletableFuture;

@Service
public class AnomalyDetectionService {

    private static final Logger log = LoggerFactory.getLogger(AnomalyDetectionService.class);

    @Value("${ai.service.url:http://localhost:5000/predict_risk}")
    private String aiServiceUrl;

    @Autowired
    private RestTemplate restTemplate;

    @Autowired
    private StringRedisTemplate redisTemplate;

    private final ObjectMapper objectMapper = new ObjectMapper();

    /**
     * 异步调用 AI 模型进行异常检测
     * 对应论文 5.2 的反馈回路设计
     */
    @Async("auditExecutor") // 需配置自定义线程池
    public CompletableFuture<Double> detectAnomalyAsync(String userId, AuditLogFeatureDTO features) {
        log.info("Sending features to AI model for user: {}", userId);

        try {
            // 1. 构建请求
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            HttpEntity<AuditLogFeatureDTO> request = new HttpEntity<>(features, headers);

            // 2. 调用 Python 服务
            JsonNode response = restTemplate.postForObject(aiServiceUrl, request, JsonNode.class);

            if (response != null && "success".equals(response.get("status").asText())) {
                double riskScore = response.get("normalized_risk_score").asDouble();
                boolean isAnomaly = response.get("is_anomaly").asBoolean();

                log.info("AI Analysis Result - User: {}, Score: {}, IsAnomaly: {}", userId, riskScore, isAnomaly);

                // 3. 如果发现异常，根据论文 5.2，异步更新 Redis 状态机
                if (isAnomaly && riskScore > 0) {
                    updateRiskState(userId, riskScore);
                }

                return CompletableFuture.completedFuture(riskScore);
            }

        } catch (Exception e) {
            log.error("Failed to call AI service", e);
        }

        return CompletableFuture.completedFuture(0.0);
    }

    /**
     * 将 AI 计算出的异常分叠加到 Redis 状态机中
     * 对应论文 5.2: "AI 服务异步调用 Redis... 触发状态机的阈值"
     */
    private void updateRiskState(String userId, double anomalyRiskScore) {
        String profileKey = "audit:risk:" + userId;
        // 使用 HINCRBYFLOAT 直接累加风险值
        redisTemplate.opsForHash().increment(profileKey, "score", anomalyRiskScore);
        
        // 可选：记录 AI 触发的审计日志
        log.warn("AI Model triggered risk increase for user {}: +{}", userId, anomalyRiskScore);
    }
}
