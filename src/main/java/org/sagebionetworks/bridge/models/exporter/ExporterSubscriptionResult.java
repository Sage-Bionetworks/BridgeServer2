package org.sagebionetworks.bridge.models.exporter;

/** The result of a subscription request to be notified when a study is initialized for Exporter 3.0 in an app. */
public class ExporterSubscriptionResult {
    private String subscriptionArn;

    /** ARN representing the subscription in SNS. */
    public String getSubscriptionArn() {
        return subscriptionArn;
    }

    public void setSubscriptionArn(String subscriptionArn) {
        this.subscriptionArn = subscriptionArn;
    }
}
