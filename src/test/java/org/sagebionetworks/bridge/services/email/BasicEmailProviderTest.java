package org.sagebionetworks.bridge.services.email;

import static com.google.common.base.Charsets.UTF_8;
import static javax.mail.Part.ATTACHMENT;
import static org.sagebionetworks.bridge.models.apps.MimeType.HTML;
import static org.sagebionetworks.bridge.models.apps.MimeType.PDF;
import static org.sagebionetworks.bridge.models.apps.MimeType.TEXT;
import static org.sagebionetworks.bridge.services.email.EmailType.EMAIL_SIGN_IN;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertNull;

import java.io.InputStream;
import java.util.Map;

import javax.mail.internet.MimeBodyPart;

import org.apache.commons.io.IOUtils;
import org.testng.annotations.Test;

import org.sagebionetworks.bridge.models.apps.App;
import org.sagebionetworks.bridge.models.templates.TemplateRevision;

import com.google.common.collect.Sets;

public class BasicEmailProviderTest {
    @Test
    public void test() throws Exception {
        // Set up dependencies
        App app = App.create();
        app.setName("Name");
        app.setShortName("ShortName");
        app.setIdentifier("id");
        app.setSponsorName("SponsorName");
        app.setSupportEmail("support@email.com");
        app.setTechnicalEmail("tech@email.com");
        app.setConsentNotificationEmail("consent@email.com,consent2@email.com");

        TemplateRevision revision = TemplateRevision.create();
        revision.setSubject("Subject ${url}");
        revision.setDocumentContent("${studyName} ${studyShortName} ${studyId} "+
            "${appName} ${appShortName} ${appId} ${sponsorName} ${supportEmail} "+
            "${technicalEmail} ${consentEmail} ${url} ${expirationPeriod}");
        revision.setMimeType(HTML); 
        
        // Create
        BasicEmailProvider provider = new BasicEmailProvider.Builder()
            .withApp(app)
            .withRecipientEmail("recipient@recipient.com")
            .withRecipientEmail("recipient2@recipient.com")
            .withTemplateRevision(revision)
            .withExpirationPeriod("expirationPeriod", 60*60)
            .withToken("url", "some-url")
            .withType(EMAIL_SIGN_IN)
            .build();

        // Check provider attributes
        assertEquals(provider.getFormattedSenderEmail(), "Name <support@email.com>");
        assertEquals(provider.getType(), EMAIL_SIGN_IN);

        // Check email
        MimeTypeEmail email = provider.getMimeTypeEmail();
        assertEquals(email.getSubject(), "Subject some-url");
        assertEquals(email.getSenderAddress(), "\"Name\" <support@email.com>");
        assertEquals(Sets.newHashSet(email.getRecipientAddresses()),
                Sets.newHashSet("recipient@recipient.com", "recipient2@recipient.com"));
        assertEquals(email.getType(), EMAIL_SIGN_IN);

        MimeBodyPart body = email.getMessageParts().get(0);
        String bodyString = (String)body.getContent();
        assertEquals(bodyString,
                "Name ShortName id Name ShortName id SponsorName support@email.com tech@email.com consent@email.com some-url 1 hour");
    }

    @Test
    public void withOverrideSenderEmail() {
        // Set up dependencies
        App app = App.create();
        app.setName("App Name");
        app.setSupportEmail("email@email.com");

        TemplateRevision revision = TemplateRevision.create();
        revision.setSubject("Subject ${url}");
        revision.setDocumentContent("Body ${url}");
        revision.setMimeType(HTML);

        // Create
        BasicEmailProvider provider = new BasicEmailProvider.Builder().withTemplateRevision(revision)
                .withOverrideSenderEmail("example@example.com").withApp(app).build();

        // Check provider attributes
        assertEquals(provider.getPlainSenderEmail(), "example@example.com");
    }
    
    @Test
    public void nullTokenMapEntryDoesntBreakMap() throws Exception {
        TemplateRevision revision = TemplateRevision.create();
        revision.setSubject("asdf");
        revision.setDocumentContent("asdf");
        revision.setMimeType(TEXT);
        
        BasicEmailProvider provider = new BasicEmailProvider.Builder().withTemplateRevision(revision)
                .withRecipientEmail("email@email.com")
                .withOverrideSenderEmail("example@example.com").withApp(App.create()).build();
        
        Map<String,String> tokenMap = provider.getTokenMap();
        assertNull(tokenMap.get("supportName"));
    }    
    
    @Test
    public void canAddBinaryAttachment() throws Exception {
        TemplateRevision revision = TemplateRevision.create();
        revision.setSubject("Subject ${url}");
        revision.setDocumentContent("Body ${url}");
        revision.setMimeType(HTML);
        
        BasicEmailProvider provider = new BasicEmailProvider.Builder()
                .withBinaryAttachment("content.pdf", PDF, "some data".getBytes())
                .withRecipientEmail("email@email.com")
                .withOverrideSenderEmail("example@example.com")
                .withTemplateRevision(revision)
                .withApp(App.create()).build();
        
        MimeTypeEmail email = provider.getMimeTypeEmail();
        
        MimeBodyPart attachment = email.getMessageParts().get(1);
        
        String bodyContent = IOUtils.toString((InputStream)attachment.getContent(), UTF_8); 
        assertEquals(attachment.getFileName(), "content.pdf");
        assertEquals(bodyContent, "some data");
        assertEquals(attachment.getDisposition(), ATTACHMENT);
        // the mime type isn't changed because headers are not updated until you call
        // MimeMessage.saveChanges(), and these objects do not include the final 
        // Java Mail MimeMessage object.
    }
}
