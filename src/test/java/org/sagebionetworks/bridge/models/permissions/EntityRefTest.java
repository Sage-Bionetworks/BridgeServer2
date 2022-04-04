package org.sagebionetworks.bridge.models.permissions;

import com.fasterxml.jackson.databind.JsonNode;
import nl.jqno.equalsverifier.EqualsVerifier;
import org.sagebionetworks.bridge.json.BridgeObjectMapper;
import org.testng.annotations.Test;

import static org.sagebionetworks.bridge.TestConstants.TEST_STUDY_ID;
import static org.testng.Assert.assertEquals;

public class EntityRefTest {
    
    @Test
    public void equalsHashCode() {
        EqualsVerifier.forClass(EntityRef.class).allFieldsShouldBeUsed().verify();;
    }
    
    @Test
    public void canSerialize() {
        EntityRef entityRef = new EntityRef(EntityType.STUDY, TEST_STUDY_ID, "test-study-name");
        
        JsonNode node = BridgeObjectMapper.get().valueToTree(entityRef);
        assertEquals(node.size(), 4);
        assertEquals(node.get("entityType").textValue(), "study");
        assertEquals(node.get("entityId").textValue(), TEST_STUDY_ID);
        assertEquals(node.get("entityName").textValue(), "test-study-name");
        assertEquals(node.get("type").textValue(), "EntityRef");
    }
} 