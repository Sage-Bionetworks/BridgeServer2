package org.sagebionetworks.bridge.services;

import com.amazonaws.services.sqs.AmazonSQS;
import com.amazonaws.services.sqs.model.SendMessageResult;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.joda.time.DateTime;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import org.sagebionetworks.bridge.BridgeConstants;
import org.sagebionetworks.bridge.config.BridgeConfig;

/** Implementation of ExportService using SQS. */
@Component
public class ExportViaSqsService implements ExportService {
    private static final Logger LOG = LoggerFactory.getLogger(ExportViaSqsService.class);

    private static final ObjectMapper JSON_OBJECT_MAPPER = new ObjectMapper();

    // constants - these are package scoped so unit tests can access them
    static final String CONFIG_KEY_EXPORTER_SQS_QUEUE_URL = "exporter.request.sqs.queue.url";
    static final String REQUEST_KEY_END_DATE_TIME = "endDateTime";
    static final String REQUEST_KEY_STUDY_WHITELIST = "studyWhitelist";
    static final String REQUEST_KEY_APP_WHITELIST = "appWhitelist";
    static final String REQUEST_KEY_TAG = "tag";
    static final String REQUEST_KEY_USE_LAST_EXPORT_TIME = "useLastExportTime";

    private AmazonSQS sqsClient;
    private BridgeConfig config;

    /** Bridge config, used to get the SQS queue URL. */
    @Autowired
    public final void setBridgeConfig(BridgeConfig bridgeConfig) {
        this.config = bridgeConfig;
    }

    /** SQS client. */
    @Autowired
    public final void setSqsClient(AmazonSQS sqsClient) {
        this.sqsClient = sqsClient;
    }

    /** {@inheritDoc} */
    @Override
    public void startOnDemandExport(String appId) throws JsonProcessingException {
        // endDateTime is set to 5 seconds ago, to account for clock skew.
        String endDateTimeStr = DateTime.now(BridgeConstants.LOCAL_TIME_ZONE).minusSeconds(5).toString();

        // App whitelist is needed because we only export the given app.
        ArrayNode appWhitelistNode = JSON_OBJECT_MAPPER.createArrayNode();
        appWhitelistNode.add(appId);

        // Generate tag, for both logging here and for the BridgeEX request
        String tag = "On-Demand Export appId=" + appId + " endDateTime=" + endDateTimeStr;

        // Create exporter request as a JSON node.
        ObjectNode requestNode = JSON_OBJECT_MAPPER.createObjectNode();
        requestNode.put(REQUEST_KEY_END_DATE_TIME, endDateTimeStr);
        requestNode.set(REQUEST_KEY_APP_WHITELIST, appWhitelistNode);
        requestNode.set(REQUEST_KEY_STUDY_WHITELIST, appWhitelistNode);
        requestNode.put(REQUEST_KEY_TAG, tag);
        requestNode.put(REQUEST_KEY_USE_LAST_EXPORT_TIME, true);

        String requestJsonText = JSON_OBJECT_MAPPER.writeValueAsString(requestNode);

        // Note: SqsInitializer runs after Spring, so we need to grab the queue URL dynamically.
        String sqsQueueUrl = config.getProperty(CONFIG_KEY_EXPORTER_SQS_QUEUE_URL);

        // send to SQS
        SendMessageResult sqsResult = sqsClient.sendMessage(sqsQueueUrl, requestJsonText);
        LOG.info("Sent request to SQS for " + tag + "; received message ID=" + sqsResult.getMessageId());
    }
}
