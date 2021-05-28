package org.sagebionetworks.bridge.models.activities;

import static org.sagebionetworks.bridge.TestConstants.MODIFIED_ON;
import static org.sagebionetworks.bridge.TestConstants.TEST_STUDY_ID;
import static org.sagebionetworks.bridge.TestConstants.TEST_USER_ID;
import static org.testng.Assert.assertEquals;

import org.testng.annotations.Test;

import nl.jqno.equalsverifier.EqualsVerifier;

public class StudyActivityEventIdTest {
    
    @Test
    public void equalsHashCode() { 
        EqualsVerifier.forClass(StudyActivityEventId.class)
            .allFieldsShouldBeUsed();
    }
    
    @Test
    public void test() {
        StudyActivityEventId id = new StudyActivityEventId(TEST_USER_ID, TEST_STUDY_ID, 
                "timeline_retrieved", MODIFIED_ON);
        assertEquals(id.getUserId(), TEST_USER_ID);
        assertEquals(id.getStudyId(), TEST_STUDY_ID);
        assertEquals(id.getEventId(), "timeline_retrieved");
        assertEquals(id.getTimestamp(), MODIFIED_ON);
    }
}
