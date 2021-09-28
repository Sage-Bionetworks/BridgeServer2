package org.sagebionetworks.bridge.models.activities;

import static org.sagebionetworks.bridge.TestConstants.CREATED_ON;
import static org.sagebionetworks.bridge.TestConstants.MODIFIED_ON;
import static org.sagebionetworks.bridge.TestConstants.TEST_APP_ID;
import static org.sagebionetworks.bridge.TestConstants.TEST_STUDY_ID;
import static org.sagebionetworks.bridge.TestConstants.TEST_USER_ID;
import static org.sagebionetworks.bridge.models.activities.ActivityEventObjectType.QUESTION;
import static org.sagebionetworks.bridge.models.activities.ActivityEventType.ANSWERED;
import static org.sagebionetworks.bridge.models.activities.ActivityEventUpdateType.FUTURE_ONLY;
import static org.sagebionetworks.bridge.models.activities.ActivityEventUpdateType.MUTABLE;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertNull;

import org.testng.annotations.Test;

public class StudyActivityEventParamsTest {
    
    @Test
    public void createsValidEvent() {
        StudyActivityEventParams params = new StudyActivityEventParams()
                .withAppId(TEST_APP_ID)
                .withUserId(TEST_USER_ID)
                .withStudyId(TEST_STUDY_ID)
                .withClientTimeZone("America/Los_Angeles")
                .withObjectType(QUESTION)
                .withObjectId("foo")
                .withAnswerValue("anAnswer")
                .withTimestamp(MODIFIED_ON)
                .withCreatedOn(CREATED_ON)
                .withUpdateType(MUTABLE)
                .withEventType(ANSWERED);
        
        StudyActivityEvent event = params.toStudyActivityEvent();
        assertEquals(event.getAppId(), TEST_APP_ID);
        assertEquals(event.getUserId(), TEST_USER_ID);
        assertEquals(event.getStudyId(), TEST_STUDY_ID);
        assertEquals(event.getEventId(), "question:foo:answered=anAnswer");
        assertEquals(event.getTimestamp(), MODIFIED_ON);
        assertEquals(event.getAnswerValue(), "anAnswer");
        assertEquals(event.getClientTimeZone(), "America/Los_Angeles");
        assertEquals(event.getCreatedOn(), CREATED_ON);
        assertEquals(event.getRecordCount(), 0);
        assertEquals(event.getUpdateType(), MUTABLE);
    }
    
    @Test
    public void nullSafe() {
        StudyActivityEvent event = new StudyActivityEventParams().toStudyActivityEvent();
        assertNull(event.getEventId());
    }
    
    @Test
    public void objectTypeIsNull() {
        StudyActivityEvent event = new StudyActivityEventParams()
                .withAppId(TEST_APP_ID)
                .withUserId(TEST_USER_ID)
                .withStudyId(TEST_STUDY_ID)
                .withClientTimeZone("America/Los_Angeles")
                .withObjectId("foo")
                .withAnswerValue("anAnswer")
                .withTimestamp(MODIFIED_ON)
                .withCreatedOn(CREATED_ON)
                .withEventType(ANSWERED).toStudyActivityEvent();
        assertNull(event.getEventId());
    }
    
    @Test
    public void updateTypeIsNull() {
        StudyActivityEvent event = new StudyActivityEventParams()
                .withAppId(TEST_APP_ID)
                .withUserId(TEST_USER_ID)
                .withStudyId(TEST_STUDY_ID)
                .withClientTimeZone("America/Los_Angeles")
                .withObjectType(QUESTION)
                .withObjectId("foo")
                .withAnswerValue("anAnswer")
                .withTimestamp(MODIFIED_ON)
                .withCreatedOn(CREATED_ON)
                .withEventType(ANSWERED).toStudyActivityEvent();
        assertEquals(event.getUpdateType(), FUTURE_ONLY);
    }
}
