package org.sagebionetworks.bridge.models.schedules2;

import static org.sagebionetworks.bridge.TestConstants.GUID;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertTrue;

import com.fasterxml.jackson.databind.JsonNode;

import org.joda.time.LocalTime;
import org.joda.time.Period;
import org.testng.annotations.Test;

import org.sagebionetworks.bridge.json.BridgeObjectMapper;

public class TimeWindowTest {
    
    @Test
    public void canSerialize() throws Exception {
        
        TimeWindow window = new TimeWindow();
        window.setGuid(GUID);
        window.setStartTime(LocalTime.parse("16:30"));
        window.setExpiration(Period.parse("PT3H"));
        window.setPersistent(true);
        
        JsonNode node = BridgeObjectMapper.get().valueToTree(window);
        assertEquals(node.get("guid").textValue(), GUID);
        assertEquals(node.get("startTime").textValue(), "16:30");
        assertEquals(node.get("expiration").textValue(), "PT3H");
        assertTrue(node.get("persistent").booleanValue());
        assertEquals(node.get("type").textValue(), "TimeWindow");

        TimeWindow deser = BridgeObjectMapper.get().readValue(node.toString(), TimeWindow.class);
        assertEquals(deser.getGuid(), GUID);
        assertEquals(deser.getStartTime(), LocalTime.parse("16:30"));
        assertEquals(deser.getExpiration(), Period.parse("PT3H"));
        assertTrue(deser.isPersistent());
    }
    
}
