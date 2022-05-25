package org.sagebionetworks.bridge;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

import com.amazonaws.services.sqs.AmazonSQS;
import com.amazonaws.services.sqs.model.CreateQueueRequest;
import com.amazonaws.services.sqs.model.CreateQueueResult;
import com.amazonaws.services.sqs.model.ListQueuesResult;
import com.google.common.collect.ImmutableSet;
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

    private static final Set<String> QUEUE_PROPERTIES = ImmutableSet.of("exporter.request.sqs.queue",
            "workerPlatform.request.sqs.queue", "integ.test.sqs.queue");

    // Package-scoped so we can mock this in unit tests.
    Set<String> getQueueProperties() {
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
        for (String prop : getQueueProperties()) {
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

                CreateQueueResult createQueueResult = sqsClient.createQueue(request);
                queueNamesToUrls.put(name, createQueueResult.getQueueUrl());
            }

            // Write queue URLs back into the config.
            String url = queueNamesToUrls.get(name);
            bridgeConfig.set(prop + ".url", url);
        }
    }
}
