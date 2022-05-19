package org.sagebionetworks.bridge;

import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.testng.Assert.assertEquals;

import java.util.Map;

import com.amazonaws.services.sns.AmazonSNS;
import com.amazonaws.services.sns.model.CreateTopicResult;
import com.amazonaws.services.sns.model.ListTopicsResult;
import com.amazonaws.services.sns.model.SubscribeRequest;
import com.amazonaws.services.sns.model.Topic;
import com.amazonaws.services.sqs.AmazonSQS;
import com.amazonaws.services.sqs.model.GetQueueAttributesResult;
import com.google.common.collect.ImmutableMap;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.mockito.Spy;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import org.sagebionetworks.bridge.config.BridgeConfig;

public class SnsInitializerTest {
    private static final String NEXT_PAGE_TOKEN = "dummy-token";

    private static final String CREATE_THIS_TOPIC_ARN = "arn:aws:sns:us-east-1:111111111111:create-this-topic";
    private static final String CREATE_THIS_TOPIC_ARN_PROPERTY = "prop.create.this.topic.arn";
    private static final String CREATE_THIS_TOPIC_NAME = "create-this-topic";
    private static final String CREATE_THIS_TOPIC_PROPERTY = "prop.create.this.topic";

    private static final String EXISTING_TOPIC_ARN = "arn:aws:sns:us-east-1:111111111111:existing-topic";
    private static final String EXISTING_TOPIC_ARN_PROPERTY = "prop.existing.topic.arn";
    private static final String EXISTING_TOPIC_NAME = "existing-topic";
    private static final String EXISTING_TOPIC_PROPERTY = "prop.existing.topic";

    private static final String SUBSCRIBER_QUEUE_ARN = "arn:aws:sqs:us-east-1:111111111111:subscriber-queue";
    private static final String SUBSCRIBER_QUEUE_URL = "https://example.com/subscriber-queue";
    private static final String SUBSCRIBER_QUEUE_URL_PROPERTY = "prop.subscriber.queue.url";

    @Mock
    private BridgeConfig mockBridgeConfig;

    @Mock
    private AmazonSNS mockSnsClient;

    @Mock
    private AmazonSQS mockSqsClient;

    @InjectMocks
    @Spy
    private SnsInitializer initializer;

    @BeforeMethod
    public void beforeMethod() {
        MockitoAnnotations.initMocks(this);
    }

    @Test
    public void test() {
        // Test cases.
        // 1. create-this-topic (We need to create this topic.)
        // 2. existing-topic (Already exists.)

        // Mock BridgeConfig.
        when(mockBridgeConfig.get(CREATE_THIS_TOPIC_PROPERTY)).thenReturn(CREATE_THIS_TOPIC_NAME);
        when(mockBridgeConfig.get(EXISTING_TOPIC_PROPERTY)).thenReturn(EXISTING_TOPIC_NAME);
        when(mockBridgeConfig.get(SUBSCRIBER_QUEUE_URL_PROPERTY)).thenReturn(SUBSCRIBER_QUEUE_URL);

        // Mock SNS Client.
        // To test pagination, the first page should have a single topic that we don't actually care about.
        Topic otherTopic = new Topic().withTopicArn("arn:aws:sns:us-east-1:111111111111:other-topic");
        ListTopicsResult listTopicsPage1Result = new ListTopicsResult().withTopics(otherTopic)
                .withNextToken(NEXT_PAGE_TOKEN);
        when(mockSnsClient.listTopics()).thenReturn(listTopicsPage1Result);

        Topic existingTopic = new Topic().withTopicArn(EXISTING_TOPIC_ARN);
        ListTopicsResult listTopicsPage2Result = new ListTopicsResult().withTopics(existingTopic).withNextToken(null);
        when(mockSnsClient.listTopics(NEXT_PAGE_TOKEN)).thenReturn(listTopicsPage2Result);

        when(mockSnsClient.createTopic(CREATE_THIS_TOPIC_NAME)).thenReturn(new CreateTopicResult()
                .withTopicArn(CREATE_THIS_TOPIC_ARN));

        // Mock SQS Client.
        GetQueueAttributesResult getQueueAttributesResult = new GetQueueAttributesResult().addAttributesEntry(
                SnsInitializer.ATTR_QUEUE_ARN, SUBSCRIBER_QUEUE_ARN);
        when(mockSqsClient.getQueueAttributes(SUBSCRIBER_QUEUE_URL, SnsInitializer.QUEUE_ATTRIBUTE_LIST))
                .thenReturn(getQueueAttributesResult);

        // Spy getSnsTopicProperties().
        Map<String, String> snsTopicProperties = ImmutableMap.<String, String>builder()
                .put(CREATE_THIS_TOPIC_PROPERTY, SUBSCRIBER_QUEUE_URL_PROPERTY)
                .put(EXISTING_TOPIC_PROPERTY, SUBSCRIBER_QUEUE_URL_PROPERTY)
                .build();
        doReturn(snsTopicProperties).when(initializer).getSnsTopicProperties();

        // Execute.
        initializer.initTopics();

        // Verify 1 topic created.
        verify(mockSnsClient).createTopic(CREATE_THIS_TOPIC_NAME);

        // Verify 1 subscription.
        ArgumentCaptor<SubscribeRequest> subscribeRequestCaptor = ArgumentCaptor.forClass(SubscribeRequest.class);
        verify(mockSnsClient).subscribe(subscribeRequestCaptor.capture());

        SubscribeRequest subscribeRequest = subscribeRequestCaptor.getValue();
        assertEquals(subscribeRequest.getEndpoint(), SUBSCRIBER_QUEUE_ARN);
        assertEquals(subscribeRequest.getProtocol(), "sqs");
        assertEquals(subscribeRequest.getTopicArn(), CREATE_THIS_TOPIC_ARN);

        Map<String, String> subscribeRequestAttrs = subscribeRequest.getAttributes();
        assertEquals(subscribeRequestAttrs.size(), 1);
        assertEquals(subscribeRequestAttrs.get("RawMessageDelivery"), "true");

        // Verify both topics have ARN entries in config.
        verify(mockBridgeConfig).set(CREATE_THIS_TOPIC_ARN_PROPERTY, CREATE_THIS_TOPIC_ARN);
        verify(mockBridgeConfig).set(EXISTING_TOPIC_ARN_PROPERTY, EXISTING_TOPIC_ARN);
    }
}
