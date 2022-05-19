package org.sagebionetworks.bridge;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.amazonaws.services.sns.AmazonSNS;
import com.amazonaws.services.sns.model.CreateTopicResult;
import com.amazonaws.services.sns.model.ListTopicsResult;
import com.amazonaws.services.sns.model.SubscribeRequest;
import com.amazonaws.services.sns.model.Topic;
import com.amazonaws.services.sqs.AmazonSQS;
import com.amazonaws.services.sqs.model.GetQueueAttributesResult;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.util.concurrent.RateLimiter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import org.sagebionetworks.bridge.config.BridgeConfig;

/** Initialzes SNS topics based on config. This requires SqsInitializer to have been run first. */
@Component
public class SnsInitializer {
    private static final Logger LOG = LoggerFactory.getLogger(SnsInitializer.class);

    static final String ATTR_QUEUE_ARN = "QueueArn";
    static final List<String> QUEUE_ATTRIBUTE_LIST = ImmutableList.of(ATTR_QUEUE_ARN);

    // SNS.listQueues throttles at 30 requests per second. Let's set our own rate limited with a margin of 2x so that
    // we don't get throttled.
    private final RateLimiter rateLimiter = RateLimiter.create(15.0);

    private BridgeConfig bridgeConfig;
    private AmazonSNS snsClient;
    private AmazonSQS sqsClient;

    @Autowired
    public final void setBridgeConfig(BridgeConfig bridgeConfig) {
        this.bridgeConfig = bridgeConfig;
    }

    @Autowired
    public final void setSnsClient(AmazonSNS snsClient) {
        this.snsClient = snsClient;
    }

    @Autowired
    public final void setSqsClient(AmazonSQS sqsClient) {
        this.sqsClient = sqsClient;
    }

    // For testing purposes, we want to subscribe an SQS queue to each SNS topic. This map has SNS topic configs as the
    // key and SQS queue url configs as the values.
    private static final Map<String, String> SNS_TOPIC_PROPERTIES = ImmutableMap.<String, String>builder()
            .put("study.creation.sns.topic", "integ.test.sqs.queue.url")
            .put("upload.sns.topic", "integ.test.sqs.queue.url")
            .build();

    // Package-scoped so we can mock this in unit tests.
    Map<String, String> getSnsTopicProperties() {
        return SNS_TOPIC_PROPERTIES;
    }

    public void initTopics() {
        // Get existing topic names.
        Map<String, String> topicNamesToArns = new HashMap<>();
        ListTopicsResult listTopicsResult = snsClient.listTopics();

        // We need to loop because listTopics() only returns 100 topics.
        while (true) {
            rateLimiter.acquire();

            // Results are topic ARNs. Topic names is after the last colon (:).
            for (Topic topic : listTopicsResult.getTopics()) {
                String arn = topic.getTopicArn();
                int lastColonIndex = arn.lastIndexOf(':');
                String name = arn.substring(lastColonIndex + 1);
                topicNamesToArns.put(name, arn);
            }

            String nextToken = listTopicsResult.getNextToken();
            if (nextToken == null) {
                // No more results.
                break;
            }
            listTopicsResult = snsClient.listTopics(nextToken);
        }

        // Do we need to create any topics?
        for (Map.Entry<String, String> entry : getSnsTopicProperties().entrySet()) {
            String topicProp = entry.getKey();
            String topicName = bridgeConfig.get(topicProp);

            if (!topicNamesToArns.containsKey(topicName)) {
                LOG.info("Creating SNS topic " + topicName);

                // Create the topic.
                // Note, we don't need to set any SNS attributes. None of them are applicable to our use cases.
                // Additionally, SNS retry policy can only be set for HTTP. For all other channels, see
                // https://docs.aws.amazon.com/sns/latest/dg/sns-message-delivery-retries.html
                CreateTopicResult createTopicResult = snsClient.createTopic(topicName);
                String topicArn = createTopicResult.getTopicArn();
                topicNamesToArns.put(topicName, topicArn);

                // Subscribe the SQS queue to the SNS topic.
                String queueProp = entry.getValue();
                String queueUrl = bridgeConfig.get(queueProp);
                GetQueueAttributesResult queueAttributesResult = sqsClient.getQueueAttributes(queueUrl,
                        QUEUE_ATTRIBUTE_LIST);
                String queueArn = queueAttributesResult.getAttributes().get(ATTR_QUEUE_ARN);

                SubscribeRequest subscribeRequest = new SubscribeRequest();
                subscribeRequest.setEndpoint(queueArn);
                subscribeRequest.setProtocol("sqs");
                subscribeRequest.setTopicArn(topicArn);

                // According to the docs, all attribute values are strings.
                subscribeRequest.addAttributesEntry("RawMessageDelivery", "true");
                snsClient.subscribe(subscribeRequest);

                // Confirmation is not necessary if the SNS topic and the SQS queue are in the same account, which in
                // this case they are.
            }

            // Write topic arns back into the config.
            String topicArn = topicNamesToArns.get(topicName);
            bridgeConfig.set(topicProp + ".arn", topicArn);
        }
    }
}
