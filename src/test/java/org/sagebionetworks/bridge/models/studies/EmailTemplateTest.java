package org.sagebionetworks.bridge.models.studies;

import nl.jqno.equalsverifier.EqualsVerifier;

import org.testng.annotations.Test;

import org.sagebionetworks.bridge.json.BridgeObjectMapper;

import static org.testng.Assert.assertEquals;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

public class EmailTemplateTest {

    @Test
    public void equalsHashCode() {
        EqualsVerifier.forClass(EmailTemplate.class).allFieldsShouldBeUsed().verify();
    }
    
    @Test
    public void deserializeFromDynamoDB() throws Exception {
        // Original serialization used Jackson's default enum serialization, which BridgePF deserializes to correct 
        // MimeType enum. However client API code does not. Verify this older serialization continues to be deserialized
        // correctly after fixing serialization for rest/SDK code.
        String json = "{\"subject\":\"${studyName} sign in link\",\"body\":\"<p>${host}/${token}</p>\",\"mimeType\":\"HTML\"}";
        
        EmailTemplate template = new ObjectMapper().readValue(json, EmailTemplate.class);
        assertEquals(template.getMimeType(), MimeType.HTML);
    }
    
    @Test
    public void canSerialize() throws Exception {
        EmailTemplate template = new EmailTemplate("Subject", "Body", MimeType.TEXT);
        
        String json = BridgeObjectMapper.get().writeValueAsString(template);
        JsonNode node = BridgeObjectMapper.get().readTree(json);
        
        assertEquals(node.get("subject").asText(), "Subject");
        assertEquals(node.get("body").asText(), "Body");
        assertEquals(node.get("type").asText(), "EmailTemplate");
        
        EmailTemplate template2 = BridgeObjectMapper.get().readValue(json, EmailTemplate.class);
        assertEquals(template2, template);
    }

    @Test
    public void mimeTypeHasDefault() {
        EmailTemplate template = new EmailTemplate("Subject", "Body", null);
        assertEquals(template.getMimeType(), MimeType.HTML);
    }
}
