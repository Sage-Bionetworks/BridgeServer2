package org.sagebionetworks.bridge.hibernate;

import org.testng.annotations.Test;

import nl.jqno.equalsverifier.EqualsVerifier;
import nl.jqno.equalsverifier.Warning;

public class HibernateSharedModuleMetadataKeyTest {
    @Test
    public void equalsVerifier() {
        EqualsVerifier.forClass(HibernateSharedModuleMetadataKey.class).suppress(Warning.NONFINAL_FIELDS)
                .allFieldsShouldBeUsed().verify();
    }
}
