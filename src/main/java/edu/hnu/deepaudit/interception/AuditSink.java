package edu.hnu.deepaudit.interception;

import edu.hnu.deepaudit.service.AuditRequest;

/**
 * Interface for Interception Module to submit captured audit requests.
 * Decouples interception logic from analysis/core logic.
 */
public interface AuditSink {
    /**
     * Submit an audit request for processing.
     * @param request The captured audit request containing SQL, User, etc.
     */
    void submit(AuditRequest request);
}
