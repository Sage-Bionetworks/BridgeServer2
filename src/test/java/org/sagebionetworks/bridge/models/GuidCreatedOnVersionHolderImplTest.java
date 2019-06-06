package org.sagebionetworks.bridge.models;

import org.testng.annotations.Test;

import nl.jqno.equalsverifier.EqualsVerifier;
import nl.jqno.equalsverifier.Warning;

public class GuidCreatedOnVersionHolderImplTest {

    @Test
    public void equalsHashCode() {
        EqualsVerifier.forClass(GuidCreatedOnVersionHolderImpl.class).suppress(Warning.NONFINAL_FIELDS).allFieldsShouldBeUsed().verify();
    }
    
}
