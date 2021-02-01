package org.sagebionetworks.bridge.models.crc.gbf.external;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlElementWrapper;

import java.util.List;

@JsonDeserialize
public class ShippingConfirmations {
    @JacksonXmlElementWrapper(useWrapping = false)
    @JsonProperty("ShippingConfirmation")
    public List<ShippingConfirmation> shippingConfirmation;
}
