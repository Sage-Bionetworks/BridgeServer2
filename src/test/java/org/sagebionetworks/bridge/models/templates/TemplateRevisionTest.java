package org.sagebionetworks.bridge.models.templates;

import static org.sagebionetworks.bridge.TestConstants.TIMESTAMP;
import static org.sagebionetworks.bridge.models.studies.MimeType.HTML;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertNull;

import com.fasterxml.jackson.databind.JsonNode;

import org.testng.annotations.Test;

import org.sagebionetworks.bridge.json.BridgeObjectMapper;

public class TemplateRevisionTest {
    
    @Test
    public void canSerialize() throws Exception { 
        TemplateRevision revision = TemplateRevision.create();
        revision.setTemplateGuid("templateGuid");
        revision.setCreatedBy("12345");
        revision.setCreatedOn(TIMESTAMP);
        revision.setStoragePath("some.path.property");
        revision.setMimeType(HTML);
        revision.setSubject("A subject");
        revision.setDocumentContent("The content we retrieved from S3");
        
        JsonNode node = BridgeObjectMapper.get().valueToTree(revision);
        assertNull(node.get("templateGuid"));
        assertEquals(node.get("createdBy").textValue(), "12345");
        assertEquals(node.get("createdOn").textValue(), TIMESTAMP.toString());
        assertNull(node.get("storagePath"));
        assertEquals(node.get("mimeType").textValue(), "text/html");
        assertEquals(node.get("subject").textValue(), "A subject");
        assertEquals(node.get("documentContent").textValue(), "The content we retrieved from S3");
        assertEquals(node.get("type").textValue(), "TemplateRevision");
        
        TemplateRevision deser = BridgeObjectMapper.get().readValue(node.toString(), TemplateRevision.class);
        assertEquals(deser.getCreatedBy(), "12345");
        assertEquals(deser.getCreatedOn(), TIMESTAMP);
        assertEquals(deser.getMimeType(), HTML);
        assertEquals(deser.getSubject(), "A subject");
        assertEquals(deser.getDocumentContent(), "The content we retrieved from S3");
    }
}
