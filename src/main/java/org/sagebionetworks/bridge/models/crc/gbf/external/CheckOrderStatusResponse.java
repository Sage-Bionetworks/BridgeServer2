package org.sagebionetworks.bridge.models.crc.gbf.external;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;

import java.util.List;

@JsonDeserialize
public class CheckOrderStatusResponse {
    public final boolean success;
    public final String errorMessage;
    public final List<OrderStatus> statuses;

    public CheckOrderStatusResponse(@JsonProperty("success") boolean success, @JsonProperty("errorMessage") String errorMessage, @JsonProperty("statuses") List<OrderStatus> statuses) {
        this.success = success;
        this.errorMessage = errorMessage;
        this.statuses = statuses;
    }

    @JsonDeserialize
    public static class OrderStatus {
        public final String orderNumber;
        public final String orderStatus;

        @JsonCreator
        public OrderStatus(@JsonProperty("orderNumber") String orderNumber, @JsonProperty("orderStatus") String orderStatus) {
            this.orderNumber = orderNumber;
            this.orderStatus = orderStatus;
        }
    }
}
