package org.sagebionetworks.bridge.dao;

import org.sagebionetworks.bridge.models.TimestampHolder;

public interface MasterSchedulerStatusDao {
    /**
     * Get the time the scheduler last ran.
     */
    public TimestampHolder getLastProcessedTime();
}
