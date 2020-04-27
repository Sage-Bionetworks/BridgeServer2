package org.sagebionetworks.bridge.models.substudies;

import static org.sagebionetworks.bridge.TestConstants.TEST_APP_ID;
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
        AccountSubstudyId key = new AccountSubstudyId(TEST_APP_ID, "substudyId", "accountId");
        assertEquals(key.getAppId(), TEST_APP_ID);
        assertEquals(key.getSubstudyId(), "substudyId");
        assertEquals(key.getAccountId(), "accountId");
    }
    
}
