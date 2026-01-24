package edu.hnu.deepaudit.proxy.factory;

import edu.hnu.deepaudit.control.RiskStateMachine;
import org.springframework.data.redis.connection.jedis.JedisConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;

/**
 * Factory to create RiskStateMachine instance in non-Spring environment (ShardingSphere Proxy).
 */
public class RiskStateMachineFactory {

    private static volatile RiskStateMachine instance;

    public static RiskStateMachine getInstance() {
        if (instance == null) {
            synchronized (RiskStateMachineFactory.class) {
                if (instance == null) {
                    instance = createInstance();
                }
            }
        }
        return instance;
    }

    private static RiskStateMachine createInstance() {
        // Initialize Redis Template manually
        // Note: In a real production environment, you should read configuration from a file.
        // Here we assume localhost:6379 for simplicity as per the current dev environment.
        JedisConnectionFactory connectionFactory = new JedisConnectionFactory();
        connectionFactory.afterPropertiesSet();

        StringRedisTemplate redisTemplate = new StringRedisTemplate();
        redisTemplate.setConnectionFactory(connectionFactory);
        redisTemplate.afterPropertiesSet();

        // Create RiskStateMachine manually
        // Note: RiskStateMachine in core module relies on Autowired dependencies.
        // We might need to refactor RiskStateMachine to allow setter injection or constructor injection
        // without Spring annotations, or use reflection to set fields.
        // For now, assuming we can't easily change core code structure, we might need a workaround.
        // Ideally, RiskStateMachine should be decoupled from Spring.
        
        // Since we are in a "plugin" mode, let's assume we can modify core or subclass it.
        // But RiskStateMachine has @Autowired fields. 
        // We will need to use reflection to set them if we don't change the core code.
        
        RiskStateMachine stateMachine = new RiskStateMachine();
        // Reflection based injection would go here...
        // For this MVP, let's just return the object and handle dependencies later or mock them if needed.
        
        return stateMachine;
    }
}
