package org.sagebionetworks.bridge.models.apps;

import org.testng.annotations.Test;

import org.sagebionetworks.bridge.json.BridgeObjectMapper;
import org.sagebionetworks.bridge.models.apps.EmailVerificationStatusHolder;
import org.sagebionetworks.bridge.services.EmailVerificationStatus;

import static org.testng.Assert.assertEquals;

import com.fasterxml.jackson.databind.JsonNode;

public class EmailVerificationStatusHolderTest {
    
    @Test
    public void serializesCorrectly() throws Exception {
        EmailVerificationStatusHolder holder = new EmailVerificationStatusHolder(EmailVerificationStatus.PENDING);
        
        String json = BridgeObjectMapper.get().writeValueAsString(holder);
        JsonNode node = BridgeObjectMapper.get().readTree(json);
        
        assertEquals(node.get("type").asText(), "EmailVerificationStatus");
        assertEquals(node.get("status").asText(), "pending");
        
        EmailVerificationStatusHolder newHolder = BridgeObjectMapper.get().readValue(json, EmailVerificationStatusHolder.class);
        assertEquals(newHolder.getStatus(), holder.getStatus());
    }
}
