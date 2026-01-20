package edu.hnu.deepaudit.perception;

/**
 * Context holder for the current application user ID.
 * Solves the "Identity Gap" by passing App User ID to the Audit layer.
 */
public class UserContext {
    private static final ThreadLocal<String> USER_HOLDER = new ThreadLocal<>();

    public static void setUserId(String userId) {
        USER_HOLDER.set(userId);
    }

    public static String getUserId() {
        return USER_HOLDER.get();
    }

    public static void clear() {
        USER_HOLDER.remove();
    }
}
