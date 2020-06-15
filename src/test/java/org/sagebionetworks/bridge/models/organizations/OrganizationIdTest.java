package org.sagebionetworks.bridge.models.organizations;

import static org.sagebionetworks.bridge.TestConstants.TEST_APP_ID;
import static org.testng.Assert.assertEquals;

import org.testng.annotations.Test;

import nl.jqno.equalsverifier.EqualsVerifier;
import nl.jqno.equalsverifier.Warning;

public class OrganizationIdTest {
    @Test
    public void hashCodeEquals() {
        EqualsVerifier.forClass(OrganizationId.class)
            .allFieldsShouldBeUsed()
            .suppress(Warning.NONFINAL_FIELDS)
            .verify();
    }
    
    @Test
    public void create() { 
        OrganizationId key = new OrganizationId(TEST_APP_ID, "orgId");
        assertEquals(key.getAppId(), TEST_APP_ID);
        assertEquals(key.getIdentifier(), "orgId");
    }
}
