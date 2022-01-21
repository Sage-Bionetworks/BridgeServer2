package org.sagebionetworks.bridge.models;

/**
 * What accounts should be returned from this query? Callers with access to production
 * accounts can choose to include test accounts (or not), so they will likely use 
 * PRODUCTION or BOTH. Developers with access only to test accounts are limited to TEST.
 */
public enum AccountTestFilter {
    TEST,
    PRODUCTION,
    BOTH
}
