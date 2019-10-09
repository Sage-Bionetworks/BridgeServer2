package org.sagebionetworks.bridge.models.files;

import static org.sagebionetworks.bridge.TestConstants.GUID;
import static org.sagebionetworks.bridge.TestConstants.TIMESTAMP;
import static org.sagebionetworks.bridge.models.files.FileRevisionStatus.AVAILABLE;
import static org.testng.Assert.assertEquals;

import com.fasterxml.jackson.databind.JsonNode;

import org.testng.annotations.Test;

import org.sagebionetworks.bridge.json.BridgeObjectMapper;

public class FileRevisionTest {

    @Test
    public void canSerialize() throws Exception {
        FileRevision revision = new FileRevision();
        revision.setFileGuid(GUID);
        revision.setCreatedOn(TIMESTAMP);
        revision.setName("oneName");
        revision.setDescription("oneDescription");
        revision.setSize(3000L);
        revision.setUploadURL("uploadURL");
        revision.setStatus(AVAILABLE);
        revision.setMimeType("application/pdf");
        revision.setDownloadURL("downloadURL");
        
        JsonNode node = BridgeObjectMapper.get().valueToTree(revision);
        assertEquals(node.get("fileGuid").textValue(), GUID);
        assertEquals(node.get("createdOn").textValue(), TIMESTAMP.toString());
        assertEquals(node.get("name").textValue(), "oneName");
        assertEquals(node.get("description").textValue(), "oneDescription");
        assertEquals(node.get("size").longValue(), 3000L);
        assertEquals(node.get("uploadURL").textValue(), "uploadURL");
        assertEquals(node.get("status").textValue(), "available");
        assertEquals(node.get("mimeType").textValue(), "application/pdf");
        assertEquals(node.get("downloadURL").textValue(), "downloadURL");
        
        FileRevision deser = BridgeObjectMapper.get().readValue(node.toString(), FileRevision.class);
        assertEquals(deser.getFileGuid(), GUID);
        assertEquals(deser.getCreatedOn(), TIMESTAMP);
        assertEquals(deser.getName(), "oneName");
        assertEquals(deser.getDescription(), "oneDescription");
        assertEquals(deser.getSize(), new Long(3000));
        assertEquals(deser.getUploadURL(), "uploadURL");
        assertEquals(deser.getStatus(), AVAILABLE);
        assertEquals(deser.getMimeType(), "application/pdf");
        assertEquals(deser.getDownloadURL(), "downloadURL");
    }
}
