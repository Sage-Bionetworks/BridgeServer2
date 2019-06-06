package org.sagebionetworks.bridge.models.subpopulations;

import org.testng.annotations.Test;

import org.sagebionetworks.bridge.json.BridgeObjectMapper;

import static org.testng.Assert.assertEquals;

import com.fasterxml.jackson.databind.JsonNode;

public class StudyConsentFormTest {

    // The only part of a study consent that users send to the server is the html content
    // in a JSON payload. The rest is constructed on the server.
    @Test
    public void canSerialize() throws Exception {
        StudyConsentForm form = new StudyConsentForm("<p>This is content</p>");
        
        String json = BridgeObjectMapper.get().writeValueAsString(form);
        JsonNode node = BridgeObjectMapper.get().readTree(json);
        
        assertEquals(node.get("documentContent").asText(), "<p>This is content</p>");
        assertEquals(node.get("type").asText(), "StudyConsent");
        
        StudyConsentForm newForm = BridgeObjectMapper.get().readValue(json, StudyConsentForm.class);
        assertEquals(newForm.getDocumentContent(), form.getDocumentContent());
    }
    
}
