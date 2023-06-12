package org.sagebionetworks.bridge.models.schedules2.adherence;

import com.fasterxml.jackson.databind.JsonNode;
import org.joda.time.DateTime;

/**
 * This is used by the UpdateAdherencePostProcessingAttributes API to add these specific fields to the Adherence
 * Record.
 */
public class AdherencePostProcessingAttributes {
    private JsonNode postProcessingAttributes;
    private DateTime postProcessingCompletedOn;
    private String postProcessingStatus;
    private DateTime startedOn;

    /** Key-value pairs related to upload post-processing (maximum size 65k). */
    public JsonNode getPostProcessingAttributes() {
        return postProcessingAttributes;
    }

    public void setPostProcessingAttributes(JsonNode postProcessingAttributes) {
        this.postProcessingAttributes = postProcessingAttributes;
    }

    /** When the post-processing step was completed. */
    public DateTime getPostProcessingCompletedOn() {
        return postProcessingCompletedOn;
    }

    public void setPostProcessingCompletedOn(DateTime postProcessingCompletedOn) {
        this.postProcessingCompletedOn = postProcessingCompletedOn;
    }

    /**
     * Short string that represents the current status of the upload in the post-processing pipeline. This may be app
     * or study specific. Examples include: "Pending", "SchemaVerified", "SchemaVerificationFailed", "DataInParquet",
     * "DataScored". Must be 255 characters or less.
     */
    public String getPostProcessingStatus() {
        return postProcessingStatus;
    }

    public void setPostProcessingStatus(String postProcessingStatus) {
        this.postProcessingStatus = postProcessingStatus;
    }

    /**
     * When the adherence was started by the participant. If this is specified, this will be set as the startedOn for
     * the adherence record. If this is not specified, the startedOn will remain unchanged, or will be set to the
     * current time if it doesn't already exist.
     */
    public DateTime getStartedOn() {
        return startedOn;
    }

    public void setStartedOn(DateTime startedOn) {
        this.startedOn = startedOn;
    }
}
