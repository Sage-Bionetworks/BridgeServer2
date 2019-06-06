package org.sagebionetworks.bridge.hibernate;

import static org.testng.Assert.assertEquals;

import org.testng.annotations.Test;

import org.sagebionetworks.bridge.dynamodb.DynamoExternalIdentifier;

import nl.jqno.equalsverifier.EqualsVerifier;
import nl.jqno.equalsverifier.Warning;

public class HibernateAccountSubstudyTest {

    @Test
    public void hashCodeEquals() {
        EqualsVerifier.forClass(DynamoExternalIdentifier.class).allFieldsShouldBeUsed()
            .suppress(Warning.NONFINAL_FIELDS).verify();
    }
    
    @Test
    public void test() {
        HibernateAccountSubstudy accountSubstudy = new HibernateAccountSubstudy("studyId", "substudyId", "accountId");
        
        // not yet used, but coming very shortly
        accountSubstudy.setExternalId("externalId");
        
        assertEquals(accountSubstudy.getStudyId(), "studyId");
        assertEquals(accountSubstudy.getSubstudyId(), "substudyId");
        assertEquals(accountSubstudy.getAccountId(), "accountId");
        assertEquals(accountSubstudy.getExternalId(), "externalId");
        
        accountSubstudy.setSubstudyId("newSubstudyId");
        assertEquals(accountSubstudy.getSubstudyId(), "newSubstudyId");
    }
}
