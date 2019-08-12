package org.sagebionetworks.bridge.services;

import static org.sagebionetworks.bridge.TestConstants.TEST_STUDY;
import static org.sagebionetworks.bridge.TestConstants.TEST_STUDY_IDENTIFIER;
import static org.sagebionetworks.bridge.TestConstants.TIMESTAMP;
import static org.sagebionetworks.bridge.models.studies.MimeType.HTML;
import static org.sagebionetworks.bridge.models.studies.MimeType.TEXT;
import static org.sagebionetworks.bridge.models.templates.TemplateType.EMAIL_ACCOUNT_EXISTS;
import static org.sagebionetworks.bridge.models.templates.TemplateType.EMAIL_APP_INSTALL_LINK;
import static org.sagebionetworks.bridge.models.templates.TemplateType.EMAIL_RESET_PASSWORD;
import static org.sagebionetworks.bridge.models.templates.TemplateType.EMAIL_SIGNED_CONSENT;
import static org.sagebionetworks.bridge.models.templates.TemplateType.EMAIL_SIGN_IN;
import static org.sagebionetworks.bridge.models.templates.TemplateType.EMAIL_VERIFY_EMAIL;
import static org.sagebionetworks.bridge.models.templates.TemplateType.SMS_ACCOUNT_EXISTS;
import static org.sagebionetworks.bridge.models.templates.TemplateType.SMS_APP_INSTALL_LINK;
import static org.sagebionetworks.bridge.models.templates.TemplateType.SMS_PHONE_SIGN_IN;
import static org.sagebionetworks.bridge.models.templates.TemplateType.SMS_RESET_PASSWORD;
import static org.sagebionetworks.bridge.models.templates.TemplateType.SMS_SIGNED_CONSENT;
import static org.sagebionetworks.bridge.models.templates.TemplateType.SMS_VERIFY_PHONE;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertNotNull;
import static org.testng.Assert.assertNull;
import static org.testng.Assert.assertTrue;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.MapDifference;
import com.google.common.collect.Maps;

import org.joda.time.DateTime;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import org.sagebionetworks.bridge.exceptions.EntityNotFoundException;
import org.sagebionetworks.bridge.models.CreatedOnHolder;
import org.sagebionetworks.bridge.models.GuidVersionHolder;
import org.sagebionetworks.bridge.models.PagedResourceList;
import org.sagebionetworks.bridge.models.studies.EmailTemplate;
import org.sagebionetworks.bridge.models.studies.SmsTemplate;
import org.sagebionetworks.bridge.models.studies.Study;
import org.sagebionetworks.bridge.models.templates.Template;
import org.sagebionetworks.bridge.models.templates.TemplateRevision;
import org.sagebionetworks.bridge.models.templates.TemplateType;

public class TemplateMigrationServiceTest extends Mockito {
    private static final String TEMPLATE_GUID = "oneTemplateGuid";
    private static final DateTime CREATED_ON = TIMESTAMP;
    private static final EmailTemplate EMAIL_TEMPLATE = new EmailTemplate("Subject", "Body", HTML);
    private static final SmsTemplate SMS_TEMPLATE = new SmsTemplate("Message");
    
    @Mock
    TemplateService mockTemplateService;
    
    @Mock
    TemplateRevisionService mockTemplateRevisionService;
    
    @InjectMocks
    TemplateMigrationService service;
    
    @Captor
    ArgumentCaptor<Template> templateCaptor;
    
    @Captor
    ArgumentCaptor<TemplateRevision> revisionCaptor;
    
    Study study;
    
    @BeforeMethod
    void beforeMethod() throws Exception {
        MockitoAnnotations.initMocks(this);
        
        study = Study.create();
        study.setIdentifier(TEST_STUDY_IDENTIFIER);
        
        GuidVersionHolder keys = new GuidVersionHolder(TEMPLATE_GUID, 1L);
        when(mockTemplateService.createTemplate(eq(study), any())).thenReturn(keys);
        when(mockTemplateService.migrateTemplate(eq(study), any(), any())).thenReturn(keys);
        
        CreatedOnHolder revKeys = new CreatedOnHolder(CREATED_ON);
        when(mockTemplateRevisionService.createTemplateRevision(eq(TEST_STUDY), eq(TEMPLATE_GUID), any())).thenReturn(revKeys);
    }
    
    private Study createNewStudy() {
        // We're assuming here that no templates are set going forward for new studies,
        // which will need to be adjusted in the StudyService.
        Study study = Study.create();
        study.setIdentifier(TEST_STUDY_IDENTIFIER);
        return study;
    }
    
    private Study createMigratedStudy() {
        Study study = Study.create();
        study.setIdentifier(TEST_STUDY_IDENTIFIER);
        Map<String,String> map = new HashMap<>();
        map.put(EMAIL_ACCOUNT_EXISTS.name().toLowerCase(), TEMPLATE_GUID);
        map.put(EMAIL_APP_INSTALL_LINK.name().toLowerCase(), TEMPLATE_GUID);
        map.put(EMAIL_RESET_PASSWORD.name().toLowerCase(), TEMPLATE_GUID);
        map.put(EMAIL_SIGN_IN.name().toLowerCase(), TEMPLATE_GUID);
        map.put(EMAIL_SIGNED_CONSENT.name().toLowerCase(), TEMPLATE_GUID);
        map.put(EMAIL_VERIFY_EMAIL.name().toLowerCase(), TEMPLATE_GUID);
        map.put(SMS_ACCOUNT_EXISTS.name().toLowerCase(), TEMPLATE_GUID);
        map.put(SMS_APP_INSTALL_LINK.name().toLowerCase(), TEMPLATE_GUID);
        map.put(SMS_PHONE_SIGN_IN.name().toLowerCase(), TEMPLATE_GUID);
        map.put(SMS_RESET_PASSWORD.name().toLowerCase(), TEMPLATE_GUID);
        map.put(SMS_SIGNED_CONSENT.name().toLowerCase(), TEMPLATE_GUID);
        map.put(SMS_VERIFY_PHONE.name().toLowerCase(), TEMPLATE_GUID);
        study.setDefaultTemplates(map);
        return study;
    }
    
    private EmailTemplate emailTemplate(TemplateType type) {
        return new EmailTemplate(type.name() + " from study", type.name() + " from study", HTML);
    }
    
    private SmsTemplate smsTemplate(TemplateType type) {
        return new SmsTemplate(type.name() + " from study");
    }
    
    private void mockAllServicesWorking() throws Exception {
        Template template = Template.create();
        template.setGuid(TEMPLATE_GUID);
        template.setPublishedCreatedOn(CREATED_ON);
        
        doReturn(template).when(mockTemplateService).getTemplate(TEST_STUDY, TEMPLATE_GUID);
        
        PagedResourceList<? extends Template> page = new PagedResourceList<>(ImmutableList.of(template), 1);
        doReturn(page).when(mockTemplateService).getTemplatesForType(eq(TEST_STUDY), any(), eq(0), eq(50), eq(false));
        
        TemplateRevision revision = TemplateRevision.create();
        revision.setSubject("from db");
        revision.setDocumentContent("from db");
        doReturn(revision).when(mockTemplateRevisionService).getTemplateRevision(TEST_STUDY, TEMPLATE_GUID, CREATED_ON);
    }
    
    private void mockNoTemplates() throws Exception {
        PagedResourceList<? extends Template> page = new PagedResourceList<>(ImmutableList.of(), 0);
        doReturn(page).when(mockTemplateService).getTemplatesForType(eq(TEST_STUDY), any(), eq(0), eq(50), eq(false));
    }
    
    @Test
    public void migrationShortCircuitedOnceCompleted() {
        Study study = createMigratedStudy();
        service.migrateTemplates(study);
        verifyZeroInteractions(mockTemplateService);
    }
    
    @Test
    public void migratedStudyPreserved() throws Exception {
        mockAllServicesWorking();
        Study study = createMigratedStudy();
        
        boolean result = service.migrateTemplates(study);
        assertFalse(result);
        
        verify(mockTemplateService, never()).createTemplate(any(), any());
        verify(mockTemplateRevisionService, never()).createTemplateRevision(any(), any(), any());
    }
    
    @Test
    public void unmigratedStudyMigrated() throws Exception {
        mockNoTemplates();
        
        study.setIdentifier(TEST_STUDY_IDENTIFIER);
        study.setVerifyEmailTemplate( emailTemplate(EMAIL_VERIFY_EMAIL) );
        study.setResetPasswordTemplate( emailTemplate(EMAIL_RESET_PASSWORD) );
        study.setEmailSignInTemplate( emailTemplate(EMAIL_SIGN_IN) );
        study.setAccountExistsTemplate( emailTemplate(EMAIL_ACCOUNT_EXISTS) );
        study.setSignedConsentTemplate( emailTemplate(EMAIL_SIGNED_CONSENT) );
        study.setAppInstallLinkTemplate( emailTemplate(EMAIL_APP_INSTALL_LINK) );
        study.setResetPasswordSmsTemplate( smsTemplate(SMS_RESET_PASSWORD) );
        study.setPhoneSignInSmsTemplate( smsTemplate(SMS_PHONE_SIGN_IN) );
        study.setAppInstallLinkSmsTemplate( smsTemplate(SMS_APP_INSTALL_LINK) );
        study.setVerifyPhoneSmsTemplate( smsTemplate(SMS_VERIFY_PHONE) );
        study.setAccountExistsSmsTemplate( smsTemplate(SMS_ACCOUNT_EXISTS) );
        study.setSignedConsentSmsTemplate( smsTemplate(SMS_SIGNED_CONSENT) );
        
        boolean result = service.migrateTemplates(study);
        assertTrue(result);
        
        MapDifference<String, String> diff = Maps.difference(createMigratedStudy().getDefaultTemplates(), study.getDefaultTemplates());
        assertTrue(diff.areEqual());
        
        verify(mockTemplateService, times(12)).migrateTemplate(eq(study), templateCaptor.capture(), revisionCaptor.capture());
        for (TemplateRevision revision : revisionCaptor.getAllValues()) {
            assertTrue(revision.getDocumentContent().contains("from study"));
        }
    }
    
    @Test
    public void newStudySetsNewDefaultTemplates() throws Exception {
        mockNoTemplates();
        
        Study study = createNewStudy();
        
        GuidVersionHolder keys = new GuidVersionHolder("guid", 1L);
        when(mockTemplateService.createTemplate(any(), any())).thenReturn(keys);
        
        boolean result = service.migrateTemplates(study);
        assertTrue(result);
        
        verify(mockTemplateService, times(12)).createTemplate(eq(study), templateCaptor.capture());
        for (TemplateRevision revision : revisionCaptor.getAllValues()) {
            assertTrue(revision.getDocumentContent().contains("from service"));
        }
    }
    
    @Test
    public void newStudyAdoptsValidTemplates() throws Exception {
        mockAllServicesWorking();
        
        Study study = createNewStudy();
        
        boolean result = service.migrateTemplates(study);
        assertTrue(result);

        verify(mockTemplateService, never()).createTemplate(any(), any());
        verify(mockTemplateRevisionService, never()).createTemplateRevision(any(), any(), any());
        MapDifference<String, String> diff = Maps.difference(createMigratedStudy().getDefaultTemplates(), study.getDefaultTemplates());
        assertTrue(diff.areEqual());
    }
    
    @Test
    public void defaultTemplatesGuidMissing() throws Exception {
        mockAllServicesWorking();
        Study study = createMigratedStudy();
        
        // remove two, this should be repaired
        study.getDefaultTemplates().remove(EMAIL_RESET_PASSWORD.name().toLowerCase());
        study.getDefaultTemplates().remove(SMS_APP_INSTALL_LINK.name().toLowerCase());
        assertEquals(study.getDefaultTemplates().size(), 10);
        
        boolean result = service.migrateTemplates(study);
        assertTrue(result);
        
        MapDifference<String, String> diff = Maps.difference(createMigratedStudy().getDefaultTemplates(), study.getDefaultTemplates());
        assertTrue(diff.areEqual());
    }
    
    // Since the migration method does so many substeps, each with many error conditions, it's worth 
    // testing the protected methods individually
    
    @Test
    public void findValidTemplateGuid() {
        Template template = Template.create();
        template.setGuid(TEMPLATE_GUID);
        template.setPublishedCreatedOn(CREATED_ON);
        List<? extends Template> list = ImmutableList.of(template);
        PagedResourceList<? extends Template> page = new PagedResourceList<>(list, 1);
        doReturn(page).when(mockTemplateService).getTemplatesForType(TEST_STUDY, SMS_VERIFY_PHONE, 0, 50, false);
        
        String result = service.findValidTemplateGuid(TEST_STUDY, SMS_VERIFY_PHONE);
        assertEquals(result, TEMPLATE_GUID);
    }
    
    @Test
    public void findValidTemplateGuidNoPublishedRevision() {
        Template template = Template.create();
        template.setGuid(TEMPLATE_GUID);
        List<? extends Template> list = ImmutableList.of(template);
        PagedResourceList<? extends Template> page = new PagedResourceList<>(list, 1);
        doReturn(page).when(mockTemplateService).getTemplatesForType(TEST_STUDY, SMS_VERIFY_PHONE, 0, 50, false);
        
        String result = service.findValidTemplateGuid(TEST_STUDY, SMS_VERIFY_PHONE);
        assertNull(result);
    }
    
    @Test
    public void hasValidTemplate() {
        Template template = Template.create();
        template.setPublishedCreatedOn(CREATED_ON);
        when(mockTemplateService.getTemplate(TEST_STUDY, TEMPLATE_GUID)).thenReturn(template);
        
        assertTrue(service.hasValidTemplate(TEST_STUDY, TEMPLATE_GUID));
    }

    @Test
    public void hasValidTemplateNoTemplateGuid() {
        assertFalse(service.hasValidTemplate(TEST_STUDY, null));
    }
    
    @Test
    public void hasValidTemplateNoTemplate() {
        when(mockTemplateService.getTemplate(TEST_STUDY, TEMPLATE_GUID))
                .thenThrow(new EntityNotFoundException(Template.class));
        
        assertFalse(service.hasValidTemplate(TEST_STUDY, TEMPLATE_GUID));
    }
    
    @Test
    public void hasValidTemplateNoPublishedRevision() {
        Template template = Template.create();
        when(mockTemplateService.getTemplate(TEST_STUDY, TEMPLATE_GUID)).thenReturn(template);
        
        assertFalse(service.hasValidTemplate(TEST_STUDY, TEMPLATE_GUID));
    }
    
    @Test
    public void migrateExistingTemplate() { 
        Map<String, String> map = new HashMap<>();
        study.setIdentifier(TEST_STUDY_IDENTIFIER);
        study.setVerifyPhoneSmsTemplate(new SmsTemplate("One message"));
        
        service.migrateExistingTemplate(study, map, SMS_VERIFY_PHONE);
        assertEquals(map.get(SMS_VERIFY_PHONE.name().toLowerCase()), TEMPLATE_GUID);
        
        verify(mockTemplateService).migrateTemplate(eq(study), templateCaptor.capture(), revisionCaptor.capture());
        assertEquals(templateCaptor.getValue().getName(), "Verify Phone Default (SMS)");
        assertEquals(templateCaptor.getValue().getTemplateType(), SMS_VERIFY_PHONE);        

        assertEquals(revisionCaptor.getValue().getDocumentContent(), "One message");
        assertEquals(revisionCaptor.getValue().getMimeType(), TEXT);
    }
    
    @Test
    public void createNewTemplate() {
        Map<String, String> map = new HashMap<>();
        
        service.createNewTemplate(study, map, SMS_VERIFY_PHONE);
        assertEquals(map.get(SMS_VERIFY_PHONE.name().toLowerCase()), TEMPLATE_GUID);
        
        verify(mockTemplateService).createTemplate(eq(study), templateCaptor.capture());
        assertEquals(templateCaptor.getValue().getName(), "Verify Phone Default (SMS)");
        assertEquals(templateCaptor.getValue().getTemplateType(), SMS_VERIFY_PHONE);
    }
    
    @Test
    public void emailTemplateToRevision() {
        EmailTemplate template = new EmailTemplate("One subject", "One body", TEXT);
        
        TemplateRevision revision = TemplateMigrationService.emailTemplateToRevision(template);
        assertEquals(revision.getSubject(), "One subject");
        assertEquals(revision.getDocumentContent(), "One body");
        assertEquals(revision.getMimeType(), TEXT);        
    }
    
    @Test
    public void emailTemplateToRevisionNoTemplate() {
        assertNull(TemplateMigrationService.emailTemplateToRevision(null));
    }
    
    @Test
    public void smsTemplateToRevision() { 
        SmsTemplate template = new SmsTemplate("Message");
        
        TemplateRevision revision = TemplateMigrationService.smsTemplateToRevision(template);
        assertNull(revision.getSubject());
        assertEquals(revision.getDocumentContent(), "Message");
        assertEquals(revision.getMimeType(), TEXT);
    }
    
    @Test
    public void smsTemplateToRevisionNoTemplate() { 
        assertNull(TemplateMigrationService.smsTemplateToRevision(null));
    }
    
    @Test
    public void getRevisionFromStudy() { 
        Study study = Study.create();
        study.setResetPasswordTemplate(new EmailTemplate("Subject", "Body", HTML));
        TemplateRevision revision = TemplateMigrationService.getRevisionFromStudy(study, EMAIL_RESET_PASSWORD);
        
        assertEquals(revision.getSubject(), "Subject");
        assertEquals(revision.getDocumentContent(), "Body");
        assertEquals(revision.getMimeType(), HTML);
    }
    
    @Test
    public void getRevisionFromStudyEmailAccountExists() {
        Study study = Study.create();
        study.setAccountExistsTemplate(EMAIL_TEMPLATE);
        assertNotNull(TemplateMigrationService.getRevisionFromStudy(study, EMAIL_ACCOUNT_EXISTS));
    }
    
    @Test
    public void getRevisionFromStudyEmailAppInstallLink() {
        Study study = Study.create();
        study.setAppInstallLinkTemplate(EMAIL_TEMPLATE);
        assertNotNull(TemplateMigrationService.getRevisionFromStudy(study, EMAIL_APP_INSTALL_LINK));
    }

    @Test
    public void getRevisionFromStudyEmailResetPassword() {
        Study study = Study.create();
        study.setResetPasswordTemplate(EMAIL_TEMPLATE);
        assertNotNull(TemplateMigrationService.getRevisionFromStudy(study, EMAIL_RESET_PASSWORD));
    }
    
    @Test
    public void getRevisionFromStudyEmailSignIn() {
        Study study = Study.create();
        study.setEmailSignInTemplate(EMAIL_TEMPLATE);
        assertNotNull(TemplateMigrationService.getRevisionFromStudy(study, EMAIL_SIGN_IN));
    }

    @Test
    public void getRevisionFromStudyEmailSignedConsent() {
        Study study = Study.create();
        study.setSignedConsentTemplate(EMAIL_TEMPLATE);
        assertNotNull(TemplateMigrationService.getRevisionFromStudy(study, EMAIL_SIGNED_CONSENT));
    }

    @Test
    public void getRevisionFromStudyEmailVerifyEmail() {
        Study study = Study.create();
        study.setVerifyEmailTemplate(EMAIL_TEMPLATE);
        assertNotNull(TemplateMigrationService.getRevisionFromStudy(study, EMAIL_VERIFY_EMAIL));
    }
    
    @Test
    public void getRevisionFromStudySmsAcountExists() {
        Study study = Study.create();
        study.setAccountExistsSmsTemplate(SMS_TEMPLATE);
        assertNotNull(TemplateMigrationService.getRevisionFromStudy(study, SMS_ACCOUNT_EXISTS));
    }

    @Test
    public void getRevisionFromStudySmsAppInstallLink() {
        Study study = Study.create();
        study.setAppInstallLinkSmsTemplate(SMS_TEMPLATE);
        assertNotNull(TemplateMigrationService.getRevisionFromStudy(study, SMS_APP_INSTALL_LINK));
    }
    
    @Test
    public void getRevisionFromStudySmsPhoneSignIn() {
        Study study = Study.create();
        study.setPhoneSignInSmsTemplate(SMS_TEMPLATE);
        assertNotNull(TemplateMigrationService.getRevisionFromStudy(study, SMS_PHONE_SIGN_IN));
    }
    
    @Test
    public void getRevisionFromStudySmsResetPassword() {
        Study study = Study.create();
        study.setResetPasswordSmsTemplate(SMS_TEMPLATE);
        assertNotNull(TemplateMigrationService.getRevisionFromStudy(study, SMS_RESET_PASSWORD));
    }
    
    @Test
    public void getRevisionFromStudySmsSignedConsent() {
        Study study = Study.create();
        study.setSignedConsentSmsTemplate(SMS_TEMPLATE);
        assertNotNull(TemplateMigrationService.getRevisionFromStudy(study, SMS_SIGNED_CONSENT));
    }
    
    @Test
    public void getRevisionFromStudySmsVerifyPhone() {
        Study study = Study.create();
        study.setVerifyPhoneSmsTemplate(SMS_TEMPLATE);
        assertNotNull(TemplateMigrationService.getRevisionFromStudy(study, SMS_VERIFY_PHONE));
    }
    
    @Test
    public void getRevisionFromStudyTemplateMissing() {
        assertNull(TemplateMigrationService.getRevisionFromStudy(Study.create(), EMAIL_RESET_PASSWORD));
    }
}