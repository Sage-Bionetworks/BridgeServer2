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
import org.sagebionetworks.bridge.models.accounts.Account;
import org.sagebionetworks.bridge.models.accounts.AccountRef;
import org.testng.annotations.Test;

public class PermissionDetailTest {
    
    @Test
    public void equalsHashCode() {
        EqualsVerifier.forClass(PermissionDetail.class).allFieldsShouldBeUsed().verify();;
    }
    
    @Test
    public void canSerialize() {
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
        
        Account account = Account.create();
        account.setEmail("testAccount@testEmail.com");
        AccountRef accountRef = new AccountRef(account);
        
        EntityRef entityRef = new EntityRef(EntityType.STUDY, TEST_STUDY_ID, "test-study-name");
        
        PermissionDetail permissionDetail = new PermissionDetail(permission, entityRef, accountRef);
        
        JsonNode node = BridgeObjectMapper.get().valueToTree(permissionDetail);
        assertEquals(node.size(), 8);
        assertEquals(node.get("guid").textValue(), GUID);
        assertNull(node.get("appId"));
        assertEquals(node.get("accessLevel").textValue(), "admin");
        assertEquals(node.get("account").get("email").textValue(), "testAccount@testEmail.com");
        assertEquals(node.get("entity").get("entityType").textValue(), "study");
        assertEquals(node.get("entity").get("entityId").textValue(), TEST_STUDY_ID);
        assertEquals(node.get("entity").get("entityName").textValue(), "test-study-name");
        assertEquals(node.get("createdOn").textValue(), CREATED_ON.toString());
        assertEquals(node.get("modifiedOn").textValue(), MODIFIED_ON.toString());
        assertEquals(node.get("version").longValue(), 10L);
        assertEquals(node.get("type").textValue(), "PermissionDetail");
    }
} 