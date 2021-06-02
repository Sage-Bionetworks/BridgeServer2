package org.sagebionetworks.bridge.models.schedules2.adherence;

import static org.sagebionetworks.bridge.TestConstants.CREATED_ON;
import static org.sagebionetworks.bridge.TestConstants.GUID;
import static org.sagebionetworks.bridge.TestConstants.TEST_STUDY_ID;
import static org.sagebionetworks.bridge.TestConstants.TEST_USER_ID;
import static org.testng.Assert.assertEquals;

import org.testng.annotations.Test;

import nl.jqno.equalsverifier.EqualsVerifier;
import nl.jqno.equalsverifier.Warning;

public class AdherenceRecordIdTest {
    
    @Test
    public void hashCodeEquals() {
        EqualsVerifier.forClass(AdherenceRecordId.class)
            .suppress(Warning.NONFINAL_FIELDS)
            .allFieldsShouldBeUsed().verify();
    }
    
    @Test
    public void test() {
        AdherenceRecordId id = new AdherenceRecordId(
                TEST_USER_ID, TEST_STUDY_ID, GUID, CREATED_ON);
     
        assertEquals(id.getUserId(), TEST_USER_ID);
        assertEquals(id.getStudyId(), TEST_STUDY_ID);
        assertEquals(id.getInstanceGuid(), GUID);
        assertEquals(id.getEventTimestamp(), CREATED_ON);
    }

}
