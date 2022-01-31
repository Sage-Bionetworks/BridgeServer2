package org.sagebionetworks.bridge.models.schedules2.timelines;

import static org.sagebionetworks.bridge.TestConstants.ASSESSMENT_1_GUID;
import static org.sagebionetworks.bridge.TestConstants.MODIFIED_ON;
import static org.sagebionetworks.bridge.TestConstants.SESSION_GUID_1;
import static org.sagebionetworks.bridge.TestConstants.SESSION_WINDOW_GUID_1;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertNull;
import static org.testng.Assert.assertTrue;

import java.util.Map;

import org.mockito.Mockito;
import org.testng.annotations.Test;

public class TimelineMetadataTest extends Mockito {

    @Test
    public void test() {
        TimelineMetadata meta = createTimelineMetadata();
        assertEquals(meta.getGuid(), SESSION_GUID_1);
        assertEquals(meta.getAssessmentInstanceGuid(), "assessmentInstanceGuid");
        assertEquals(meta.getAssessmentGuid(), ASSESSMENT_1_GUID);
        assertEquals(meta.getAssessmentId(), "assessmentId");
        assertEquals(meta.getAssessmentRevision(), Integer.valueOf(7));
        assertEquals(meta.getSessionInstanceGuid(), "sessionInstanceGuid");
        assertEquals(meta.getSessionGuid(), "sessionGuid");
        assertEquals(meta.getSessionStartEventId(), "enrollment");
        assertEquals(meta.getSessionInstanceStartDay(), Integer.valueOf(5));
        assertEquals(meta.getSessionInstanceEndDay(), Integer.valueOf(15));
        assertEquals(meta.getSessionSymbol(), "●");
        assertEquals(meta.getSessionName(), "Session #1");
        assertEquals(meta.getTimeWindowGuid(), SESSION_WINDOW_GUID_1);
        assertEquals(meta.getScheduleGuid(), "scheduleGuid");
        assertEquals(meta.getScheduleModifiedOn(), MODIFIED_ON);
        assertTrue(meta.isSchedulePublished());
        assertTrue(meta.isTimeWindowPersistent());
        assertEquals(meta.getStudyBurstId(), "studyBurstId");
        assertEquals(meta.getStudyBurstNum(), Integer.valueOf(4));
        assertEquals(meta.getAppId(), "appId");
    }

    @Test
    public void copy() {
        TimelineMetadata meta = createTimelineMetadata();
        TimelineMetadata copy = TimelineMetadata.copy(meta);
        assertEquals(copy.getGuid(), SESSION_GUID_1);
        assertEquals(copy.getAssessmentInstanceGuid(), "assessmentInstanceGuid");
        assertEquals(copy.getAssessmentGuid(), ASSESSMENT_1_GUID);
        assertEquals(copy.getAssessmentId(), "assessmentId");
        assertEquals(copy.getAssessmentRevision(), Integer.valueOf(7));
        assertEquals(copy.getSessionInstanceGuid(), "sessionInstanceGuid");
        assertEquals(copy.getSessionGuid(), "sessionGuid");
        assertEquals(copy.getSessionStartEventId(), "enrollment");
        assertEquals(copy.getSessionInstanceStartDay(), Integer.valueOf(5));
        assertEquals(copy.getSessionInstanceEndDay(), Integer.valueOf(15));
        assertEquals(copy.getSessionSymbol(), "●");
        assertEquals(copy.getSessionName(), "Session #1");
        assertEquals(copy.getTimeWindowGuid(), SESSION_WINDOW_GUID_1);
        assertEquals(copy.getScheduleGuid(), "scheduleGuid");
        assertEquals(copy.getScheduleModifiedOn(), MODIFIED_ON);
        assertTrue(copy.isSchedulePublished());
        assertTrue(copy.isTimeWindowPersistent());
        assertEquals(copy.getStudyBurstId(), "studyBurstId");
        assertEquals(copy.getStudyBurstNum(), Integer.valueOf(4));
        assertEquals(copy.getAppId(), "appId");
    }
    
    @Test
    public void asMap() {
        TimelineMetadata meta = createTimelineMetadata();
        
        Map<String,String> map = meta.asMap();
        assertEquals(map.size(), 18);
        assertEquals(map.get("appId"), "appId");
        assertEquals(map.get("guid"), SESSION_GUID_1);
        assertEquals(map.get("assessmentInstanceGuid"), "assessmentInstanceGuid");
        assertEquals(map.get("assessmentGuid"), ASSESSMENT_1_GUID);
        assertEquals(map.get("assessmentId"), "assessmentId");
        assertEquals(map.get("assessmentRevision"), "7");
        assertEquals(map.get("sessionInstanceGuid"), "sessionInstanceGuid");
        assertEquals(map.get("sessionGuid"), "sessionGuid");
        assertEquals(map.get("sessionStartEventId"), "enrollment");
        assertEquals(map.get("sessionInstanceStartDay"), "5");
        assertEquals(map.get("sessionInstanceEndDay"), "15");
        assertEquals(map.get("timeWindowGuid"), SESSION_WINDOW_GUID_1);
        assertEquals(map.get("timeWindowPersistent"), "true");
        assertEquals(map.get("scheduleGuid"), "scheduleGuid");
        assertEquals(map.get("scheduleModifiedOn"), MODIFIED_ON.toString());
        assertEquals(map.get("schedulePublished"), "true");
        assertEquals(map.get("studyBurstId"), "studyBurstId");
        assertEquals(map.get("studyBurstNum"), "4");
    }
    
    @Test
    public void asMap_nullValues() {
        TimelineMetadata meta = new TimelineMetadata();
        
        Map<String,String> map = meta.asMap();
        assertNull(map.get("appId"));
        assertNull(map.get("guid"));
        assertNull(map.get("assessmentInstanceGuid"));
        assertNull(map.get("assessmentGuid"));
        assertNull(map.get("assessmentId"));
        assertNull(map.get("assessmentRevision"));
        assertNull(map.get("sessionInstanceGuid"));
        assertNull(map.get("sessionGuid"));
        assertNull(map.get("sessionStartEventId"));
        assertNull(map.get("sessionInstanceStartDay"));
        assertNull(map.get("sessionInstanceEndDay"));
        assertNull(map.get("sessionLabel"));
        assertNull(map.get("timeWindowGuid"));
        assertEquals(map.get("timeWindowPersistent"), "false");
        assertNull(map.get("scheduleGuid"));
        assertNull(map.get("scheduleModifiedOn"));
        assertEquals(map.get("schedulePublished"), "false");
        assertNull(map.get("studyBurstId"));
        assertNull(map.get("studyBurstNum"));
    }

    public static TimelineMetadata createTimelineMetadata() {
        TimelineMetadata meta = new TimelineMetadata();
        meta.setGuid(SESSION_GUID_1);
        meta.setAssessmentInstanceGuid("assessmentInstanceGuid");
        meta.setAssessmentGuid(ASSESSMENT_1_GUID);
        meta.setAssessmentId("assessmentId");
        meta.setAssessmentRevision(7);
        meta.setSessionInstanceGuid("sessionInstanceGuid");
        meta.setSessionGuid("sessionGuid");
        meta.setSessionStartEventId("enrollment");
        meta.setSessionInstanceStartDay(5);
        meta.setSessionInstanceEndDay(15);
        meta.setSessionSymbol("●");
        meta.setSessionName("Session #1");        
        meta.setTimeWindowGuid(SESSION_WINDOW_GUID_1);
        meta.setTimeWindowPersistent(true);
        meta.setScheduleGuid("scheduleGuid");
        meta.setScheduleModifiedOn(MODIFIED_ON);
        meta.setSchedulePublished(true);
        meta.setAppId("appId");
        meta.setStudyBurstId("studyBurstId");
        meta.setStudyBurstNum(Integer.valueOf(4));
        return meta;
    }
}
