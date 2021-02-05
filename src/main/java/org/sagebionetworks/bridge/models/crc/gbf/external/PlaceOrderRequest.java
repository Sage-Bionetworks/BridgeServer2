package org.sagebionetworks.bridge.models.crc.gbf.external;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;

@JsonDeserialize
public class PlaceOrderRequest {
    public final String orderXml;
    public final boolean test;

    public PlaceOrderRequest(String orderXml, boolean test) {
        this.orderXml = orderXml;
        this.test = test;
    }
}
