package org.sagebionetworks.bridge.models.permissions;

import static org.sagebionetworks.bridge.TestConstants.CREATED_ON;
import static org.sagebionetworks.bridge.TestConstants.GUID;
import static org.sagebionetworks.bridge.TestConstants.MODIFIED_ON;
import static org.sagebionetworks.bridge.TestConstants.TEST_APP_ID;
import static org.sagebionetworks.bridge.TestConstants.TEST_STUDY_ID;
import static org.sagebionetworks.bridge.TestConstants.TEST_USER_ID;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertNull;

import com.fasterxml.jackson.databind.JsonNode;
import nl.jqno.equalsverifier.EqualsVerifier;
import org.sagebionetworks.bridge.json.BridgeObjectMapper;
import org.testng.annotations.Test;

public class PermissionTest {
    
    @Test
    public void equalsHashCode() {
        EqualsVerifier.forClass(Permission.class).allFieldsShouldBeUsed().verify();
    }
    
    @Test
    public void canSerialize() throws Exception {
        Permission permission = new Permission();
        permission.setGuid(GUID);
        permission.setAppId(TEST_APP_ID);
        permission.setUserId(TEST_USER_ID);
        permission.setAccessLevel(AccessLevel.ADMIN);
        permission.setEntityType(EntityType.STUDY);
        permission.setEntityId(TEST_STUDY_ID);
        permission.setCreatedOn(CREATED_ON);
        permission.setModifiedOn(MODIFIED_ON);
        permission.setVersion(10L);
        
        JsonNode node = BridgeObjectMapper.get().valueToTree(permission);
        assertEquals(node.size(), 9);
        assertEquals(node.get("guid").textValue(), GUID);
        assertNull(node.get("appId"));
        assertEquals(node.get("userId").textValue(), TEST_USER_ID);
        assertEquals(node.get("accessLevel").textValue(), "admin");
        assertEquals(node.get("entityType").textValue(), "study");
        assertEquals(node.get("entityId").textValue(), TEST_STUDY_ID);
        assertEquals(node.get("createdOn").textValue(), CREATED_ON.toString());
        assertEquals(node.get("modifiedOn").textValue(), MODIFIED_ON.toString());
        assertEquals(node.get("version").longValue(), 10L);
        assertEquals(node.get("type").textValue(), "Permission");
        
        Permission deser = BridgeObjectMapper.get().readValue(node.toString(), Permission.class);
        assertEquals(deser.getGuid(), GUID);
        assertNull(deser.getAppId());
        assertEquals(deser.getUserId(), TEST_USER_ID);
        assertEquals(deser.getAccessLevel(), AccessLevel.ADMIN);
        assertEquals(deser.getEntityType(), EntityType.STUDY);
        assertEquals(deser.getEntityId(), TEST_STUDY_ID);
        assertEquals(deser.getCreatedOn(), CREATED_ON);
        assertEquals(deser.getModifiedOn(), MODIFIED_ON);
        assertEquals(deser.getVersion(), 10L);
    }
}
