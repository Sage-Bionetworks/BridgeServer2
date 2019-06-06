package org.sagebionetworks.bridge.dynamodb;

import static org.testng.Assert.assertEquals;

import com.fasterxml.jackson.databind.JsonNode;

import org.testng.annotations.Test;

import org.sagebionetworks.bridge.TestConstants;
import org.sagebionetworks.bridge.json.BridgeObjectMapper;
import org.sagebionetworks.bridge.models.sms.SmsMessage;
import org.sagebionetworks.bridge.models.sms.SmsType;
import org.sagebionetworks.bridge.time.DateUtils;

public class DynamoSmsMessageTest {
    private static final String HEALTH_CODE = "health-code";
    private static final String MESSAGE_BODY = "lorem ipsum";
    private static final String MESSAGE_ID = "my-message-id";
    private static final String PHONE_NUMBER = "+12065550123";

    private static final String SENT_ON_STRING = "2018-10-17T13:53:07.883Z";
    private static final long SENT_ON_MILLIS = DateUtils.convertToMillisFromEpoch(SENT_ON_STRING);

    @Test
    public void serialize() throws Exception {
        // Start with JSON.
        String jsonText = "{\n" +
                "   \"phoneNumber\":\"" + PHONE_NUMBER + "\",\n" +
                "   \"sentOn\":\"" + SENT_ON_STRING + "\",\n" +
                "   \"healthCode\":\"" + HEALTH_CODE + "\",\n" +
                "   \"messageBody\":\"" + MESSAGE_BODY + "\",\n" +
                "   \"messageId\":\"" + MESSAGE_ID + "\",\n" +
                "   \"smsType\":\"" + SmsType.PROMOTIONAL.getValue().toLowerCase() + "\",\n" +
                "   \"studyId\":\"" + TestConstants.TEST_STUDY_IDENTIFIER + "\"\n" +
                "}";

        // Convert to POJO.
        SmsMessage smsMessage = BridgeObjectMapper.get().readValue(jsonText, SmsMessage.class);
        assertEquals(smsMessage.getPhoneNumber(), PHONE_NUMBER);
        assertEquals(smsMessage.getSentOn(), SENT_ON_MILLIS);
        assertEquals(smsMessage.getHealthCode(), HEALTH_CODE);
        assertEquals(smsMessage.getMessageBody(), MESSAGE_BODY);
        assertEquals(smsMessage.getMessageId(), MESSAGE_ID);
        assertEquals(smsMessage.getSmsType(), SmsType.PROMOTIONAL);
        assertEquals(smsMessage.getStudyId(), TestConstants.TEST_STUDY_IDENTIFIER);

        // Convert back to JSON node.
        JsonNode jsonNode = BridgeObjectMapper.get().convertValue(smsMessage, JsonNode.class);
        assertEquals(jsonNode.get("phoneNumber").textValue(), PHONE_NUMBER);
        assertEquals(jsonNode.get("sentOn").textValue(), SENT_ON_STRING);
        assertEquals(jsonNode.get("healthCode").textValue(), HEALTH_CODE);
        assertEquals(jsonNode.get("messageBody").textValue(), MESSAGE_BODY);
        assertEquals(jsonNode.get("messageId").textValue(), MESSAGE_ID);
        assertEquals(jsonNode.get("smsType").textValue(), SmsType.PROMOTIONAL.getValue().toLowerCase());
        assertEquals(jsonNode.get("studyId").textValue(), TestConstants.TEST_STUDY_IDENTIFIER);
    }
}
