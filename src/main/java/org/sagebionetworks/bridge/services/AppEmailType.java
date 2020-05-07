package org.sagebionetworks.bridge.services;

/** Enumeration of different email types associated with the app. Currently used to verify recipient emails. */
public enum AppEmailType {
    /**
     * Email address that should receive consent notification emails, when a participant signs or withdraws consent.
     * See {@link App#getConsentNotificationEmail}.
     */
    CONSENT_NOTIFICATION
}
