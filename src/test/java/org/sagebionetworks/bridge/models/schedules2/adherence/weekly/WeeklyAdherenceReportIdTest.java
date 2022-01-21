package org.sagebionetworks.bridge.models.schedules2.adherence.weekly;

import static org.sagebionetworks.bridge.TestConstants.TEST_APP_ID;
import static org.sagebionetworks.bridge.TestConstants.TEST_STUDY_ID;
import static org.sagebionetworks.bridge.TestConstants.TEST_USER_ID;
import static org.testng.Assert.assertEquals;

import org.testng.annotations.Test;

import nl.jqno.equalsverifier.EqualsVerifier;

public class WeeklyAdherenceReportIdTest {
    @Test
    public void hashCodeEquals() {
        EqualsVerifier.forClass(WeeklyAdherenceReportId.class).verify();
    }
    
    @Test
    public void test() {
        WeeklyAdherenceReportId id = new WeeklyAdherenceReportId(TEST_APP_ID, TEST_STUDY_ID, TEST_USER_ID);
        assertEquals(id.getAppId(), TEST_APP_ID);
        assertEquals(id.getStudyId(), TEST_STUDY_ID);
        assertEquals(id.getUserId(), TEST_USER_ID);
    }
}
