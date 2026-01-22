package edu.hnu.deepaudit.service;

import java.util.Map;

/**
 * Service to handle risk interceptions.
 */
public interface InterceptionService {

    /**
     * Handle an interception event.
     * @param type The type of interception (e.g. ACCOUNT_LOCK)
     * @param userId The user ID involved
     * @param context Additional context (e.g. risk score, reason)
     */
    void handleInterception(InterceptionType type, String userId, Map<String, Object> context);
}
