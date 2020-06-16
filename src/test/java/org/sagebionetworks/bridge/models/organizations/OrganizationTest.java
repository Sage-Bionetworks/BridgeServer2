package org.sagebionetworks.bridge.models.organizations;

import static org.sagebionetworks.bridge.TestConstants.CREATED_ON;
import static org.sagebionetworks.bridge.TestConstants.MODIFIED_ON;
import static org.sagebionetworks.bridge.TestConstants.TEST_APP_ID;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertNull;

import com.fasterxml.jackson.databind.JsonNode;

import org.testng.annotations.Test;

import org.sagebionetworks.bridge.json.BridgeObjectMapper;

public class OrganizationTest {
    
    @Test
    public void canSerialize() throws Exception { 
        Organization org = Organization.create();
        org.setAppId(TEST_APP_ID);
        org.setIdentifier("anIdentifier");
        org.setName("aName");
        org.setDescription("aDescription");
        org.setCreatedOn(CREATED_ON);
        org.setModifiedOn(MODIFIED_ON);
        org.setSynapseProjectId("aProjectId");
        org.setSynapseDataAccessTeamId("anAccessTeamId");
        org.setVersion(3L);
        
        // not serialized, but it is there
        assertEquals(org.getAppId(), TEST_APP_ID);
        
        JsonNode node = BridgeObjectMapper.get().valueToTree(org);
        assertEquals(node.get("identifier").textValue(), "anIdentifier");
        assertEquals(node.get("name").textValue(), "aName");
        assertEquals(node.get("description").textValue(), "aDescription");
        assertEquals(node.get("synapseDataAccessTeamId").textValue(), "anAccessTeamId");
        assertEquals(node.get("synapseProjectId").textValue(), "aProjectId");
        assertEquals(node.get("createdOn").textValue(), CREATED_ON.toString());
        assertEquals(node.get("modifiedOn").textValue(), MODIFIED_ON.toString());
        assertEquals(node.get("version").longValue(), 3L);
        assertEquals(node.get("type").textValue(), "Organization");
        
        Organization deser = BridgeObjectMapper.get().readValue(node.toString(), Organization.class);
        assertNull(deser.getAppId());
        assertEquals(deser.getIdentifier(), "anIdentifier");
        assertEquals(deser.getName(), "aName");
        assertEquals(deser.getDescription(), "aDescription");
        assertEquals(deser.getSynapseDataAccessTeamId(), "anAccessTeamId");
        assertEquals(deser.getSynapseProjectId(), "aProjectId");
        assertEquals(deser.getCreatedOn(), CREATED_ON);
        assertEquals(deser.getModifiedOn(), MODIFIED_ON);
        assertEquals(deser.getVersion(), Long.valueOf(3L));
    }

    @Test
    public void testConstructor() { 
        HibernateOrganization org = new HibernateOrganization("name", "identifier", "description");
        assertEquals(org.getName(), "name");
        assertEquals(org.getIdentifier(), "identifier");
        assertEquals(org.getDescription(), "description");
    }
}
