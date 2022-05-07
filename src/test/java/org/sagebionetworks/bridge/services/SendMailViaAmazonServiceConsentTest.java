package org.sagebionetworks.bridge.services;

import static com.google.common.base.Charsets.UTF_8;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.notNull;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.sagebionetworks.bridge.TestConstants.TEST_APP_ID;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertTrue;

import java.io.FileInputStream;
import java.util.List;

import com.amazonaws.AmazonServiceException;
import com.amazonaws.services.simpleemail.model.MessageRejectedException;
import org.apache.commons.io.IOUtils;
import org.mockito.ArgumentCaptor;

import org.sagebionetworks.bridge.TestConstants;
import org.sagebionetworks.bridge.dynamodb.DynamoApp;
import org.sagebionetworks.bridge.exceptions.BridgeServiceException;
import org.sagebionetworks.bridge.time.DateUtils;
import org.sagebionetworks.bridge.models.accounts.SharingScope;
import org.sagebionetworks.bridge.models.accounts.StudyParticipant;
import org.sagebionetworks.bridge.models.apps.App;
import org.sagebionetworks.bridge.models.apps.MimeType;
import org.sagebionetworks.bridge.models.subpopulations.ConsentSignature;
import org.sagebionetworks.bridge.models.subpopulations.StudyConsent;
import org.sagebionetworks.bridge.models.subpopulations.StudyConsentView;
import org.sagebionetworks.bridge.models.subpopulations.Subpopulation;
import org.sagebionetworks.bridge.models.templates.TemplateRevision;
import org.sagebionetworks.bridge.services.email.BasicEmailProvider;

import com.amazonaws.services.simpleemail.AmazonSimpleEmailService;
import com.amazonaws.services.simpleemail.model.SendRawEmailRequest;
import com.amazonaws.services.simpleemail.model.SendRawEmailResult;
import com.google.common.base.Charsets;
import org.springframework.core.io.ClassPathResource;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

/**
 * Set-up here for consent-specific tests is extensive, so tests for the participant roster
 * are in a separate class.
 */
public class SendMailViaAmazonServiceConsentTest {
    private static final String SUPPORT_EMAIL = "app-support-email@study.com";
    private static final String FROM_APP_AS_FORMATTED = "\"Test App (Sage)\" <"+SUPPORT_EMAIL+">";
    private static final StudyParticipant PARTICIPANT = new StudyParticipant.Builder()
            .withEmail("test-user@sagebase.org").withEmailVerified(true).build();;
    private SendMailViaAmazonService service;
    private AmazonSimpleEmailService emailClient;
    private AppService appService;
    private StudyConsentService studyConsentService;
    private EmailVerificationService emailVerificationService;
    private ArgumentCaptor<SendRawEmailRequest> argument;
    private App app;
    private String consentBodyTemplate;
    private Subpopulation subpopulation;
    private TemplateRevision revision;
    
    @BeforeMethod
    public void setUp() throws Exception {
        consentBodyTemplate = IOUtils.toString(new FileInputStream(new ClassPathResource(
                "conf/app-defaults/consent-page.xhtml").getFile()), UTF_8);
        
        app = new DynamoApp();
        app.setName("Test App (Sage)");
        app.setIdentifier(TEST_APP_ID);
        app.setSupportEmail(SUPPORT_EMAIL);

        revision = TemplateRevision.create();
        revision.setSubject("Subject of Template");
        revision.setDocumentContent("Body of Template");
        revision.setMimeType(MimeType.HTML);
        
        appService = mock(AppService.class);
        when(appService.getApp(app.getIdentifier())).thenReturn(app);
        
        emailClient = mock(AmazonSimpleEmailService.class);
        argument = ArgumentCaptor.forClass(SendRawEmailRequest.class);

        service = new SendMailViaAmazonService();
        service.setEmailClient(emailClient);
        
        emailVerificationService = mock(EmailVerificationService.class);
        service.setEmailVerificationService(emailVerificationService);
        
        subpopulation = Subpopulation.create();
        subpopulation.setGuidString(TEST_APP_ID);
        
        StudyConsentView view = new StudyConsentView(mock(StudyConsent.class), 
            "<document>Had this been a real app: @@name@@ @@signing.date@@ @@email@@ @@sharing@@</document>");
        
        studyConsentService = mock(StudyConsentService.class);
        when(studyConsentService.getActiveConsent(subpopulation)).thenReturn(view);
    }

    @Test
    public void sendConsentEmail() {
        // mock email client with success
        when(emailClient.sendRawEmail(notNull())).thenReturn(
                new SendRawEmailResult().withMessageId("test message id"));
        when(emailVerificationService.isVerified(SUPPORT_EMAIL)).thenReturn(true);
        
        // set up inputs
        ConsentSignature consent = new ConsentSignature.Builder().withName("Test 2").withBirthdate("1950-05-05")
                .withSignedOn(DateUtils.getCurrentMillisFromEpoch()).build();
        
        String htmlTemplate = studyConsentService.getActiveConsent(subpopulation).getDocumentContent();
        
        ConsentPdf consentPdf = new ConsentPdf(app, PARTICIPANT, consent, SharingScope.SPONSORS_AND_PARTNERS,
                htmlTemplate, consentBodyTemplate);
        
        BasicEmailProvider provider = new BasicEmailProvider.Builder()
                .withApp(app)
                .withTemplateRevision(revision)
                .withBinaryAttachment("consent.pdf", MimeType.PDF, consentPdf.getBytes())
                .withRecipientEmail("test-user@sagebase.org").build();
        service.sendEmail(provider);

        verify(emailClient).sendRawEmail(argument.capture());

        // validate from
        SendRawEmailRequest req = argument.getValue();
        assertEquals(req.getSource(), FROM_APP_AS_FORMATTED, "Correct sender");

        // validate to
        List<String> toList = req.getDestinations();
        assertEquals(toList.size(), 1, "Correct number of recipients");
        assertEquals(toList.get(0), "test-user@sagebase.org", "Correct recipient");

        // Validate message content. MIME message must be ASCII
        String rawMessage = new String(req.getRawMessage().getData().array(), Charsets.US_ASCII);
        assertTrue(rawMessage.contains("Body of Template"), "Contains consent content");
    }

    @Test
    public void sendConsentEmailWithSignatureImage() {
        // mock email client with success
        when(emailClient.sendRawEmail(notNull())).thenReturn(
                new SendRawEmailResult().withMessageId("test message id"));
        when(emailVerificationService.isVerified(SUPPORT_EMAIL)).thenReturn(true);
        
        // set up inputs
        ConsentSignature consent = new ConsentSignature.Builder().withName("Eggplant McTester")
                .withBirthdate("1970-05-01").withImageData(TestConstants.DUMMY_IMAGE_DATA)
                .withImageMimeType("image/fake").withSignedOn(DateUtils.getCurrentMillisFromEpoch()).build();
        
        String htmlTemplate = studyConsentService.getActiveConsent(subpopulation).getDocumentContent();
        
        ConsentPdf consentPdf = new ConsentPdf(app, PARTICIPANT, consent,
                SharingScope.SPONSORS_AND_PARTNERS, htmlTemplate, consentBodyTemplate);
        
        BasicEmailProvider provider = new BasicEmailProvider.Builder()
                .withApp(app)
                .withTemplateRevision(revision)
                .withBinaryAttachment("consent.pdf", MimeType.PDF, consentPdf.getBytes())
                .withRecipientEmail("test-user@sagebase.org").build();
        
        service.sendEmail(provider);

        verify(emailClient).sendRawEmail(argument.capture());

        // validate from
        SendRawEmailRequest req = argument.getValue();
        assertEquals(req.getSource(), FROM_APP_AS_FORMATTED, "Correct sender");

        // validate to
        List<String> toList = req.getDestinations();
        assertEquals(toList.size(), 1, "Correct number of recipients");
        assertEquals(toList.get(0), "test-user@sagebase.org", "Correct recipient");

        // Validate message content. MIME message must be ASCII
        String rawMessage = new String(req.getRawMessage().getData().array(), Charsets.US_ASCII);
        // The  HTML
        assertTrue(rawMessage.contains("Content-Type: text/html; charset=utf-8"), "Contains body text");
        assertTrue(rawMessage.contains("Body of Template"), "Contains body template");
        
        // The PDF attachment
        assertTrue(rawMessage.contains("Content-Type: application/pdf; name=consent.pdf"), "Contains PDF attachment");
        assertTrue(rawMessage.contains("Content-Disposition: attachment; filename=consent.pdf"), "Contains correct disposition");
        assertTrue(rawMessage.contains("JVBERi0xLjQKJeLjz9"), "Contains base 64 encoded image");
    }

    @Test
    public void messageRejectedNotPropagated() {
        // mock email client with exception
        when(emailClient.sendRawEmail(notNull())).thenThrow(MessageRejectedException.class);
        when(emailVerificationService.isVerified(SUPPORT_EMAIL)).thenReturn(true);
        
        // set up inputs
        ConsentSignature consent = new ConsentSignature.Builder().withName("Test 2").withBirthdate("1950-05-05")
                .withSignedOn(DateUtils.getCurrentMillisFromEpoch()).build();
        
        String htmlTemplate = studyConsentService.getActiveConsent(subpopulation).getDocumentContent();
        
        ConsentPdf consentPdf = new ConsentPdf(app, PARTICIPANT, consent, SharingScope.SPONSORS_AND_PARTNERS,
                htmlTemplate, consentBodyTemplate);
        
        BasicEmailProvider provider = new BasicEmailProvider.Builder()
                .withApp(app)
                .withTemplateRevision(revision)
                .withBinaryAttachment("consent.pdf", MimeType.PDF, consentPdf.getBytes())
                .withRecipientEmail("test-user@sagebase.org").build();

        // execute
        service.sendEmail(provider);

        // Verify email client was called. No need to test anything else, everything else is already tested in the
        // normal case above.
        verify(emailClient).sendRawEmail(any());
    }

    @Test(expectedExceptions = BridgeServiceException.class)
    public void otherExceptionsPropagated() {
        // mock email client with exception
        when(emailClient.sendRawEmail(notNull())).thenThrow(AmazonServiceException.class);

        // set up inputs
        ConsentSignature consent = new ConsentSignature.Builder().withName("Test 2").withBirthdate("1950-05-05")
                .withSignedOn(DateUtils.getCurrentMillisFromEpoch()).build();
        
        String htmlTemplate = studyConsentService.getActiveConsent(subpopulation).getDocumentContent();

        ConsentPdf consentPdf = new ConsentPdf(app, PARTICIPANT, consent, SharingScope.SPONSORS_AND_PARTNERS,
                htmlTemplate, consentBodyTemplate);
        
        BasicEmailProvider provider = new BasicEmailProvider.Builder()
                .withApp(app)
                .withTemplateRevision(revision)
                .withBinaryAttachment("consent.pdf", MimeType.PDF, consentPdf.getBytes())
                .withRecipientEmail("test-user@sagebase.org").build();

        // execute
        service.sendEmail(provider);
    }
}