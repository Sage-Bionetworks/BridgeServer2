package org.sagebionetworks.bridge.services.email;

import static org.testng.Assert.assertEquals;

import javax.mail.MessagingException;

import org.testng.annotations.Test;

import org.sagebionetworks.bridge.models.studies.Study;

public class MimeTypeEmailProviderTest {
    static class MimeTypeEmailProviderImpl extends MimeTypeEmailProvider {
        public MimeTypeEmailProviderImpl(Study study) {
            super(study);
        }
        public MimeTypeEmail getMimeTypeEmail() throws MessagingException {
            return null;
        }
    }
    
    @Test
    public void works() {
        Study study = Study.create();
        study.setName("Very Useful Study 🐶");
        study.setSupportEmail("support@support.com");
        
        MimeTypeEmailProvider provider = new MimeTypeEmailProviderImpl(study);
        
        assertEquals(provider.getPlainSenderEmail(), "support@support.com");
        assertEquals(provider.getFormattedSenderEmail(), "Very Useful Study 🐶 <support@support.com>");
    }
    
    @Test
    public void worksWithMultipleAddresses() {
        Study study = Study.create();
        study.setName("Very Useful Study 🐶");
        study.setSupportEmail("support@support.com,email@email.com");
        
        MimeTypeEmailProvider provider = new MimeTypeEmailProviderImpl(study);
        
        assertEquals(provider.getPlainSenderEmail(), "support@support.com");
        assertEquals(provider.getFormattedSenderEmail(), "Very Useful Study 🐶 <support@support.com>");
    }
}
