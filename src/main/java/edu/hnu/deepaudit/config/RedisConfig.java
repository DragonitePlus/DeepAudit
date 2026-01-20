package edu.hnu.deepaudit.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.serializer.StringRedisSerializer;

@Configuration
public class RedisConfig {

    @Bean
    public RedisTemplate<String, Object> redisTemplate(RedisConnectionFactory factory) {
        RedisTemplate<String, Object> template = new RedisTemplate<>();
        template.setConnectionFactory(factory);
        // Use String serializer for keys
        template.setKeySerializer(new StringRedisSerializer());
        // Use default (JDK) or JSON serializer for values depending on needs. 
        // For now, default is fine, or we can add Jackson/Fastjson later.
        return template;
    }
}
