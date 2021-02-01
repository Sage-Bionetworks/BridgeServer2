package org.sagebionetworks.bridge.models.crc.gbf.external;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;

import java.util.List;

@JsonDeserialize
public class CheckOrderStatusRequest {
    public final List<String> orderNumbers;

    public CheckOrderStatusRequest(List<String> orderNumbers) {
        this.orderNumbers = orderNumbers;
    }
}
