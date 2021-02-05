package org.sagebionetworks.bridge.models.crc.gbf.external;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import org.joda.time.LocalDate;

@JsonDeserialize
public class ConfirmShippingRequest {
    public final LocalDate startDate;
    public final LocalDate endDate;

    public ConfirmShippingRequest(LocalDate startDate, LocalDate endDate) {
        this.startDate = startDate;
        this.endDate = endDate;
    }
}
