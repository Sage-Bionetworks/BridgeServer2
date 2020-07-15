package org.sagebionetworks.bridge.hibernate;

import org.joda.time.DateTime;
import org.joda.time.DateTimeZone;
import org.testng.annotations.Test;

import org.sagebionetworks.bridge.json.BridgeObjectMapper;
import org.sagebionetworks.bridge.models.studies.Study;

import static org.sagebionetworks.bridge.TestConstants.TEST_APP_ID;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertNull;
import static org.testng.Assert.assertTrue;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

public class HibernateStudyTest {

    private static final DateTime CREATED_ON = DateTime.now().withZone(DateTimeZone.UTC);
    private static final DateTime MODIFIED_ON = DateTime.now().minusHours(1).withZone(DateTimeZone.UTC);
    
    @Test
    public void canSerialize() throws Exception {
        Study study = Study.create();
        study.setAppId(TEST_APP_ID);
        study.setIdentifier("oneId");
        study.setName("name");
        study.setDeleted(true);
        study.setCreatedOn(CREATED_ON);
        study.setModifiedOn(MODIFIED_ON);
        study.setVersion(3L);
        
        JsonNode node = BridgeObjectMapper.get().valueToTree(study);
        assertEquals(node.size(), 7);
        assertEquals(node.get("identifier").textValue(), "oneId");
        assertEquals(node.get("name").textValue(), "name");
        assertTrue(node.get("deleted").booleanValue());
        assertEquals(node.get("createdOn").textValue(), CREATED_ON.toString());
        assertEquals(node.get("modifiedOn").textValue(), MODIFIED_ON.toString());
        assertEquals(node.get("version").longValue(), 3L);
        assertEquals(node.get("type").textValue(), "Study");
        assertNull(node.get("studyId"));
        assertNull(node.get("appId"));
        
        Study deser = BridgeObjectMapper.get().readValue(node.toString(), Study.class);
        assertEquals(deser.getIdentifier(), "oneId");
        assertEquals(deser.getName(), "name");
        assertTrue(deser.isDeleted());
        assertEquals(deser.getCreatedOn(), CREATED_ON);
        assertEquals(deser.getModifiedOn(), MODIFIED_ON);
        assertEquals(deser.getVersion(), new Long(3));
        
        ((ObjectNode)node).remove("id");
        ((ObjectNode)node).put("identifier", "oneId");
        deser = BridgeObjectMapper.get().readValue(node.toString(), Study.class);
        assertEquals(deser.getIdentifier(), "oneId");
    }
}
