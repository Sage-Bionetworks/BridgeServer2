package org.sagebionetworks.bridge.models.schedules2.adherence.weekly;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertNull;

import org.joda.time.LocalDate;
import org.sagebionetworks.bridge.json.BridgeObjectMapper;
import org.sagebionetworks.bridge.models.schedules2.adherence.eventstream.EventStreamDay;
import org.testng.annotations.Test;

import com.fasterxml.jackson.databind.JsonNode;

public class NextActivityTest {
    
    @Test
    public void canSerialize() {
        EventStreamDay day = new EventStreamDay();
        day.setLabel("label");
        day.setSessionGuid("sessionGuid");
        day.setSessionName("sessionName");
        day.setSessionSymbol("sessionSymbol");
        day.setStartDay(1);
        day.setStartDate(LocalDate.parse("2015-02-02"));
        day.setWeek(2);
        day.setStudyBurstId("burst");
        day.setStudyBurstNum(3);

        NextActivity next = NextActivity.create(day);
        
        JsonNode node = BridgeObjectMapper.get().valueToTree(next);
        assertEquals(node.size(), 9);
        assertEquals(node.get("label").textValue(), "label");
        assertEquals(node.get("sessionGuid").textValue(), "sessionGuid");
        assertEquals(node.get("sessionName").textValue(), "sessionName");
        assertEquals(node.get("sessionSymbol").textValue(), "sessionSymbol");
        assertEquals(node.get("weekInStudy").intValue(), 2);
        assertEquals(node.get("studyBurstId").textValue(), "burst");
        assertEquals(node.get("studyBurstNum").intValue(), 3);
        assertEquals(node.get("startDate").textValue(), "2015-02-02");
        assertEquals(node.get("type").textValue(), "NextActivity");
    }
    
    @Test
    public void nullSafe() {
        assertNull(NextActivity.create(null));
    }
}
