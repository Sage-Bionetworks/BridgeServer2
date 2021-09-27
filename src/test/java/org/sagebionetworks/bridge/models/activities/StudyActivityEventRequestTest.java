package org.sagebionetworks.bridge.models.activities;

import static org.sagebionetworks.bridge.TestConstants.MODIFIED_ON;
import static org.testng.Assert.assertEquals;

import org.testng.annotations.Test;
import org.sagebionetworks.bridge.TestUtils;
import org.sagebionetworks.bridge.json.BridgeObjectMapper;

public class StudyActivityEventRequestTest {
    
    @Test
    public void testConstruction() {
        StudyActivityEventRequest request = createRequest();
        assertRequest(request);
    }

    @Test
    public void canDeserialize() throws Exception {
        // Try it with eventKey
        String json = TestUtils.createJson("{'eventKey':'event1',"+
                "'timestamp':'"+MODIFIED_ON.toString()+"',"+
                "'answerValue':'my answer',"+
                "'clientTimeZone':'America/Los_Angeles'}");
        
        StudyActivityEventRequest request = BridgeObjectMapper.get().readValue(json, StudyActivityEventRequest.class);
        assertEquals(request.getTimestamp(), MODIFIED_ON);
        assertEquals(request.getAnswerValue(), "my answer");
        assertEquals(request.getClientTimeZone(), "America/Los_Angeles");
        assertEquals(request.getEventKey(), "event1");
        
        // Try it with eventId
        json = TestUtils.createJson("{'eventId':'event1',"+
                "'timestamp':'"+MODIFIED_ON.toString()+"',"+
                "'answerValue':'my answer',"+
                "'clientTimeZone':'America/Los_Angeles'}");
        
        request = BridgeObjectMapper.get().readValue(json, StudyActivityEventRequest.class);
        assertEquals(request.getTimestamp(), MODIFIED_ON);
        assertEquals(request.getAnswerValue(), "my answer");
        assertEquals(request.getClientTimeZone(), "America/Los_Angeles");
        assertEquals(request.getEventKey(), "event1");
    }
    
    private StudyActivityEventRequest createRequest() {
        return new StudyActivityEventRequest("survey:surveyGuid:finished", MODIFIED_ON, "my answer", "America/Los_Angeles");
    }

    private void assertRequest(StudyActivityEventRequest request) {
        assertEquals(request.getTimestamp(), MODIFIED_ON);
        assertEquals(request.getAnswerValue(), "my answer");
        assertEquals(request.getClientTimeZone(), "America/Los_Angeles");
        assertEquals(request.getEventKey(), "survey:surveyGuid:finished");
    }
}
