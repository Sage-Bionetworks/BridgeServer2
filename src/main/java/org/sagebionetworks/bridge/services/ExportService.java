package org.sagebionetworks.bridge.services;

import com.fasterxml.jackson.core.JsonProcessingException;

/** Service that interfaces Bridge Server with Bridge Exporter. */
public interface ExportService {
    /** Kicks off an on-demand export for the given study. */
    void startOnDemandExport(String studyId) throws JsonProcessingException;
}
