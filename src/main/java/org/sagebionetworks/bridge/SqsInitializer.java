package org.sagebionetworks.bridge;

import java.util.HashMap;
import java.util.Map;

import com.amazonaws.services.sqs.AmazonSQS;
import com.amazonaws.services.sqs.model.CreateQueueRequest;
import com.amazonaws.services.sqs.model.CreateQueueResult;
import com.amazonaws.services.sqs.model.ListQueuesResult;
import com.google.common.collect.ImmutableMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import org.sagebionetworks.bridge.config.BridgeConfig;

/** Initializes SQS queues based on config. */
@Component
public class SqsInitializer {
    private static final Logger LOG = LoggerFactory.getLogger(SqsInitializer.class);

    static final String CONFIG_KEY_DEAD_LETTER_QUEUE_ARN = "dead.letter.queue.arn";
    static final String POLICY_SNS_ALLOWED = "{\n" +
            "  \"Statement\": [{\n" +
            "    \"Sid\": \"Allow-SNS-SendMessage\",\n" +
            "    \"Effect\": \"Allow\",\n" +
            "    \"Principal\": {\n" +
            "      \"Service\": \"sns.amazonaws.com\"\n" +
            "    },\n" +
            "    \"Action\": [\"sqs:SendMessage\"],\n" +
            "    \"Resource\": \"*\"\n" +
            "  }]\n" +
            "}";

    private BridgeConfig bridgeConfig;
    private String deadLetterQueueArn;
    private AmazonSQS sqsClient;

    @Autowired
    public final void setBridgeConfig(BridgeConfig bridgeConfig) {
        this.bridgeConfig = bridgeConfig;
        this.deadLetterQueueArn = bridgeConfig.get(CONFIG_KEY_DEAD_LETTER_QUEUE_ARN);
    }

    @Autowired
    public final void setSqsClient(AmazonSQS sqsClient) {
        this.sqsClient = sqsClient;
    }

    // Key is the config key that holds the queue name. Value is a boolean of whether SNS should be allowed for this
    // queue.
    private static final Map<String, Boolean> QUEUE_PROPERTIES = ImmutableMap.<String, Boolean>builder()
            .put("exporter.request.sqs.queue", false)
            .put("s3.notification.sqs.queue", true)
            .put("virus.scan.result.sqs.queue", true)
            .put("workerPlatform.request.sqs.queue", false)
            .put("integ.test.sqs.queue", true)
            .build();

    // Package-scoped so we can mock this in unit tests.
    Map<String, Boolean> getQueueProperties() {
        return QUEUE_PROPERTIES;
    }

    public void initQueues() {
        // Get existing queue names. This gets 1000 queues, so we _probably_ don't need to paginate.
        Map<String, String> queueNamesToUrls = new HashMap<>();
        ListQueuesResult listQueuesResult = sqsClient.listQueues();

        // Results are queue URLs. Queue names is after the last foreslash (/).
        for (String url : listQueuesResult.getQueueUrls()) {
            int lastSlashIndex = url.lastIndexOf('/');
            String name = url.substring(lastSlashIndex + 1);
            queueNamesToUrls.put(name, url);
        }

        // Do we need to create any queues?
        for (Map.Entry<String, Boolean> entry : getQueueProperties().entrySet()) {
            String prop = entry.getKey();
            boolean snsAllowed = entry.getValue();
            String name = bridgeConfig.get(prop);

            if (!queueNamesToUrls.containsKey(name)) {
                LOG.info("Creating SQS queue " + name);

                // Create the queue.
                CreateQueueRequest request = new CreateQueueRequest();
                request.setQueueName(name);

                // Yes, according to the docs, all attribute values are strings.
                request.addAttributesEntry("DelaySeconds", "0");
                request.addAttributesEntry("MaximumMessageSize", "12288");  // 12kb
                request.addAttributesEntry("MessageRetentionPeriod", "1209600");  // 14 days
                request.addAttributesEntry("ReceiveMessageWaitTimeSeconds", "0");
                request.addAttributesEntry("VisibilityTimeout", "14400");  // 4 hours

                // Dead Letter Queue has sub-attributes in JSON format.
                String redrivePolicy = "{\"deadLetterTargetArn\":\"" + deadLetterQueueArn + "\"," +
                        "\"maxReceiveCount\":\"5\"}";
                request.addAttributesEntry("RedrivePolicy", redrivePolicy);

                if (snsAllowed) {
                    // We need to explicitly allow SNS to write to our queue.
                    request.addAttributesEntry("Policy", POLICY_SNS_ALLOWED);
                }

                CreateQueueResult createQueueResult = sqsClient.createQueue(request);
                queueNamesToUrls.put(name, createQueueResult.getQueueUrl());
            }

            // Write queue URLs back into the config.
            String url = queueNamesToUrls.get(name);
            bridgeConfig.set(prop + ".url", url);
        }
    }
}
