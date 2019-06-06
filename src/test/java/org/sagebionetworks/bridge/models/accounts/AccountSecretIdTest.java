package org.sagebionetworks.bridge.models.accounts;

import static org.testng.Assert.assertEquals;

import org.testng.annotations.Test;

import nl.jqno.equalsverifier.EqualsVerifier;
import nl.jqno.equalsverifier.Warning;

public class AccountSecretIdTest {

    @Test
    public void hashCodeEquals() {
        EqualsVerifier.forClass(AccountSecretId.class).allFieldsShouldBeUsed().suppress(Warning.NONFINAL_FIELDS)
                .verify();
    }
    
    @Test
    public void create() { 
        AccountSecretId key = new AccountSecretId("accountId", "hash");
        assertEquals(key.getAccountId(), "accountId");
        assertEquals(key.getHash(), "hash");
    }
}
