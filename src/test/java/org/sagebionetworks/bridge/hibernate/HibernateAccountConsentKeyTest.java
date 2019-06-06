package org.sagebionetworks.bridge.hibernate;

import org.testng.annotations.Test;

import nl.jqno.equalsverifier.EqualsVerifier;
import nl.jqno.equalsverifier.Warning;

public class HibernateAccountConsentKeyTest {
    @Test
    public void equalsVerified() {
        EqualsVerifier.forClass(HibernateAccountConsentKey.class).allFieldsShouldBeUsed()
                .suppress(Warning.NONFINAL_FIELDS).verify();
    }
}
