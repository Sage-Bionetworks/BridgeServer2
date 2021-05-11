package org.sagebionetworks.bridge.models.activities;

import static org.sagebionetworks.bridge.models.activities.ActivityEventObjectType.ACTIVITIES_RETRIEVED;
import static org.sagebionetworks.bridge.models.activities.ActivityEventObjectType.ACTIVITY;
import static org.sagebionetworks.bridge.models.activities.ActivityEventObjectType.ASSESSMENT;
import static org.sagebionetworks.bridge.models.activities.ActivityEventObjectType.CREATED_ON;
import static org.sagebionetworks.bridge.models.activities.ActivityEventObjectType.CUSTOM;
import static org.sagebionetworks.bridge.models.activities.ActivityEventObjectType.ENROLLMENT;
import static org.sagebionetworks.bridge.models.activities.ActivityEventObjectType.QUESTION;
import static org.sagebionetworks.bridge.models.activities.ActivityEventObjectType.SESSION;
import static org.sagebionetworks.bridge.models.activities.ActivityEventObjectType.STUDY_START_DATE;
import static org.sagebionetworks.bridge.models.activities.ActivityEventObjectType.SURVEY;
import static org.sagebionetworks.bridge.models.activities.ActivityEventType.ANSWERED;
import static org.sagebionetworks.bridge.models.activities.ActivityEventType.FINISHED;
import static org.sagebionetworks.bridge.models.activities.ActivityEventUpdateType.FUTURE_ONLY;
import static org.sagebionetworks.bridge.models.activities.ActivityEventUpdateType.IMMUTABLE;
import static org.testng.Assert.assertEquals;

import org.testng.annotations.Test;

public class ActivityEventObjectTypeTest {

    @Test
    public void activitiesRetrieved() {
        String retValue = ACTIVITIES_RETRIEVED.getEventId(null, null, null);
        assertEquals(retValue, "activities_retrieved");
        assertEquals(ACTIVITIES_RETRIEVED.getUpdateType(), IMMUTABLE);
    }
    
    @Test
    public void enrollment() {
        String retValue = ENROLLMENT.getEventId(null, null, null);
        assertEquals(retValue, "enrollment");
        assertEquals(ENROLLMENT.getUpdateType(), IMMUTABLE);
    }
    
    @Test
    public void question() {
        String retValue = QUESTION.getEventId("questionGuid", ANSWERED, "my answer");
        assertEquals(retValue, "question:questionGuid:answered=my answer");
        assertEquals(QUESTION.getUpdateType(), FUTURE_ONLY);
    }
    
    @Test
    public void survey() {
        String retValue = SURVEY.getEventId("surveyGuid", FINISHED, null);
        assertEquals(retValue, "survey:surveyGuid:finished");
        assertEquals(SURVEY.getUpdateType(), FUTURE_ONLY);
    }
    
    @Test
    public void activity() {
        String retValue = ACTIVITY.getEventId("actGuid", FINISHED, null);
        assertEquals(retValue, "activity:actGuid:finished");
        assertEquals(ACTIVITY.getUpdateType(), FUTURE_ONLY);
    }
    
    @Test
    public void session() {
        String retValue = SESSION.getEventId("sessionGuid", FINISHED, null);
        assertEquals(retValue, "session:sessionGuid:finished");
        assertEquals(SESSION.getUpdateType(), FUTURE_ONLY);
    }
    
    @Test
    public void assessment() {
        String retValue = ASSESSMENT.getEventId("asmtId", FINISHED, null);
        assertEquals(retValue, "assessment:asmtId:finished");
        assertEquals(ASSESSMENT.getUpdateType(), FUTURE_ONLY);
    }
    
    @Test
    public void custom() {
        String retValue = CUSTOM.getEventId("eventId", null, null);
        assertEquals(retValue, "custom:eventId");
        assertEquals(CUSTOM.getUpdateType(), IMMUTABLE);
    }

    @Test
    public void createdOn() {
        String retValue = CREATED_ON.getEventId(null, null, null);
        assertEquals(retValue, "created_on");
        assertEquals(CREATED_ON.getUpdateType(), IMMUTABLE);
    }
    
    @Test
    public void studyStartDate() {
        String retValue = STUDY_START_DATE.getEventId(null, null, null);
        assertEquals(retValue, "study_start_date");
        assertEquals(STUDY_START_DATE.getUpdateType(), IMMUTABLE);
    }
}
