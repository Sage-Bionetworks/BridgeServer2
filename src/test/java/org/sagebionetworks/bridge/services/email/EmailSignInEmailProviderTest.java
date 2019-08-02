package org.sagebionetworks.bridge.services.email;

import static org.testng.Assert.assertEquals;

import java.net.URLEncoder;

import org.testng.annotations.Test;

import org.sagebionetworks.bridge.BridgeUtils;
import org.sagebionetworks.bridge.config.BridgeConfigFactory;
import org.sagebionetworks.bridge.dynamodb.DynamoStudy;
import org.sagebionetworks.bridge.models.studies.EmailTemplate;
import org.sagebionetworks.bridge.models.studies.MimeType;
import org.sagebionetworks.bridge.models.studies.Study;
import org.sagebionetworks.bridge.models.templates.TemplateRevision;

public class EmailSignInEmailProviderTest {

    private static final String RECIPIENT_EMAIL = "recipient@recipient.com";
    private static final String SUBJECT_TEMPLATE = "${studyName} sign in link";
    private static final String BODY_TEMPLATE = "Click here to sign in: <a href=\"" +
            "https://${host}/mobile/startSession.html?email=${email}&study=${studyId}&token=${token}\""+
            ">https://${host}/mobile/startSession.html?email=${email}&study=${studyId}&token=${token}</a>";

    @Test
    public void testProvider() throws Exception {
        Study study = new DynamoStudy();
        study.setName("Study name");
        study.setIdentifier("foo");
        study.setSupportEmail("support@email.com");
        study.setEmailSignInTemplate(new EmailTemplate(SUBJECT_TEMPLATE, BODY_TEMPLATE, MimeType.HTML));
        
        // Verifying in particular that all instances of a template variable are replaced
        // in the template.
        BasicEmailProvider provider = new BasicEmailProvider.Builder()
                .withStudy(study)
                .withTemplateRevision(TemplateRevision.create(study.getEmailSignInTemplate()))
                .withRecipientEmail(RECIPIENT_EMAIL)
                .withToken("email", BridgeUtils.encodeURIComponent(RECIPIENT_EMAIL))
                .withToken("token", "ABC").build();
        
        String url = String.format("https://%s/mobile/startSession.html?email=%s&study=foo&token=ABC", 
                BridgeConfigFactory.getConfig().getHostnameWithPostfix("ws"),
                URLEncoder.encode(RECIPIENT_EMAIL, "UTF-8"));
        
        String finalBody = String.format("Click here to sign in: <a href=\"%s\">%s</a>", url, url);
        
        MimeTypeEmail email = provider.getMimeTypeEmail();
        assertEquals(email.getSenderAddress(), "\"Study name\" <support@email.com>");
        assertEquals(email.getRecipientAddresses().get(0), RECIPIENT_EMAIL);
        assertEquals(email.getSubject(), "Study name sign in link");
        assertEquals(email.getMessageParts().get(0).getContent(), finalBody);
    }
    
}
