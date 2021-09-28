package org.sagebionetworks.bridge.models.activities;

import static java.util.stream.Collectors.toList;
import static org.sagebionetworks.bridge.TestConstants.MODIFIED_ON;
import static org.sagebionetworks.bridge.models.activities.ActivityEventUpdateType.MUTABLE;
import static org.testng.Assert.assertEquals;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;
import org.sagebionetworks.bridge.TestUtils;
import org.sagebionetworks.bridge.json.BridgeObjectMapper;
import org.sagebionetworks.bridge.models.schedules2.StudyBurst;
import org.sagebionetworks.bridge.models.studies.StudyCustomEvent;

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
    
    @DataProvider(name = "parseRequestParams")
    public static Object[][] parseRequestParams() {
        return new Object[][] {
            {"", null, m(), m()},
            {"activities_retrieved", "activities_retrieved", m("foo"), m()},
            {"ACTIVITIES_RETRIEVED", "activities_retrieved", m("foo"), m()},
            {"custom:foo", "custom:foo", m("foo"), m()},
            {"custom:foo", null, m(), m("foo")},
            {"custom:foo", null, m(), m()},
            {"custom:TIMELINE_RETRIEVED", null, m("timeline_retrieved"), m()},
            {"custom:timeline_retrieved", null, m(), m()},
            {"foo", "custom:foo", m("foo"), m()},
            {"FOO", "custom:FOO", m("FOO"), m()},
            {"foo", null, m(), m()},
            {"question:foo:answer portion wrong", null, m(), m()},
            {"question:foo:answer=4", null, m(), m()}, // also wrong
            {"question:foo:answered=4", "question:foo:answered=4", m(), m()},
            {"QUESTION:foo:ANSWERED=4", "question:foo:answered=4", m(), m()},
            {"session:_yfDuP0ZgHx8Kx6_oYRlv3-z:finished", "session:_yfDuP0ZgHx8Kx6_oYRlv3-z:finished", m("foo"), m()},
            {"study_burst:bar:01", null, m(), m("foo")},
            {"study_burst:foo:01", "study_burst:foo:01", m(), m("foo")},
            {"study_burst:foo:01", null, m("foo"), m()},
            {"study_burst:foo:01", null, m(), m()},
            {"timeline_retrieved", "timeline_retrieved", m("timeline_retrieved"), m()},
            {null, null, m(), m()},
        };
    }
    
    private static Map<String, ActivityEventUpdateType> m(String... values) {
        Map<String, ActivityEventUpdateType> map = new HashMap<>();
        for (String eventId : values) {
            map.put(eventId, MUTABLE);
        }
        return map;
    }
    
    @Test(dataProvider = "parseRequestParams")
    public void parseRequest(String input, String expectedOutput, 
            Map<String, ActivityEventUpdateType> customEvents, 
            Map<String, ActivityEventUpdateType> studyBursts) {
        
        StudyActivityEventMap eventMap = new StudyActivityEventMap();
        
        List<StudyCustomEvent> events = customEvents.entrySet().stream()
                .map(entry -> new StudyCustomEvent(entry.getKey(), entry.getValue()))
                .collect(toList());
        eventMap.addCustomEvents(events);
        
        List<StudyBurst> bursts = studyBursts.entrySet().stream()
                .map(entry -> new StudyBurst(entry.getKey(), entry.getValue()))
                .collect(toList());
        eventMap.addStudyBursts(bursts);

        StudyActivityEventRequest request = new StudyActivityEventRequest(input, null, null, null); 
        
        String retValue = request.parse(eventMap).build().getEventId();
        assertEquals(retValue, expectedOutput);
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
