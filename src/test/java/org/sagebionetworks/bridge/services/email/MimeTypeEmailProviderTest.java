package org.sagebionetworks.bridge.services.email;

import static org.testng.Assert.assertEquals;

import javax.mail.MessagingException;

import org.testng.annotations.Test;

import org.sagebionetworks.bridge.models.apps.App;

public class MimeTypeEmailProviderTest {
    static class MimeTypeEmailProviderImpl extends MimeTypeEmailProvider {
        public MimeTypeEmailProviderImpl(App app) {
            super(app);
        }
        public MimeTypeEmail getMimeTypeEmail() throws MessagingException {
            return null;
        }
    }
    
    @Test
    public void works() {
        App app = App.create();
        app.setName("Very Useful App 🐶");
        app.setSupportEmail("support@support.com");
        
        MimeTypeEmailProvider provider = new MimeTypeEmailProviderImpl(app);
        
        assertEquals(provider.getPlainSenderEmail(), "support@support.com");
        assertEquals(provider.getFormattedSenderEmail(), "Very Useful App 🐶 <support@support.com>");
    }
    
    @Test
    public void worksWithMultipleAddresses() {
        App app = App.create();
        app.setName("Very Useful App 🐶");
        app.setSupportEmail("support@support.com,email@email.com");
        
        MimeTypeEmailProvider provider = new MimeTypeEmailProviderImpl(app);
        
        assertEquals(provider.getPlainSenderEmail(), "support@support.com");
        assertEquals(provider.getFormattedSenderEmail(), "Very Useful App 🐶 <support@support.com>");
    }
}
