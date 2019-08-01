package org.sagebionetworks.bridge.models;

/**
 * Enumeration of request types that we want to throttle on. Used to differentiate between request types and generate
 * cache keys.
 */
public enum ThrottleRequestType {
    EMAIL_SIGNIN,
    PHONE_SIGNIN,
    VERIFY_EMAIL,
    VERIFY_PHONE
}
