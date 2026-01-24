package edu.hnu.deepaudit.service.impl;

import edu.hnu.deepaudit.service.InterceptionService;
import edu.hnu.deepaudit.service.InterceptionType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.Map;

/**
 * Log-based implementation of InterceptionService.
 * Currently prints logs, but designed to be extended for real actions.
 */
@Service
public class LogBasedInterceptionService implements InterceptionService {

    private static final Logger log = LoggerFactory.getLogger(LogBasedInterceptionService.class);

    @Override
    public void handleInterception(InterceptionType type, String userId, Map<String, Object> context) {
        // In a real implementation, this would switch on 'type' and call specific handlers.
        // For now, we log the event as requested.
        log.warn(">>> INTERCEPTION TRIGGERED <<<");
        log.warn("Type: {}", type);
        log.warn("User: {}", userId);
        log.warn("Context: {}", context);
        
        if (type == InterceptionType.DB_REFUSE) {
            log.error("ACTION: Database access REFUSED for user {}", userId);
        } else if (type == InterceptionType.ACCOUNT_LOCK) {
            log.error("ACTION: Account LOCKED for user {}", userId);
        }
    }
}
