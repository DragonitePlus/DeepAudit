package edu.hnu.deepaudit.exception;

/**
 * Exception thrown when a risk control rule blocks an operation.
 */
public class RiskControlException extends RuntimeException {
    public RiskControlException(String message) {
        super(message);
    }
}
