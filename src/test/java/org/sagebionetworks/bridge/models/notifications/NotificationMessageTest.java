package org.sagebionetworks.bridge.models.notifications;

import org.testng.annotations.Test;

import org.sagebionetworks.bridge.TestUtils;
import org.sagebionetworks.bridge.json.BridgeObjectMapper;

import static org.testng.Assert.assertEquals;

import com.fasterxml.jackson.databind.JsonNode;

import nl.jqno.equalsverifier.EqualsVerifier;

public class NotificationMessageTest {
    
    @Test
    public void equalsHashCode() {
        EqualsVerifier.forClass(NotificationMessage.class).allFieldsShouldBeUsed().verify();
    }
    
    @Test
    public void canSerialize() throws Exception {
        String json = TestUtils.createJson("{'lang': 'en', 'subject':'The Subject','message':'The Message'}");

        NotificationMessage message = BridgeObjectMapper.get().readValue(json, NotificationMessage.class);
        assertEquals(message.getLang(), "en");
        assertEquals(message.getSubject(), "The Subject");
        assertEquals(message.getMessage(), "The Message");
        
        JsonNode node = BridgeObjectMapper.get().valueToTree(message);
        assertEquals(node.get("lang").textValue(), "en");
        assertEquals(node.get("subject").textValue(), "The Subject");
        assertEquals(node.get("message").textValue(), "The Message");
        assertEquals(node.get("type").textValue(), "NotificationMessage");
    }
}
