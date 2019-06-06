package org.sagebionetworks.bridge.models.substudies;

import static org.testng.Assert.assertEquals;

import org.testng.annotations.Test;

public class AccountSubstudyTest {

    @Test
    public void create() {
        AccountSubstudy substudy = AccountSubstudy.create("studyId", "substudyId", "accountId");
        assertEquals(substudy.getStudyId(), "studyId");
        assertEquals(substudy.getSubstudyId(), "substudyId");
        assertEquals(substudy.getAccountId(), "accountId");
    }
    
}
