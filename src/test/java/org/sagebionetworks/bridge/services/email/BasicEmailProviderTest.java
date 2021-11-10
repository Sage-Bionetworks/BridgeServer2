package org.sagebionetworks.bridge.services.email;

import static com.google.common.base.Charsets.UTF_8;
import static javax.mail.Part.ATTACHMENT;
import static org.sagebionetworks.bridge.TestConstants.PHONE;
import static org.sagebionetworks.bridge.models.apps.MimeType.HTML;
import static org.sagebionetworks.bridge.models.apps.MimeType.PDF;
import static org.sagebionetworks.bridge.models.apps.MimeType.TEXT;
import static org.sagebionetworks.bridge.services.email.EmailType.EMAIL_SIGN_IN;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertNull;

import java.io.InputStream;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import javax.mail.internet.MimeBodyPart;

import org.apache.commons.io.IOUtils;
import org.testng.annotations.Test;
import org.sagebionetworks.bridge.models.accounts.StudyParticipant;
import org.sagebionetworks.bridge.models.apps.App;
import org.sagebionetworks.bridge.models.templates.TemplateRevision;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Sets;
import com.newrelic.agent.deps.com.google.common.base.Joiner;

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
        
        StudyParticipant participant = new StudyParticipant.Builder()
                .withFirstName("firstName")
                .withLastName("lastName")
                .withEmail("participant@email.com")
                .withAttributes(ImmutableMap.of("prop_A", "A", "prop_B", "B"))
                .withPhone(PHONE)
                .build();

        List<String> documentContentElements = Arrays.asList("studyName", "studyShortName", 
                "studyId", "appName", "appShortName", "appId", "sponsorName", "supportEmail", 
                "technicalEmail", "consentEmail", "url", "expirationPeriod", 
                "participantFirstName", "participantLastName", "participantEmail", 
                "participant.prop_A", "participant.prop_B", "participantPhone", 
                "participantPhoneRegion", "participantPhoneNationalFormat");
        
        String documentContent = Joiner.on("~").join(documentContentElements.stream()
                .map(s -> ("${" + s + "}")).collect(Collectors.toList()));

        TemplateRevision revision = TemplateRevision.create();
        revision.setSubject("Subject ${url}");
        revision.setDocumentContent(documentContent);
        revision.setMimeType(HTML); 
        
        // Create
        BasicEmailProvider provider = new BasicEmailProvider.Builder()
            .withApp(app)
            .withParticipant(participant)
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
        
        String[] elements = bodyString.split("~");
        assertEquals(elements[0], "Name");
        assertEquals(elements[1], "ShortName");
        assertEquals(elements[2], "id");
        assertEquals(elements[3], "Name");
        assertEquals(elements[4], "ShortName");
        assertEquals(elements[5], "id");
        assertEquals(elements[6], "SponsorName");
        assertEquals(elements[7], "support@email.com");
        assertEquals(elements[8], "tech@email.com");
        assertEquals(elements[9], "consent@email.com");
        assertEquals(elements[10], "some-url");
        assertEquals(elements[11], "1 hour");
        assertEquals(elements[12], "firstName");
        assertEquals(elements[13], "lastName");
        assertEquals(elements[14], "participant@email.com");
        assertEquals(elements[15], "A");
        assertEquals(elements[16], "B");
        assertEquals(elements[17], PHONE.getNumber());
        assertEquals(elements[18], PHONE.getRegionCode());
        assertEquals(elements[19], PHONE.getNationalFormat());
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
