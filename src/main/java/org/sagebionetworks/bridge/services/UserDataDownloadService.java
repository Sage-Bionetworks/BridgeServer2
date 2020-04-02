package org.sagebionetworks.bridge.services;

import com.fasterxml.jackson.core.JsonProcessingException;

import org.sagebionetworks.bridge.models.DateRange;

/**
 * Interface for user data download requests. Current implementation uses SQS (see {@link
 * UserDataDownloadViaSqsService), but this is an interface to allow different implementations.
 */
public interface UserDataDownloadService {
    /**
     * Kicks off an asynchronous request to gather user data for the logged in user, with data from the specified date
     * range (inclusive).
     */
    void requestUserData(String studyId, String userId, DateRange dateRange)
            throws JsonProcessingException;
}
