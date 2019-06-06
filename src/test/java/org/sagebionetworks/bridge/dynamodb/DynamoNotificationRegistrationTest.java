package org.sagebionetworks.bridge.dynamodb;

import org.joda.time.DateTime;
import org.testng.annotations.Test;

import org.sagebionetworks.bridge.TestUtils;
import org.sagebionetworks.bridge.json.BridgeObjectMapper;
import org.sagebionetworks.bridge.models.OperatingSystem;
import org.sagebionetworks.bridge.models.notifications.NotificationProtocol;
import org.sagebionetworks.bridge.models.notifications.NotificationRegistration;

import static org.testng.Assert.assertEquals;

import com.fasterxml.jackson.databind.JsonNode;

public class DynamoNotificationRegistrationTest {
    
    private static final String DEVICE_ID = "aDeviceId";
    private static final String ENDPOINT = "anEndpoint";
    private static final String GUID = "ABC";
    private static final String HEALTH_CODE = "healthCode";
    private static final String CREATED_ON_STRING = "2017-01-10T20:29:14.319Z";
    private static final String MODIFIED_ON_STRING = "2017-01-11T20:29:14.319Z";
    private static final long CREATED_ON = DateTime.parse(CREATED_ON_STRING).getMillis();
    private static final long MODIFIED_ON = DateTime.parse(MODIFIED_ON_STRING).getMillis();

    @Test
    public void defaultProtocol() {
        // Default value is protocol.
        DynamoNotificationRegistration reg = new DynamoNotificationRegistration();
        assertEquals(reg.getProtocol(), NotificationProtocol.APPLICATION);

        // Set another value.
        reg.setProtocol(NotificationProtocol.SMS);
        assertEquals(reg.getProtocol(), NotificationProtocol.SMS);
    }

    @Test
    public void canSerialize() throws Exception {
        NotificationRegistration reg = NotificationRegistration.create();
        reg.setHealthCode(HEALTH_CODE);
        reg.setGuid(GUID);
        reg.setProtocol(NotificationProtocol.SMS);
        reg.setEndpoint(ENDPOINT);
        reg.setDeviceId(DEVICE_ID);
        reg.setOsName(OperatingSystem.ANDROID);
        reg.setCreatedOn(CREATED_ON);
        reg.setModifiedOn(MODIFIED_ON);
        
        JsonNode node = BridgeObjectMapper.get().valueToTree(reg);
        assertEquals(node.get("guid").asText(), GUID);
        assertEquals(node.get("protocol").textValue(), "sms");
        assertEquals(node.get("endpoint").textValue(), ENDPOINT);
        assertEquals(node.get("deviceId").asText(), DEVICE_ID);
        assertEquals(node.get("osName").asText(), OperatingSystem.ANDROID);
        assertEquals(node.get("createdOn").asText(), CREATED_ON_STRING);
        assertEquals(node.get("modifiedOn").asText(), MODIFIED_ON_STRING);
        assertEquals(node.get("type").asText(), "NotificationRegistration");
        assertEquals(node.size(), 8); // and no other fields like healthCode;
        
        // In creating a registration, the deviceId, osName and sometimes the  guid, so these must 
        // deserialize correctly. Other fields will be set on the server.
        String json = TestUtils.createJson("{'guid':'ABC','protocol':'sms','deviceId':'aDeviceId'," +
                "'osName':'iPhone OS'}");
        
        NotificationRegistration deser = BridgeObjectMapper.get().readValue(json, NotificationRegistration.class);
        assertEquals(deser.getGuid(), GUID);
        assertEquals(deser.getProtocol(), NotificationProtocol.SMS);
        assertEquals(deser.getDeviceId(), DEVICE_ID);
        assertEquals(deser.getOsName(), OperatingSystem.IOS);
    }
}
