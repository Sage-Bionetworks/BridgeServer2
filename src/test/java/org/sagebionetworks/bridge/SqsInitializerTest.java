package org.sagebionetworks.bridge;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.testng.Assert.assertEquals;

import java.util.Map;

import com.amazonaws.services.sqs.AmazonSQS;
import com.amazonaws.services.sqs.model.CreateQueueRequest;
import com.amazonaws.services.sqs.model.CreateQueueResult;
import com.amazonaws.services.sqs.model.ListQueuesResult;
import com.fasterxml.jackson.databind.JsonNode;
import com.google.common.collect.ImmutableSet;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.mockito.Spy;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import org.sagebionetworks.bridge.config.BridgeConfig;
import org.sagebionetworks.bridge.json.BridgeObjectMapper;

public class SqsInitializerTest {
    private static final String DEAD_LETTER_QUEUE_ARN = "dummy-dlq-arn";

    private static final String CREATE_THIS_QUEUE_NAME = "create-this-queue";
    private static final String CREATE_THIS_QUEUE_PROPERTY = "prop.create.this.queue";
    private static final String CREATE_THIS_QUEUE_URL = "https://example.com/create-this-queue";
    private static final String CREATE_THIS_QUEUE_URL_PROPERTY = "prop.create.this.queue.url";

    private static final String EXISTING_QUEUE_NAME = "existing-queue";
    private static final String EXISTING_QUEUE_PROPERTY = "prop.existing.queue";
    private static final String EXISTING_QUEUE_URL = "https://example.com/existing-queue";
    private static final String EXISTING_QUEUE_URL_PROPERTY = "prop.existing.queue.url";

    @Mock
    private AmazonSQS mockSqsClient;

    @InjectMocks
    @Spy
    private SqsInitializer initializer;

    private BridgeConfig mockBridgeConfig;

    @BeforeMethod
    public void beforeMethod() {
        MockitoAnnotations.initMocks(this);

        // BridgeConfig needs to be mocked separately, because we grab the dead letter queue ARN when we call
        // setBridgeConfig().
        mockBridgeConfig = mock(BridgeConfig.class);
        when(mockBridgeConfig.get(SqsInitializer.CONFIG_KEY_DEAD_LETTER_QUEUE_ARN)).thenReturn(DEAD_LETTER_QUEUE_ARN);
        initializer.setBridgeConfig(mockBridgeConfig);
    }

    @Test
    public void test() throws Exception {
        // Test cases:
        // 1. create-this-queue (We need to create this queue.)
        // 2. existing-queue (Already exists.)

        // Mock BridgeConfig.
        when(mockBridgeConfig.get(CREATE_THIS_QUEUE_PROPERTY)).thenReturn(CREATE_THIS_QUEUE_NAME);
        when(mockBridgeConfig.get(EXISTING_QUEUE_PROPERTY)).thenReturn(EXISTING_QUEUE_NAME);

        // Mock SQS client.
        ListQueuesResult listQueuesResult = new ListQueuesResult();
        listQueuesResult.withQueueUrls(EXISTING_QUEUE_URL);
        when(mockSqsClient.listQueues()).thenReturn(listQueuesResult);

        when(mockSqsClient.createQueue(any(CreateQueueRequest.class))).thenReturn(new CreateQueueResult()
                .withQueueUrl(CREATE_THIS_QUEUE_URL));

        // Spy getQueueProperties().
        doReturn(ImmutableSet.of(CREATE_THIS_QUEUE_PROPERTY, EXISTING_QUEUE_PROPERTY)).when(initializer)
                .getQueueProperties();

        // Execute.
        initializer.initQueues();

        // Verify 1 queue was created.
        ArgumentCaptor<CreateQueueRequest> createQueueRequestCaptor = ArgumentCaptor.forClass(
                CreateQueueRequest.class);
        verify(mockSqsClient).createQueue(createQueueRequestCaptor.capture());

        CreateQueueRequest createQueueRequest = createQueueRequestCaptor.getValue();
        assertEquals(createQueueRequest.getQueueName(), CREATE_THIS_QUEUE_NAME);

        Map<String, String> createQueueRequestAttrs = createQueueRequest.getAttributes();
        assertEquals(createQueueRequestAttrs.size(), 6);
        assertEquals(createQueueRequestAttrs.get("DelaySeconds"), "0");
        assertEquals(createQueueRequestAttrs.get("MaximumMessageSize"), "12288");
        assertEquals(createQueueRequestAttrs.get("MessageRetentionPeriod"), "1209600");
        assertEquals(createQueueRequestAttrs.get("ReceiveMessageWaitTimeSeconds"), "0");
        assertEquals(createQueueRequestAttrs.get("VisibilityTimeout"), "14400");

        String redrivePolicyJson = createQueueRequestAttrs.get("RedrivePolicy");
        JsonNode redrivePolicyNode = BridgeObjectMapper.get().readTree(redrivePolicyJson);
        assertEquals(redrivePolicyNode.size(), 2);
        assertEquals(redrivePolicyNode.get("deadLetterTargetArn").textValue(), DEAD_LETTER_QUEUE_ARN);
        assertEquals(redrivePolicyNode.get("maxReceiveCount").textValue(), "5");

        // Verify both queues have URL entries in config.
        verify(mockBridgeConfig).set(CREATE_THIS_QUEUE_URL_PROPERTY, CREATE_THIS_QUEUE_URL);
        verify(mockBridgeConfig).set(EXISTING_QUEUE_URL_PROPERTY, EXISTING_QUEUE_URL);
    }
}
