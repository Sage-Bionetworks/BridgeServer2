package org.sagebionetworks.bridge.dao;

public interface MasterSchedulerStatusDao {
    /**
     * Get the scheduler status object.
     */
    public Long getLastProcessedTime();
}
