package org.sagebionetworks.bridge.models.substudies;

import static org.testng.Assert.assertEquals;

import org.testng.annotations.Test;

import nl.jqno.equalsverifier.EqualsVerifier;
import nl.jqno.equalsverifier.Warning;

public class AccountSubstudyIdTest {

    @Test
    public void hashCodeEquals() {
        EqualsVerifier.forClass(AccountSubstudyId.class).allFieldsShouldBeUsed().suppress(Warning.NONFINAL_FIELDS)
                .verify();
    }
    
    @Test
    public void create() { 
        AccountSubstudyId key = new AccountSubstudyId("studyId", "substudyId", "accountId");
        assertEquals(key.getStudyId(), "studyId");
        assertEquals(key.getSubstudyId(), "substudyId");
        assertEquals(key.getAccountId(), "accountId");
    }
    
}
