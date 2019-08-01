package org.sagebionetworks.bridge.sms;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertNull;

import java.util.Map;

import org.testng.annotations.Test;

import org.sagebionetworks.bridge.BridgeConstants;
import org.sagebionetworks.bridge.TestConstants;
import org.sagebionetworks.bridge.models.sms.SmsType;
import org.sagebionetworks.bridge.models.studies.Study;
import org.sagebionetworks.bridge.models.templates.TemplateRevision;

import com.amazonaws.services.sns.model.PublishRequest;

public class SmsMessageProviderTest {
    @Test
    public void test() {
        // Set up dependencies
        Study study = Study.create();
        study.setName("Name");
        study.setShortName("ShortName");
        study.setIdentifier("id");
        study.setSponsorName("SponsorName");
        study.setSupportEmail("support@email.com");
        study.setTechnicalEmail("tech@email.com");
        study.setConsentNotificationEmail("consent@email.com,consent2@email.com");

        TemplateRevision revision = TemplateRevision.create();
        revision.setDocumentContent("${studyShortName} ${url} ${supportEmail} ${expirationPeriod}");
        
        String expectedMessage = "ShortName some-url support@email.com 4 hours";
        
        // Create
        SmsMessageProvider provider = new SmsMessageProvider.Builder()
            .withStudy(study)
            .withPhone(TestConstants.PHONE)
            .withTemplateRevision(revision)
            .withTransactionType()
            .withExpirationPeriod("expirationPeriod", 60*60*4) // 4 hours
            .withToken("url", "some-url").build();
        assertEquals(provider.getSmsType(), "Transactional");
        assertEquals(provider.getSmsTypeEnum(), SmsType.TRANSACTIONAL);
        assertEquals(provider.getFormattedMessage(), expectedMessage);

        // Check email
        PublishRequest request = provider.getSmsRequest();
        assertEquals(request.getMessage(), expectedMessage);
        assertEquals(request.getMessageAttributes().get(BridgeConstants.AWS_SMS_SENDER_ID).getStringValue(),
                study.getShortName());
        assertEquals(request.getMessageAttributes().get(BridgeConstants.AWS_SMS_TYPE).getStringValue(),
                "Transactional");
        
        assertEquals(provider.getTokenMap().get("url"), "some-url");
        assertEquals(provider.getTokenMap().get("expirationPeriod"), "4 hours");
        // BridgeUtils.studyTemplateVariables() has been called
        assertEquals(provider.getTokenMap().get("studyName"), "Name");
        assertEquals(provider.getTokenMap().get("studyShortName"), "ShortName");
        assertEquals(provider.getTokenMap().get("studyId"), "id");
        assertEquals(provider.getTokenMap().get("sponsorName"), "SponsorName");
        assertEquals(provider.getTokenMap().get("supportEmail"), "support@email.com");
        assertEquals(provider.getTokenMap().get("technicalEmail"), "tech@email.com");
        assertEquals(provider.getTokenMap().get("consentEmail"), "consent@email.com");
    }
    
    @Test
    public void defaultsShortNameToBridge() {
        // Set up dependencies
        Study study = Study.create();
        TemplateRevision revision = TemplateRevision.create();
        revision.setDocumentContent("${studyShortName} ${url} ${supportEmail}");
        
        // Create
        SmsMessageProvider provider = new SmsMessageProvider.Builder()
            .withStudy(study)
            .withPhone(TestConstants.PHONE)
            .withTemplateRevision(revision)
            .withPromotionType()
            .withToken("url", "some-url").build();
        PublishRequest request = provider.getSmsRequest();
        assertEquals("Bridge some-url", request.getMessage());
    }
    
    @Test
    public void nullTokenMapEntryDoesntBreakMap() {
        SmsMessageProvider provider = new SmsMessageProvider.Builder()
                .withStudy(Study.create())
                .withPhone(TestConstants.PHONE)
                .withTemplateRevision(TemplateRevision.create())
                .withPromotionType()
                .withToken("url", null).build();
        
        Map<String,String> tokenMap = provider.getTokenMap();
        assertNull(tokenMap.get("supportName"));
    }
    
    @Test
    public void canConstructPromotionalMessage() {
        SmsMessageProvider provider = new SmsMessageProvider.Builder()
                .withStudy(Study.create())
                .withPhone(TestConstants.PHONE)
                .withTemplateRevision(TemplateRevision.create())
                .withPromotionType().build();
        assertEquals("Promotional", provider.getSmsType());
        assertEquals(SmsType.PROMOTIONAL, provider.getSmsTypeEnum());
    }
}
