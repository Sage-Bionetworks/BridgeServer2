package org.sagebionetworks.bridge.models.schedules2;

import static org.sagebionetworks.bridge.TestConstants.CREATED_ON;
import static org.sagebionetworks.bridge.TestConstants.MODIFIED_ON;
import static org.sagebionetworks.bridge.TestConstants.SCHEDULE_GUID;
import static org.sagebionetworks.bridge.TestConstants.TEST_APP_ID;
import static org.sagebionetworks.bridge.TestConstants.TEST_ORG_ID;
import static org.sagebionetworks.bridge.TestUtils.getClientData;
import static org.sagebionetworks.bridge.models.activities.ActivityEventObjectType.ENROLLMENT;
import static org.sagebionetworks.bridge.models.activities.ActivityEventObjectType.TIMELINE_RETRIEVED;
import static org.sagebionetworks.bridge.models.activities.ActivityEventUpdateType.FUTURE_ONLY;
import static org.sagebionetworks.bridge.models.activities.ActivityEventUpdateType.MUTABLE;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertNull;
import static org.testng.Assert.assertTrue;

import java.util.Map;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.google.common.collect.ImmutableList;

import org.joda.time.Period;
import org.testng.annotations.Test;

import org.sagebionetworks.bridge.json.BridgeObjectMapper;
import org.sagebionetworks.bridge.models.activities.ActivityEventUpdateType;

public class Schedule2Test {
    
    public static final Schedule2 createValidSchedule() {
        Schedule2 schedule = new Schedule2();
        schedule.setAppId(TEST_APP_ID);
        schedule.setOwnerId(TEST_ORG_ID);
        schedule.setName("Schedule name");
        schedule.setGuid(SCHEDULE_GUID);
        schedule.setDuration(Period.parse("P8W"));
        schedule.setClientData(getClientData());
        schedule.setCreatedOn(CREATED_ON);
        schedule.setModifiedOn(MODIFIED_ON);
        schedule.setDeleted(true);
        schedule.setPublished(true);
        schedule.setVersion(10L);
        
        StudyBurst burst = new StudyBurst();
        burst.setIdentifier("burst1");
        burst.setOriginEventId(TIMELINE_RETRIEVED.name().toLowerCase());
        burst.setInterval(Period.parse("P1W"));
        burst.setOccurrences(2);
        burst.setUpdateType(MUTABLE);
        
        schedule.setSessions(ImmutableList.of(SessionTest.createValidSession()));
        schedule.setStudyBursts(ImmutableList.of(burst));
        return schedule;
    }
    
    @Test
    public void canSerialize() throws Exception {
        Schedule2 schedule = createValidSchedule();
        
        Session session = new Session();
        session.setGuid("sessionGuid");
        schedule.setSessions(ImmutableList.of(session));
        
        JsonNode node = BridgeObjectMapper.get().valueToTree(schedule);
        assertEquals(node.size(), 13);
        assertNull(node.get("appId"));
        assertEquals(node.get("ownerId").textValue(), TEST_ORG_ID);
        assertEquals(node.get("name").textValue(), "Schedule name");
        assertEquals(node.get("guid").textValue(), SCHEDULE_GUID);
        assertEquals(node.get("duration").textValue(), "P8W");
        assertEquals(node.get("createdOn").textValue(), CREATED_ON.toString());
        assertEquals(node.get("modifiedOn").textValue(), MODIFIED_ON.toString());
        assertTrue(node.get("deleted").booleanValue());
        assertTrue(node.get("published").booleanValue());
        assertEquals(node.get("version").longValue(), 10L);
        assertEquals(node.get("type").textValue(), "Schedule");
        
        ArrayNode arrayNode = (ArrayNode)node.get("sessions");
        assertEquals(arrayNode.get(0).get("guid").textValue(), "sessionGuid");
        
        arrayNode = (ArrayNode)node.get("studyBursts");
        assertEquals(arrayNode.get(0).size(), 6);
        assertEquals(arrayNode.get(0).get("identifier").textValue(), "burst1");
        assertEquals(arrayNode.get(0).get("updateType").textValue(), "mutable");
        
        Schedule2 deser = BridgeObjectMapper.get().readValue(node.toString(), Schedule2.class);
        assertNull(deser.getAppId());
        assertEquals(deser.getOwnerId(), TEST_ORG_ID);
        assertEquals(deser.getName(), "Schedule name");
        assertEquals(deser.getGuid(),SCHEDULE_GUID);
        assertEquals(deser.getDuration(), Period.parse("P8W"));
        assertEquals(deser.getClientData().toString(), getClientData().toString());
        assertEquals(deser.getCreatedOn(), CREATED_ON);
        assertEquals(deser.getModifiedOn(), MODIFIED_ON);
        assertTrue(deser.isDeleted());
        assertTrue(deser.isPublished());
        assertEquals(deser.getVersion(), 10L);
    }
    
    @Test
    public void getStudyBurstsUpdateMap() {
        Schedule2 schedule = createValidSchedule();
        
        StudyBurst burst = new StudyBurst();
        burst.setIdentifier("burst2");
        burst.setOriginEventId(ENROLLMENT.name().toLowerCase());
        burst.setInterval(Period.parse("P1W"));
        burst.setOccurrences(2);
        burst.setUpdateType(FUTURE_ONLY);
        
        schedule.setStudyBursts(ImmutableList.of(schedule.getStudyBursts().get(0), burst));
        
        Map<String, ActivityEventUpdateType> map = schedule.getStudyBurstsUpdateMap();
        assertEquals(map.size(), 2);
        assertEquals(map.get("burst1"), MUTABLE);
        assertEquals(map.get("burst2"), FUTURE_ONLY);
    }
}
