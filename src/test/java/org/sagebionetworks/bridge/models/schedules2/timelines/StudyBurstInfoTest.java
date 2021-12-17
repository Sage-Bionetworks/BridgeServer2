package org.sagebionetworks.bridge.models.schedules2.timelines;

import static org.sagebionetworks.bridge.models.activities.ActivityEventUpdateType.FUTURE_ONLY;
import static org.testng.Assert.assertEquals;

import org.joda.time.Period;
import org.mockito.Mockito;
import org.sagebionetworks.bridge.json.BridgeObjectMapper;
import org.sagebionetworks.bridge.models.schedules2.StudyBurst;
import org.testng.annotations.Test;

import com.fasterxml.jackson.databind.JsonNode;

public class StudyBurstInfoTest extends Mockito {
    
    @Test
    public void canSerialize() throws Exception { 
        StudyBurst burst = new StudyBurst();
        burst.setIdentifier("foo");
        burst.setDelay(Period.parse("P1W"));
        burst.setInterval(Period.parse("P23D"));
        burst.setOccurrences(3);
        burst.setOriginEventId("custom:event1");
        burst.setUpdateType(FUTURE_ONLY);
        
        StudyBurstInfo info = StudyBurstInfo.create(burst);
        
        JsonNode node = BridgeObjectMapper.get().valueToTree(info);
        assertEquals(node.size(), 6);
        assertEquals(node.get("identifier").textValue(), "foo");
        assertEquals(node.get("originEventId").textValue(), "custom:event1");
        assertEquals(node.get("delay").textValue(), "P1W");
        assertEquals(node.get("interval").textValue(), "P23D");
        assertEquals(node.get("occurrences").intValue(), 3);
        assertEquals(node.get("type").textValue(), "StudyBurstInfo");
        
        // we never deserialize this, or persist it. It's just in the timeline.
    }

}
