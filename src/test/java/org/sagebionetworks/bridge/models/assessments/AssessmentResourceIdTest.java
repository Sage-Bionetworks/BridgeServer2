package org.sagebionetworks.bridge.models.assessments;

import static org.testng.Assert.assertEquals;

import org.testng.annotations.Test;

import nl.jqno.equalsverifier.EqualsVerifier;
import nl.jqno.equalsverifier.Warning;

public class AssessmentResourceIdTest {

    @Test
    public void equalsHashCode() {
        EqualsVerifier.forClass(AssessmentResourceId.class).allFieldsShouldBeUsed()
            .suppress(Warning.NONFINAL_FIELDS).verify();
    }
    
    @Test
    public void test() {
        AssessmentResourceId resId = new AssessmentResourceId("appId", "guid");
        
        assertEquals(resId.getAppId(), "appId");
        assertEquals(resId.getGuid(), "guid");
    }
}
