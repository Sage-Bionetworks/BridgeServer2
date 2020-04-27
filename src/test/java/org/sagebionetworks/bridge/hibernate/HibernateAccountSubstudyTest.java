package org.sagebionetworks.bridge.hibernate;

import static org.sagebionetworks.bridge.TestConstants.TEST_APP_ID;
import static org.testng.Assert.assertEquals;

import org.testng.annotations.Test;

import nl.jqno.equalsverifier.EqualsVerifier;
import nl.jqno.equalsverifier.Warning;

public class HibernateAccountSubstudyTest {

    @Test
    public void hashCodeEquals() {
        EqualsVerifier.forClass(HibernateAccountSubstudy.class).allFieldsShouldBeUsed()
            .suppress(Warning.NONFINAL_FIELDS).verify();
    }
    
    @Test
    public void test() {
        HibernateAccountSubstudy accountSubstudy = new HibernateAccountSubstudy(TEST_APP_ID, "substudyId", "accountId");
        
        // not yet used, but coming very shortly
        accountSubstudy.setExternalId("externalId");
        
        assertEquals(accountSubstudy.getAppId(), TEST_APP_ID);
        assertEquals(accountSubstudy.getSubstudyId(), "substudyId");
        assertEquals(accountSubstudy.getAccountId(), "accountId");
        assertEquals(accountSubstudy.getExternalId(), "externalId");
        
        accountSubstudy.setSubstudyId("newSubstudyId");
        assertEquals(accountSubstudy.getSubstudyId(), "newSubstudyId");
    }
}
