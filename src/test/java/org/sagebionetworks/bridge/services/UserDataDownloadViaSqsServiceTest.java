package org.sagebionetworks.bridge.services;

import static org.mockito.Mockito.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.sagebionetworks.bridge.TestConstants.TEST_APP_ID;
import static org.sagebionetworks.bridge.services.UserDataDownloadViaSqsService.CONFIG_KEY_UDD_SQS_QUEUE_URL;
import static org.sagebionetworks.bridge.services.UserDataDownloadViaSqsService.REQUEST_KEY_APP_ID;
import static org.sagebionetworks.bridge.services.UserDataDownloadViaSqsService.REQUEST_KEY_BODY;
import static org.sagebionetworks.bridge.services.UserDataDownloadViaSqsService.REQUEST_KEY_END_DATE;
import static org.sagebionetworks.bridge.services.UserDataDownloadViaSqsService.REQUEST_KEY_SERVICE;
import static org.sagebionetworks.bridge.services.UserDataDownloadViaSqsService.REQUEST_KEY_START_DATE;
import static org.sagebionetworks.bridge.services.UserDataDownloadViaSqsService.REQUEST_KEY_STUDY_ID;
import static org.sagebionetworks.bridge.services.UserDataDownloadViaSqsService.REQUEST_KEY_USER_ID;
import static org.sagebionetworks.bridge.services.UserDataDownloadViaSqsService.UDD_SERVICE_TITLE;
import static org.testng.Assert.assertEquals;

import com.amazonaws.services.sqs.AmazonSQS;
import com.amazonaws.services.sqs.model.SendMessageResult;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.joda.time.LocalDate;
import org.mockito.ArgumentCaptor;
import org.testng.annotations.Test;

import org.sagebionetworks.bridge.config.BridgeConfig;
import org.sagebionetworks.bridge.models.DateRange;

public class UserDataDownloadViaSqsServiceTest {
    private static final String START_DATE = "2015-08-15";
    private static final String END_DATE = "2015-08-19";
    private static final ObjectMapper JSON_OBJECT_MAPPER = new ObjectMapper();
    private static final String SQS_MESSAGE_ID = "dummy-message-id";
    private static final String SQS_URL = "dummy-sqs-url";
    private static final String USER_ID = "test-user-id";

    @Test
    public void test() throws Exception {

        // main test strategy is to validate that the args get transformed and sent to SQS as expected

        // mock config
        BridgeConfig mockConfig = mock(BridgeConfig.class);
        when(mockConfig.getProperty(CONFIG_KEY_UDD_SQS_QUEUE_URL)).thenReturn(SQS_URL);

        // mock SQS
        AmazonSQS mockSqsClient = mock(AmazonSQS.class);
        SendMessageResult mockSqsResult = new SendMessageResult().withMessageId(SQS_MESSAGE_ID);
        ArgumentCaptor<String> sqsMessageCaptor = ArgumentCaptor.forClass(String.class);
        when(mockSqsClient.sendMessage(eq(SQS_URL), sqsMessageCaptor.capture())).thenReturn(mockSqsResult);

        // set up test service
        UserDataDownloadViaSqsService testService = new UserDataDownloadViaSqsService();
        testService.setBridgeConfig(mockConfig);
        testService.setSqsClient(mockSqsClient);

        // test inputs
        DateRange dateRange = new DateRange(LocalDate.parse(START_DATE), LocalDate.parse(END_DATE));

        // execute
        testService.requestUserData(TEST_APP_ID, USER_ID, dateRange);

        // Validate SQS args.
        String sqsMessageText = sqsMessageCaptor.getValue();

        JsonNode sqsMessageNode = JSON_OBJECT_MAPPER.readTree(sqsMessageText);

        // first assert parent node
        assertEquals(sqsMessageNode.size(), 2);
        assertEquals(sqsMessageNode.get(REQUEST_KEY_SERVICE).asText(), UDD_SERVICE_TITLE);

        // then assert body node
        JsonNode msgBody = sqsMessageNode.path(REQUEST_KEY_BODY);
        assertEquals(msgBody.size(), 5);

        assertEquals(msgBody.get(REQUEST_KEY_STUDY_ID).textValue(), TEST_APP_ID);
        assertEquals(msgBody.get(REQUEST_KEY_APP_ID).textValue(), TEST_APP_ID);
        assertEquals(msgBody.get(REQUEST_KEY_USER_ID).textValue(), USER_ID);
        assertEquals(msgBody.get(REQUEST_KEY_START_DATE).textValue(), START_DATE);
        assertEquals(msgBody.get(REQUEST_KEY_END_DATE).textValue(), END_DATE);
    }
}
