package org.sagebionetworks.bridge.validators;

import static org.sagebionetworks.bridge.TestUtils.assertValidatorMessage;

import com.google.common.collect.ImmutableMap;
import org.testng.annotations.Test;

import org.sagebionetworks.bridge.models.exporter.ExporterSubscriptionRequest;

public class ExporterSubscriptionRequestValidatorTest {
    private static final String ENDPOINT = "arn:aws:sqs:us-east-1:111111111111:test-queue";
    private static final String PROTOCOL = "sqs";

    @Test
    public void validRequest() {
        Validate.entityThrowingException(ExporterSubscriptionRequestValidator.INSTANCE, makeValidRequest());
    }

    @Test
    public void withOptionalParams() {
        ExporterSubscriptionRequest request = makeValidRequest();
        request.setAttributes(ImmutableMap.of("test-attr-name", "test-attr-value"));

        Validate.entityThrowingException(ExporterSubscriptionRequestValidator.INSTANCE, request);
    }

    @Test
    public void nullEndpoint() {
        ExporterSubscriptionRequest request = makeValidRequest();
        request.setEndpoint(null);
        assertValidatorMessage(ExporterSubscriptionRequestValidator.INSTANCE, request, "endpoint",
                "is required");
    }

    @Test
    public void emptyEndpoint() {
        ExporterSubscriptionRequest request = makeValidRequest();
        request.setEndpoint("");
        assertValidatorMessage(ExporterSubscriptionRequestValidator.INSTANCE, request, "endpoint",
                "is required");
    }

    @Test
    public void blankEndpoint() {
        ExporterSubscriptionRequest request = makeValidRequest();
        request.setEndpoint("   ");
        assertValidatorMessage(ExporterSubscriptionRequestValidator.INSTANCE, request, "endpoint",
                "is required");
    }

    @Test
    public void nullProtocol() {
        ExporterSubscriptionRequest request = makeValidRequest();
        request.setProtocol(null);
        assertValidatorMessage(ExporterSubscriptionRequestValidator.INSTANCE, request, "protocol",
                "is required");
    }

    @Test
    public void emptyProtocol() {
        ExporterSubscriptionRequest request = makeValidRequest();
        request.setProtocol("");
        assertValidatorMessage(ExporterSubscriptionRequestValidator.INSTANCE, request, "protocol",
                "is required");
    }

    @Test
    public void blankProtocol() {
        ExporterSubscriptionRequest request = makeValidRequest();
        request.setProtocol("   ");
        assertValidatorMessage(ExporterSubscriptionRequestValidator.INSTANCE, request, "protocol",
                "is required");
    }

    private static ExporterSubscriptionRequest makeValidRequest() {
        ExporterSubscriptionRequest request = new ExporterSubscriptionRequest();
        request.setEndpoint(ENDPOINT);
        request.setProtocol(PROTOCOL);
        return request;
    }
}
