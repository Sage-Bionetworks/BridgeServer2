package org.sagebionetworks.bridge.models.schedules2.adherence.eventstream;

import static org.sagebionetworks.bridge.models.schedules2.adherence.SessionCompletionState.ABANDONED;
import static org.testng.Assert.assertEquals;

import org.joda.time.LocalDate;
import org.sagebionetworks.bridge.json.BridgeObjectMapper;
import org.testng.annotations.Test;

import com.fasterxml.jackson.databind.JsonNode;

public class EventStreamWindowTest {
    
    @Test
    public void canSerialize() throws Exception { 
        EventStreamWindow window = new EventStreamWindow();
        window.setSessionInstanceGuid("sessionInstanceGuid");
        window.setTimeWindowGuid("timeWindowGuid");
        window.setState(ABANDONED);
        window.setEndDay(3);
        window.setEndDate(LocalDate.parse("2021-10-15"));
        
        JsonNode node = BridgeObjectMapper.get().valueToTree(window);
        assertEquals(node.size(), 6);
        assertEquals(node.get("sessionInstanceGuid").textValue(), "sessionInstanceGuid");
        assertEquals(node.get("timeWindowGuid").textValue(), "timeWindowGuid");
        assertEquals(node.get("state").textValue(), "abandoned");
        assertEquals(node.get("endDay").intValue(), 3);
        assertEquals(node.get("endDate").textValue(), "2021-10-15");
        assertEquals(node.get("type").textValue(), "EventStreamWindow");
        
        EventStreamWindow deser = BridgeObjectMapper.get().readValue(node.toString(), EventStreamWindow.class);
        assertEquals(deser.getSessionInstanceGuid(), "sessionInstanceGuid");
        assertEquals(deser.getTimeWindowGuid(), "timeWindowGuid");
        assertEquals(deser.getState(), ABANDONED);
        assertEquals(deser.getEndDay(), Integer.valueOf(3));
        assertEquals(deser.getEndDate(), LocalDate.parse("2021-10-15"));
    }

}
