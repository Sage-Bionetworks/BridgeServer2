package org.sagebionetworks.bridge.models.permissions;

import com.fasterxml.jackson.databind.JsonNode;
import nl.jqno.equalsverifier.EqualsVerifier;
import nl.jqno.equalsverifier.Warning;
import org.sagebionetworks.bridge.json.BridgeObjectMapper;
import org.testng.annotations.Test;

import static org.sagebionetworks.bridge.TestConstants.TEST_APP_ID;
import static org.sagebionetworks.bridge.TestConstants.TEST_STUDY_ID;
import static org.sagebionetworks.bridge.TestConstants.TEST_USER_ID;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertNull;

public class PermissionTest {
    
    @Test
    public void equalsHashCode() {
        EqualsVerifier.forClass(Permission.class)
                .suppress(Warning.NONFINAL_FIELDS).allFieldsShouldBeUsed().verify();
    }
    
    @Test
    public void canSerialize() throws Exception {
        Permission permission = new Permission();
        permission.setGuid("testGuid");
        permission.setAppId(TEST_APP_ID);
        permission.setUserId(TEST_USER_ID);
        permission.setAccessLevel(PermissionAccessLevel.ADMIN);
        permission.setEntityType(EntityType.STUDY);
        permission.setEntityId(TEST_STUDY_ID);
    
        JsonNode node = BridgeObjectMapper.get().valueToTree(permission);
        assertEquals(node.get("guid").textValue(), "testGuid");
        assertNull(node.get("appId"));
        assertEquals(node.get("userId").textValue(), TEST_USER_ID);
        assertEquals(node.get("accessLevel").textValue(), "admin");
        assertEquals(node.get("entityType").textValue(), "study");
        assertEquals(node.get("entityId").textValue(), TEST_STUDY_ID);
        
        Permission deser = BridgeObjectMapper.get().readValue(node.toString(), Permission.class);
        assertEquals(deser.getGuid(), "testGuid");
        assertNull(deser.getAppId());
        assertEquals(deser.getUserId(), TEST_USER_ID);
        assertEquals(deser.getAccessLevel(), PermissionAccessLevel.ADMIN);
        assertEquals(deser.getEntityType(), EntityType.STUDY);
        assertEquals(deser.getEntityId(), TEST_STUDY_ID);
    }
}
