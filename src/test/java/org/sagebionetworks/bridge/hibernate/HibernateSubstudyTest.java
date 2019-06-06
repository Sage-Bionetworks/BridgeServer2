package org.sagebionetworks.bridge.hibernate;

import org.joda.time.DateTime;
import org.joda.time.DateTimeZone;
import org.testng.annotations.Test;

import org.sagebionetworks.bridge.TestConstants;
import org.sagebionetworks.bridge.json.BridgeObjectMapper;
import org.sagebionetworks.bridge.models.substudies.Substudy;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertNull;
import static org.testng.Assert.assertTrue;

import com.fasterxml.jackson.databind.JsonNode;

public class HibernateSubstudyTest {

    private static final DateTime CREATED_ON = DateTime.now().withZone(DateTimeZone.UTC);
    private static final DateTime MODIFIED_ON = DateTime.now().minusHours(1).withZone(DateTimeZone.UTC);
    
    @Test
    public void canSerialize() throws Exception {
        Substudy substudy = Substudy.create();
        substudy.setId("oneId");
        substudy.setStudyId(TestConstants.TEST_STUDY_IDENTIFIER);
        substudy.setName("name");
        substudy.setDeleted(true);
        substudy.setCreatedOn(CREATED_ON);
        substudy.setModifiedOn(MODIFIED_ON);
        substudy.setVersion(3L);
        
        JsonNode node = BridgeObjectMapper.get().valueToTree(substudy);
        assertEquals(node.size(), 7);
        assertEquals(node.get("id").textValue(), "oneId");
        assertEquals(node.get("name").textValue(), "name");
        assertTrue(node.get("deleted").booleanValue());
        assertEquals(node.get("createdOn").textValue(), CREATED_ON.toString());
        assertEquals(node.get("modifiedOn").textValue(), MODIFIED_ON.toString());
        assertEquals(node.get("version").longValue(), 3L);
        assertEquals(node.get("type").textValue(), "Substudy");
        assertNull(node.get("studyId"));
        
        Substudy deser = BridgeObjectMapper.get().readValue(node.toString(), Substudy.class);
        assertEquals(deser.getId(), "oneId");
        assertEquals(deser.getName(), "name");
        assertTrue(deser.isDeleted());
        assertEquals(deser.getCreatedOn(), CREATED_ON);
        assertEquals(deser.getModifiedOn(), MODIFIED_ON);
        assertEquals(deser.getVersion(), new Long(3));
    }
}
