package org.sagebionetworks.bridge.dao;

import org.sagebionetworks.bridge.models.DateTimeHolder;

public interface MasterSchedulerStatusDao {
    /**
     * Get the time the scheduler last ran.
     */
    public DateTimeHolder getLastProcessedTime();
}
