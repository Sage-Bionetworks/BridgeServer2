package org.sagebionetworks.bridge;

import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;
import static org.sagebionetworks.bridge.BridgeUtils.resolveTemplate;
import static org.testng.Assert.assertEquals;

import java.util.HashMap;
import java.util.Map;

import com.amazonaws.services.sns.AmazonSNS;
import com.amazonaws.services.sns.model.CreateTopicResult;
import com.amazonaws.services.sns.model.ListTopicsResult;
import com.amazonaws.services.sns.model.SubscribeRequest;
import com.amazonaws.services.sns.model.Topic;
import com.amazonaws.services.sqs.AmazonSQS;
import com.amazonaws.services.sqs.model.GetQueueAttributesResult;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.mockito.Spy;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import org.sagebionetworks.bridge.config.BridgeConfig;

public class SnsInitializerTest {
    private static final String DUMMY_AWS_ACCOUNT_ID = "111111111111";
    private static final String NEXT_PAGE_TOKEN = "dummy-token";

    private static final String CREATE_WITH_SQS_SUB_ARN = "arn:aws:sns:us-east-1:111111111111:create-with-sqs-sub";
    private static final String CREATE_WITH_SQS_SUB_ARN_PROPERTY = "prop.create.with.sqs.sub.arn";
    private static final String CREATE_WITH_SQS_SUB_NAME = "create-with-sqs-sub";
    private static final String CREATE_WITH_SQS_SUB_PROPERTY = "prop.create.with.sqs.sub";

    private static final String CREATE_WITH_S3_NOTIFS_ARN = "arn:aws:sns:us-east-1:111111111111:create-with-s3-notifs";
    private static final String CREATE_WITH_S3_NOTIFS_ARN_PROPERTY = "prop.create.with.s3.notifs.arn";
    private static final String CREATE_WITH_S3_NOTIFS_NAME = "create-with-s3-notife";
    private static final String CREATE_WITH_S3_NOTIFS_PROPERTY = "prop.create.with.s3.notifs";

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
        // 1. create-with-sqs-sub (Create a queue and associate it with an SQS queue.)
        // 2. create-with-s3-notifs (Create a queue and give permissions to receive notifications from S3.)
        // 3. existing-topic (Configured for both an SQS queue and S3 notifications, but it already exists, so we
        //   create neither.)

        // Mock BridgeConfig.
        when(mockBridgeConfig.get(SnsInitializer.CONFIG_KEY_AWS_ACCOUNT_ID)).thenReturn(DUMMY_AWS_ACCOUNT_ID);
        when(mockBridgeConfig.get(CREATE_WITH_SQS_SUB_PROPERTY)).thenReturn(CREATE_WITH_SQS_SUB_NAME);
        when(mockBridgeConfig.get(CREATE_WITH_S3_NOTIFS_PROPERTY)).thenReturn(CREATE_WITH_S3_NOTIFS_NAME);
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

        when(mockSnsClient.createTopic(CREATE_WITH_SQS_SUB_NAME)).thenReturn(new CreateTopicResult()
                .withTopicArn(CREATE_WITH_SQS_SUB_ARN));
        when(mockSnsClient.createTopic(CREATE_WITH_S3_NOTIFS_NAME)).thenReturn(new CreateTopicResult()
                .withTopicArn(CREATE_WITH_S3_NOTIFS_ARN));

        // Mock SQS Client.
        GetQueueAttributesResult getQueueAttributesResult = new GetQueueAttributesResult().addAttributesEntry(
                SnsInitializer.ATTR_QUEUE_ARN, SUBSCRIBER_QUEUE_ARN);
        when(mockSqsClient.getQueueAttributes(SUBSCRIBER_QUEUE_URL, SnsInitializer.QUEUE_ATTRIBUTE_LIST))
                .thenReturn(getQueueAttributesResult);

        // Spy getSnsReceiveS3NotificationsEnabled() and getSnsTopicProperties().
        doReturn(ImmutableSet.of(CREATE_WITH_S3_NOTIFS_PROPERTY)).when(initializer)
                .getSnsReceiveS3NotificationsEnabled();

        Map<String, String> snsTopicProperties = new HashMap<>();
        snsTopicProperties.put(CREATE_WITH_SQS_SUB_PROPERTY, SUBSCRIBER_QUEUE_URL_PROPERTY);
        snsTopicProperties.put(CREATE_WITH_S3_NOTIFS_PROPERTY, null);
        snsTopicProperties.put(EXISTING_TOPIC_PROPERTY, SUBSCRIBER_QUEUE_URL_PROPERTY);
        doReturn(snsTopicProperties).when(initializer).getSnsTopicProperties();

        // Execute.
        initializer.initTopics();

        // Verify 2 topics created.
        verify(mockSnsClient).createTopic(CREATE_WITH_SQS_SUB_NAME);
        verify(mockSnsClient).createTopic(CREATE_WITH_S3_NOTIFS_NAME);

        // Verify 1 subscription.
        ArgumentCaptor<SubscribeRequest> subscribeRequestCaptor = ArgumentCaptor.forClass(SubscribeRequest.class);
        verify(mockSnsClient).subscribe(subscribeRequestCaptor.capture());

        SubscribeRequest subscribeRequest = subscribeRequestCaptor.getValue();
        assertEquals(subscribeRequest.getEndpoint(), SUBSCRIBER_QUEUE_ARN);
        assertEquals(subscribeRequest.getProtocol(), "sqs");
        assertEquals(subscribeRequest.getTopicArn(), CREATE_WITH_SQS_SUB_ARN);

        Map<String, String> subscribeRequestAttrs = subscribeRequest.getAttributes();
        assertEquals(subscribeRequestAttrs.size(), 1);
        assertEquals(subscribeRequestAttrs.get("RawMessageDelivery"), "true");

        // Verify 1 SNS policy.
        Map<String, String> varMap = ImmutableMap.<String, String>builder()
                .put("topicArn", CREATE_WITH_S3_NOTIFS_ARN)
                .put("awsAccountId", DUMMY_AWS_ACCOUNT_ID)
                .build();
        String resolvedPolicy = resolveTemplate(SnsInitializer.S3_NOTIFICATIONS_POLICY, varMap);
        verify(mockSnsClient).setTopicAttributes(CREATE_WITH_S3_NOTIFS_ARN, SnsInitializer.ATTRIBUTE_NAME_POLICY,
                resolvedPolicy);

        // For completeness, verify the listTopics calls.
        verify(mockSnsClient).listTopics();
        verify(mockSnsClient).listTopics(NEXT_PAGE_TOKEN);
        verifyNoMoreInteractions(mockSnsClient);

        // Verify all topics have ARN entries in config.
        verify(mockBridgeConfig).set(CREATE_WITH_SQS_SUB_ARN_PROPERTY, CREATE_WITH_SQS_SUB_ARN);
        verify(mockBridgeConfig).set(CREATE_WITH_S3_NOTIFS_ARN_PROPERTY, CREATE_WITH_S3_NOTIFS_ARN);
        verify(mockBridgeConfig).set(EXISTING_TOPIC_ARN_PROPERTY, EXISTING_TOPIC_ARN);
    }
}
