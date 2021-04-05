package org.sagebionetworks.bridge.models.schedules2.timelines;

import static org.sagebionetworks.bridge.TestConstants.MODIFIED_ON;
import static org.sagebionetworks.bridge.TestConstants.USER_STUDY_IDS;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertTrue;

import org.mockito.Mockito;
import org.testng.annotations.Test;

public class TimelineMetadataTest extends Mockito {
    
    @Test
    public void test() {
        TimelineMetadata meta = createTimelineMetadata();
        assertEquals(meta.getGuid(), "guid");
        assertEquals(meta.getAssessmentInstanceGuid(), "assessmentInstanceGuid");
        assertEquals(meta.getAssessmentGuid(), "assessmentGuid");
        assertEquals(meta.getAssessmentId(), "assessmentId");
        assertEquals(meta.getAssessmentRevision(), Integer.valueOf(7));
        assertEquals(meta.getSessionInstanceGuid(), "sessionInstanceGuid");
        assertEquals(meta.getSessionGuid(), "sessionGuid");
        assertEquals(meta.getScheduleGuid(), "scheduleGuid");
        assertEquals(meta.getScheduleModifiedOn(), MODIFIED_ON);
        assertTrue(meta.isSchedulePublished());
        assertEquals(meta.getAppId(), "appId");
        assertEquals(meta.getStudyIds(), USER_STUDY_IDS);
    }

    @Test
    public void copy() {
        TimelineMetadata meta = createTimelineMetadata();
        TimelineMetadata copy = TimelineMetadata.copy(meta);
        assertEquals(copy.getGuid(), "guid");
        assertEquals(copy.getAssessmentInstanceGuid(), "assessmentInstanceGuid");
        assertEquals(copy.getAssessmentGuid(), "assessmentGuid");
        assertEquals(copy.getAssessmentId(), "assessmentId");
        assertEquals(copy.getAssessmentRevision(), Integer.valueOf(7));
        assertEquals(copy.getSessionInstanceGuid(), "sessionInstanceGuid");
        assertEquals(copy.getSessionGuid(), "sessionGuid");
        assertEquals(copy.getScheduleGuid(), "scheduleGuid");
        assertEquals(copy.getScheduleModifiedOn(), MODIFIED_ON);
        assertTrue(copy.isSchedulePublished());
        assertEquals(copy.getAppId(), "appId");
        assertEquals(copy.getStudyIds(), USER_STUDY_IDS);
    }

    private TimelineMetadata createTimelineMetadata() {
        TimelineMetadata meta = new TimelineMetadata();
        meta.setGuid("guid");
        meta.setAssessmentInstanceGuid("assessmentInstanceGuid");
        meta.setAssessmentGuid("assessmentGuid");
        meta.setAssessmentId("assessmentId");
        meta.setAssessmentRevision(7);
        meta.setSessionInstanceGuid("sessionInstanceGuid");
        meta.setSessionGuid("sessionGuid");
        meta.setScheduleGuid("scheduleGuid");
        meta.setScheduleModifiedOn(MODIFIED_ON);
        meta.setSchedulePublished(true);
        meta.setAppId("appId");
        meta.setStudyIds(USER_STUDY_IDS);
        return meta;
    }
}
