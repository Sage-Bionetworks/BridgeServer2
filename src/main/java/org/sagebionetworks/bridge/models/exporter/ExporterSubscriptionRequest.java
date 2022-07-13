package org.sagebionetworks.bridge.models.exporter;

import java.util.Map;

import org.sagebionetworks.bridge.models.BridgeEntity;

/**
 * Represents a subscription request to be notified when a study is initialized for Exporter 3.0 in an app. This class
 * is a wrapper for SNS attributes. See SNS documentation for additional details.
 */
public class ExporterSubscriptionRequest implements BridgeEntity {
    private Map<String, String> attributes;
    private String endpoint;
    private String protocol;

    /** SNS subscription attributes. */
    public Map<String, String> getAttributes() {
        return attributes;
    }

    public void setAttributes(Map<String, String> attributes) {
        this.attributes = attributes;
    }

    /** Endpoint to receive notifications. Format is protocol dependent. */
    public String getEndpoint() {
        return endpoint;
    }

    public void setEndpoint(String endpoint) {
        this.endpoint = endpoint;
    }

    /** Protocol to receive notifications. */
    public String getProtocol() {
        return protocol;
    }

    public void setProtocol(String protocol) {
        this.protocol = protocol;
    }
}
