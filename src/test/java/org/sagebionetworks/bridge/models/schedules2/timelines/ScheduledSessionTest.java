package org.sagebionetworks.bridge.models.schedules2.timelines;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertTrue;

import com.fasterxml.jackson.databind.JsonNode;

import org.joda.time.LocalTime;
import org.joda.time.Period;
import org.mockito.Mockito;
import org.testng.annotations.Test;

import org.sagebionetworks.bridge.json.BridgeObjectMapper;

public class ScheduledSessionTest extends Mockito {

    @Test
    public void canSerialize() throws Exception {
        ScheduledAssessment asmt = new ScheduledAssessment("ref", "instanceGuid");
        
        ScheduledSession schSession = new ScheduledSession.Builder()
                .withRefGuid("guid")
                .withInstanceGuid("instanceGuid")
                .withStartDay(10)
                .withEndDay(13)
                .withDelayTime(Period.parse("PT3H"))
                .withStartTime(LocalTime.parse("17:00"))
                .withExpiration(Period.parse("PT30M"))
                .withPersistent(true)
                .withScheduledAssessment(asmt)
                .build();
        
        JsonNode node = BridgeObjectMapper.get().valueToTree(schSession);
        assertEquals(node.get("refGuid").textValue(), "guid");
        assertEquals(node.get("instanceGuid").textValue(), "instanceGuid");
        assertEquals(node.get("startDay").intValue(), 10);
        assertEquals(node.get("endDay").intValue(), 13);
        assertEquals(node.get("startTime").textValue(), "17:00");
        assertEquals(node.get("delayTime").textValue(), "PT3H");
        assertEquals(node.get("expiration").textValue(), "PT30M");
        assertTrue(node.get("persistent").booleanValue());
        assertEquals(node.get("type").textValue(), "ScheduledSession");
        assertEquals(node.get("assessments").size(), 1);
    }
    
    @Test
    public void serializationHandlesNulls() {
        ScheduledSession schSession = new ScheduledSession.Builder().build();
        
        JsonNode node = BridgeObjectMapper.get().valueToTree(schSession);
        assertEquals(node.size(), 4);
        assertEquals(node.get("startDay").intValue(), 0);
        assertEquals(node.get("endDay").intValue(), 0);
        assertEquals(node.get("assessments").size(), 0);
        assertEquals(node.get("type").textValue(), "ScheduledSession");
    }
}
