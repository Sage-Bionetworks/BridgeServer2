package org.sagebionetworks.bridge.models.crc.gbf.external;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;

@JsonDeserialize
public class PlaceOrderResponse {
    public final boolean success;
    public final String message;

    @JsonCreator
    public PlaceOrderResponse(@JsonProperty("success") boolean success, @JsonProperty("message") String message) {
        this.success = success;
        this.message = message;
    }
}
