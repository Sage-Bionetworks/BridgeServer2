package org.sagebionetworks.bridge.models.surveys;

import static nl.jqno.equalsverifier.Warning.NONFINAL_FIELDS;
import static org.sagebionetworks.bridge.TestConstants.TIMESTAMP;
import static org.testng.Assert.assertEquals;

import org.sagebionetworks.bridge.models.GuidCreatedOnVersionHolder;
import org.sagebionetworks.bridge.models.GuidCreatedOnVersionHolderImpl;
import org.testng.annotations.Test;

import nl.jqno.equalsverifier.EqualsVerifier;

public class SurveyIdTest {
    @Test
    public void equalsHashCode() {
        EqualsVerifier.forClass(SurveyId.class).allFieldsShouldBeUsed()
            .suppress(NONFINAL_FIELDS).verify();
    }
    
    @Test
    public void test() {
        GuidCreatedOnVersionHolder keys = new GuidCreatedOnVersionHolderImpl("surveyGuid", TIMESTAMP.getMillis());
        SurveyId surveyId = new SurveyId(keys);
        
        assertEquals(surveyId.getSurveyGuid(), "surveyGuid");
        assertEquals(surveyId.getCreatedOn(), TIMESTAMP.getMillis());
    }    
}
