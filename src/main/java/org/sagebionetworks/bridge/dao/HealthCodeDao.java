package org.sagebionetworks.bridge.dao;

public interface HealthCodeDao {
    /**
     * Return the ID of the app associated with this health code; or null if 
     * the health code does not exist. This DAO exists for legacy uploads that 
     * do not have a appId associated with them.
     */
    String getStudyIdentifier(String healthCode);
}
