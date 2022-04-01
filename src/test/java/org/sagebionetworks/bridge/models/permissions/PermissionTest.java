package org.sagebionetworks.bridge.models.permissions;

import static org.sagebionetworks.bridge.TestConstants.CREATED_ON;
import static org.sagebionetworks.bridge.TestConstants.GUID;
import static org.sagebionetworks.bridge.TestConstants.MODIFIED_ON;
import static org.sagebionetworks.bridge.TestConstants.TEST_APP_ID;
import static org.sagebionetworks.bridge.TestConstants.TEST_STUDY_ID;
import static org.sagebionetworks.bridge.TestConstants.TEST_USER_ID;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertNull;
import static org.testng.Assert.fail;

import com.fasterxml.jackson.databind.JsonNode;
import nl.jqno.equalsverifier.EqualsVerifier;
import org.sagebionetworks.bridge.json.BridgeObjectMapper;
import org.testng.annotations.Test;

public class PermissionTest {
    
    private String testEntityId = "test-entity-id";
    
    @Test
    public void equalsHashCode() {
        EqualsVerifier.forClass(Permission.class).allFieldsShouldBeUsedExcept("assessmentId",
                "organizationId", "studyId").verify();
    }
    
    @Test
    public void canSerialize() throws Exception {
        Permission permission = createPermission();
        
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
        assertNull(deser.getAssessmentId());
        assertNull(deser.getOrganizationId());
        assertEquals(deser.getStudyId(), TEST_STUDY_ID);
    }
    
    @Test
    public void settingAssessmentId() {
        Permission permission = createPermission();
        permission.setEntityType(EntityType.ASSESSMENT);
        
        assertEquals(permission.getAssessmentId(), TEST_STUDY_ID);
        assertNull(permission.getOrganizationId());
        assertNull(permission.getStudyId());
        
        permission.setEntityId("test-assessment-id");
    
        assertEquals(permission.getAssessmentId(), "test-assessment-id");
        assertNull(permission.getOrganizationId());
        assertNull(permission.getStudyId());
    }
    
    @Test
    public void foreignIdsSetWithEntitySetters() {
        Permission permission = createPermission();
        for (EntityType entityType : EntityType.values()) {
            permission.setEntityId(testEntityId);
            permission.setEntityType(entityType);
            if (EntityType.ASSESSMENT_TYPES.contains(entityType)) {
                assertEquals(permission.getAssessmentId(), testEntityId);
                assertNull(permission.getOrganizationId());
                assertNull(permission.getStudyId());
            } else if (EntityType.ORGANIZATION_TYPES.contains(entityType)) {
                assertEquals(permission.getOrganizationId(), testEntityId);
                assertNull(permission.getAssessmentId());
                assertNull(permission.getStudyId());
            } else if (EntityType.STUDY_TYPES.contains(entityType)) {
                assertEquals(permission.getStudyId(), testEntityId);
                assertNull(permission.getAssessmentId());
                assertNull(permission.getOrganizationId());
            } else {
                fail("Foreign ID not set for EntityType: " + entityType);
            }
            
            permission.setEntityId(entityType.toString());
            if (EntityType.ASSESSMENT_TYPES.contains(entityType)) {
                assertEquals(permission.getAssessmentId(), entityType.toString());
                assertNull(permission.getOrganizationId());
                assertNull(permission.getStudyId());
            } else if (EntityType.ORGANIZATION_TYPES.contains(entityType)) {
                assertEquals(permission.getOrganizationId(), entityType.toString());
                assertNull(permission.getAssessmentId());
                assertNull(permission.getStudyId());
            } else if (EntityType.STUDY_TYPES.contains(entityType)) {
                assertEquals(permission.getStudyId(), entityType.toString());
                assertNull(permission.getAssessmentId());
                assertNull(permission.getOrganizationId());
            } else {
                fail("Foreign ID not updated for EntityType: " + entityType);
            }
        }
    }
    
    @Test
    public void nullingEntityTypeNullsForeignId() {
        Permission permission = createPermission();
        permission.setEntityType(null);
        assertNull(permission.getAssessmentId());
        assertNull(permission.getOrganizationId());
        assertNull(permission.getStudyId());
    }
    
    @Test
    public void nullingEntityIdNullsForeignId() {
        Permission permission = createPermission();
        permission.setEntityId(null);
        assertNull(permission.getAssessmentId());
        assertNull(permission.getOrganizationId());
        assertNull(permission.getStudyId());
    }
    
    private Permission createPermission() {
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
        return permission;
    }
}