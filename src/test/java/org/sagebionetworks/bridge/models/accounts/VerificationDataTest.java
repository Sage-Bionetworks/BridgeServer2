package org.sagebionetworks.bridge.models.accounts;

import static org.sagebionetworks.bridge.TestConstants.TEST_APP_ID;
import static org.sagebionetworks.bridge.TestConstants.TIMESTAMP;
import static org.sagebionetworks.bridge.TestConstants.TEST_USER_ID;

import com.fasterxml.jackson.databind.JsonNode;

import static org.testng.Assert.assertEquals;

import org.testng.annotations.Test;

import org.sagebionetworks.bridge.TestUtils;
import org.sagebionetworks.bridge.json.BridgeObjectMapper;
import org.sagebionetworks.bridge.services.AuthenticationService.ChannelType;

public class VerificationDataTest {
    @Test
    public void serializeVerificationData() throws Exception { 
        VerificationData data = new VerificationData.Builder()
                .withAppId(TEST_APP_ID)
                .withType(ChannelType.PHONE)
                .withUserId(TEST_USER_ID)
                .withExpiresOn(TIMESTAMP.getMillis()).build();
        
        JsonNode node = BridgeObjectMapper.get().valueToTree(data);
        assertEquals(node.get("appId").textValue(), TEST_APP_ID);
        assertEquals(node.get("type").textValue(), "phone");
        assertEquals(node.get("userId").textValue(), TEST_USER_ID);
        assertEquals(node.get("expiresOn").longValue(), TIMESTAMP.getMillis());
        
        VerificationData deser = BridgeObjectMapper.get().readValue(node.toString(),
                VerificationData.class);
        assertEquals(deser.getAppId(), TEST_APP_ID);
        assertEquals(deser.getType(), ChannelType.PHONE);
        assertEquals(deser.getUserId(), TEST_USER_ID);
        assertEquals(deser.getExpiresOn(), TIMESTAMP.getMillis());
    }
    
    @Test
    public void restoreVerificationDataWithStudyId() throws Exception {
        String json = TestUtils.createJson("{'studyId':'"+TEST_APP_ID+"','type':'email','userId':'"+
                TEST_USER_ID+"',"+"'expiresOn':1422319112486}");
        VerificationData deser = BridgeObjectMapper.get().readValue(json,
                VerificationData.class);
        assertEquals(deser.getAppId(), TEST_APP_ID);
        assertEquals(deser.getType(), ChannelType.EMAIL);
        assertEquals(deser.getUserId(), TEST_USER_ID);
        assertEquals(deser.getExpiresOn(), TIMESTAMP.getMillis());
    }
    
    @Test
    public void restoreVerificationDataWithAppId() throws Exception {
        String json = TestUtils.createJson("{'appId':'"+TEST_APP_ID+"','type':'email','userId':'"+
                TEST_USER_ID+"',"+"'expiresOn':1422319112486}");
        VerificationData deser = BridgeObjectMapper.get().readValue(json,
                VerificationData.class);
        assertEquals(deser.getAppId(), TEST_APP_ID);
        assertEquals(deser.getType(), ChannelType.EMAIL);
        assertEquals(deser.getUserId(), TEST_USER_ID);
        assertEquals(deser.getExpiresOn(), TIMESTAMP.getMillis());
    }
}
