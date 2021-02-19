package org.sagebionetworks.bridge.models.crc.gbf.external;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;

@JsonDeserialize
public class ConfirmShippingResponse {
    public final String XML;

    @JsonCreator
    public ConfirmShippingResponse(@JsonProperty("XML") String xml) {
        XML = xml;
    }
}
