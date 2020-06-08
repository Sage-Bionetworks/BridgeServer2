package org.sagebionetworks.bridge.models.schedules;

import static org.sagebionetworks.bridge.TestUtils.mockConfigResolver;
import static org.sagebionetworks.bridge.config.Environment.UAT;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertTrue;

import org.joda.time.DateTime;
import org.testng.annotations.Test;

import org.sagebionetworks.bridge.models.GuidCreatedOnVersionHolder;
import org.sagebionetworks.bridge.models.GuidCreatedOnVersionHolderImpl;
import org.sagebionetworks.bridge.models.appconfig.ConfigResolver;

import nl.jqno.equalsverifier.EqualsVerifier;

public class SurveyReferenceTest {

    private static final String IDENTIFIER = "id";
    private static final String GUID = "abc";
    private static final DateTime CREATED_ON = DateTime.parse("2017-02-09T20:15:59.558Z");
    
    @Test
    public void equalsVerifier() {
        // The reference may or may not have been resolved to include identifiers, the GUID
        // should be globally unique for assessement references and is sufficient for 
        // equality. This makes detecting duplicates easier during validation.
        EqualsVerifier.forClass(SurveyReference.class).allFieldsShouldBeUsedExcept("resolver", "identifier").verify();
    }
    
    @Test
    public void test() {
        ConfigResolver resolver = mockConfigResolver(UAT, "ws");
        SurveyReference ref = new SurveyReference(resolver, IDENTIFIER, GUID, CREATED_ON);
        
        assertEquals(ref.getIdentifier(), IDENTIFIER);
        assertEquals(ref.getGuid(), GUID);
        assertEquals(ref.getCreatedOn(), CREATED_ON);
        assertEquals(ref.getHref(), "https://ws-uat.bridge.org/v3/surveys/abc/revisions/2017-02-09T20:15:59.558Z");
        
        GuidCreatedOnVersionHolder keys1 = new GuidCreatedOnVersionHolderImpl(GUID, CREATED_ON.getMillis());
        assertTrue(ref.equalsSurvey(keys1));
        
        GuidCreatedOnVersionHolder keys2 = new GuidCreatedOnVersionHolderImpl("def", CREATED_ON.getMillis());
        assertFalse(ref.equalsSurvey(keys2));
        
        // Doesn't match published links, only specific ones.
        ref = new SurveyReference(IDENTIFIER, GUID, null);
        assertFalse(ref.equalsSurvey(keys1));
        assertFalse(ref.equalsSurvey(keys2));
        
        // Null test (should return false)
        assertFalse(ref.equalsSurvey(null));
        
        // Same guid, different createdOn
        GuidCreatedOnVersionHolder keys3 = new GuidCreatedOnVersionHolderImpl(GUID, DateTime.now().getMillis());
        assertFalse(ref.equalsSurvey(keys3));
        
        // Different guid, different createdOn
        GuidCreatedOnVersionHolder keys4 = new GuidCreatedOnVersionHolderImpl("def", DateTime.now().getMillis());
        assertFalse(ref.equalsSurvey(keys4));
    }
    
}
