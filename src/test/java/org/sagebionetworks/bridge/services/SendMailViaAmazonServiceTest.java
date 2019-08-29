package org.sagebionetworks.bridge.services;

import static org.mockito.Mockito.any;
import static org.mockito.Mockito.when;
import static org.sagebionetworks.bridge.models.studies.MimeType.HTML;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.fail;

import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import org.sagebionetworks.bridge.exceptions.BridgeServiceException;
import org.sagebionetworks.bridge.models.studies.MimeType;
import org.sagebionetworks.bridge.models.studies.Study;
import org.sagebionetworks.bridge.models.templates.TemplateRevision;
import org.sagebionetworks.bridge.services.email.BasicEmailProvider;

import com.amazonaws.services.simpleemail.AmazonSimpleEmailServiceClient;
import com.amazonaws.services.simpleemail.model.SendRawEmailResult;

public class SendMailViaAmazonServiceTest {

    private static final String SUPPORT_EMAIL = "email@email.com";
    private static final String RECIPIENT_EMAIL = "recipient@recipient.com";
    
    private SendMailViaAmazonService service;
    
    private Study study;
    
    @Mock
    private AmazonSimpleEmailServiceClient emailClient;
    
    @Mock
    private EmailVerificationService emailVerificationService;
    
    @Mock
    private SendRawEmailResult result;
    
    @BeforeMethod
    public void before() {
        MockitoAnnotations.initMocks(this);
        
        study = Study.create();
        study.setName("Name");
        study.setSupportEmail(SUPPORT_EMAIL);
        
        service = new SendMailViaAmazonService();
        service.setEmailClient(emailClient);
        service.setEmailVerificationService(emailVerificationService);
    }
    
    @Test
    public void unverifiedEmailThrowsException() {
        when(emailVerificationService.isVerified(SUPPORT_EMAIL)).thenReturn(false);
        
        TemplateRevision revision = TemplateRevision.create();
        revision.setSubject("subject");
        revision.setDocumentContent("body");
        revision.setMimeType(HTML);
        
        BasicEmailProvider provider = new BasicEmailProvider.Builder()
                .withStudy(study)
                .withRecipientEmail(RECIPIENT_EMAIL)
                .withTemplateRevision(revision)
                .build();
        try {
            service.sendEmail(provider);
            fail("Should have thrown exception");
        } catch(BridgeServiceException e) {
            assertEquals(e.getMessage(), SendMailViaAmazonService.UNVERIFIED_EMAIL_ERROR);
        }
    }
    
    @Test
    public void verifiedEmailWorks() {
        when(emailClient.sendRawEmail(any())).thenReturn(result);
        when(emailVerificationService.isVerified(SUPPORT_EMAIL)).thenReturn(true);
        
        TemplateRevision revision = TemplateRevision.create();
        revision.setSubject("subject");
        revision.setDocumentContent("body");
        revision.setMimeType(MimeType.HTML);
        
        BasicEmailProvider provider = new BasicEmailProvider.Builder()
                .withStudy(study)
                .withRecipientEmail(RECIPIENT_EMAIL)
                .withTemplateRevision(revision)
                .build();
        service.sendEmail(provider);
    }
}
