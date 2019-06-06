package org.sagebionetworks.bridge.services;

import static org.sagebionetworks.bridge.services.EmailVerificationStatus.PENDING;
import static org.sagebionetworks.bridge.services.EmailVerificationStatus.UNVERIFIED;
import static org.sagebionetworks.bridge.services.EmailVerificationStatus.VERIFIED;
import static org.testng.Assert.assertEquals;

import org.testng.annotations.Test;

public class EmailVerificationStatusTest {

    @Test
    public void testStringsConvertToTypes() {
        assertEquals(EmailVerificationStatus.fromSesVerificationStatus("Pending"), PENDING);
        assertEquals(EmailVerificationStatus.fromSesVerificationStatus("Success"), VERIFIED);
        assertEquals(EmailVerificationStatus.fromSesVerificationStatus("Anything Else"), UNVERIFIED);
        assertEquals(EmailVerificationStatus.fromSesVerificationStatus(EmailVerificationStatus.PENDING.name()), PENDING);
        assertEquals(EmailVerificationStatus.fromSesVerificationStatus(EmailVerificationStatus.VERIFIED.name()), VERIFIED);
        assertEquals(EmailVerificationStatus.fromSesVerificationStatus(EmailVerificationStatus.UNVERIFIED.name()), UNVERIFIED);
        assertEquals(EmailVerificationStatus.fromSesVerificationStatus(null), UNVERIFIED);
    }
    
}
