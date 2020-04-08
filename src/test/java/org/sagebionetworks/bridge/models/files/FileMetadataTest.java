package org.sagebionetworks.bridge.models.files;

import static org.sagebionetworks.bridge.BridgeConstants.API_APP_ID;
import static org.sagebionetworks.bridge.TestConstants.GUID;
import static org.sagebionetworks.bridge.TestConstants.TIMESTAMP;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertNull;
import static org.testng.Assert.assertTrue;

import com.fasterxml.jackson.databind.JsonNode;

import org.mockito.Mockito;
import org.testng.annotations.Test;

import org.sagebionetworks.bridge.json.BridgeObjectMapper;

import nl.jqno.equalsverifier.EqualsVerifier;

public class FileMetadataTest extends Mockito {
    
    @Test
    public void equalsHashCode() {
        EqualsVerifier.forClass(FileMetadata.class).allFieldsShouldBeUsed().verify();
    }
    
    @Test
    public void canSerialize() throws Exception { 
        FileMetadata metadata = new FileMetadata();
        metadata.setStudyId(API_APP_ID);
        metadata.setName("oneName");
        metadata.setGuid(GUID);
        metadata.setDescription("oneDescription");
        metadata.setDeleted(true);
        metadata.setVersion(3L);
        metadata.setCreatedOn(TIMESTAMP);
        metadata.setModifiedOn(TIMESTAMP.plusHours(1));
        
        JsonNode node = BridgeObjectMapper.get().valueToTree(metadata);
        assertEquals(node.get("name").textValue(), "oneName");
        assertEquals(node.get("guid").textValue(), GUID);
        assertEquals(node.get("description").textValue(), "oneDescription");
        assertTrue(node.get("deleted").booleanValue());
        assertEquals(node.get("version").intValue(), 3);
        assertEquals(node.get("createdOn").textValue(), TIMESTAMP.toString());
        assertEquals(node.get("modifiedOn").textValue(), TIMESTAMP.plusHours(1).toString());        
        assertEquals(node.get("type").textValue(), "FileMetadata");
        assertNull(node.get("studyId"));
        
        FileMetadata deser = BridgeObjectMapper.get().readValue(node.toString(), FileMetadata.class);
        assertNull(deser.getStudyId());
        assertEquals(deser.getName(), "oneName");
        assertEquals(deser.getGuid(), GUID);
        assertEquals(deser.getDescription(), "oneDescription");
        assertTrue(deser.isDeleted());
        assertEquals(deser.getVersion(), 3L);
        assertEquals(deser.getCreatedOn(), TIMESTAMP);
        assertEquals(deser.getModifiedOn(), TIMESTAMP.plusHours(1));
    }

}
