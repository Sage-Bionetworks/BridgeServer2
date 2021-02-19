package org.sagebionetworks.bridge.models.crc.gbf.external;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;

@JsonDeserialize
public class ConfirmOrderResponse {
    public final boolean success;
    public final String message;

    public ConfirmOrderResponse(boolean success, String message) {
        this.success = success;
        this.message = message;
    }
}
