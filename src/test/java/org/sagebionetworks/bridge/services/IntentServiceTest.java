package org.sagebionetworks.bridge.services;

import static org.mockito.Mockito.any;
import static org.mockito.Mockito.eq;
import static org.mockito.Mockito.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;
import static org.sagebionetworks.bridge.TestConstants.PHONE;
import static org.sagebionetworks.bridge.TestConstants.TEST_APP_ID;
import static org.sagebionetworks.bridge.models.apps.MimeType.HTML;
import static org.sagebionetworks.bridge.models.apps.MimeType.TEXT;
import static org.sagebionetworks.bridge.models.templates.TemplateType.EMAIL_APP_INSTALL_LINK;
import static org.sagebionetworks.bridge.models.templates.TemplateType.SMS_APP_INSTALL_LINK;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertNull;

import java.util.Map;

import javax.mail.internet.MimeBodyPart;

import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import org.sagebionetworks.bridge.TestConstants;
import org.sagebionetworks.bridge.TestUtils;
import org.sagebionetworks.bridge.cache.CacheProvider;
import org.sagebionetworks.bridge.cache.CacheKey;
import org.sagebionetworks.bridge.exceptions.InvalidEntityException;
import org.sagebionetworks.bridge.models.accounts.Account;
import org.sagebionetworks.bridge.models.accounts.AccountId;
import org.sagebionetworks.bridge.models.accounts.SharingScope;
import org.sagebionetworks.bridge.models.accounts.StudyParticipant;
import org.sagebionetworks.bridge.models.apps.App;
import org.sagebionetworks.bridge.models.itp.IntentToParticipate;
import org.sagebionetworks.bridge.models.subpopulations.ConsentSignature;
import org.sagebionetworks.bridge.models.subpopulations.Subpopulation;
import org.sagebionetworks.bridge.models.subpopulations.SubpopulationGuid;
import org.sagebionetworks.bridge.models.templates.TemplateRevision;
import org.sagebionetworks.bridge.services.email.BasicEmailProvider;
import org.sagebionetworks.bridge.services.email.EmailType;
import org.sagebionetworks.bridge.services.email.MimeTypeEmail;
import org.sagebionetworks.bridge.services.email.MimeTypeEmailProvider;
import org.sagebionetworks.bridge.sms.SmsMessageProvider;

import com.google.common.collect.Iterables;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;

public class IntentServiceTest {

    private static final long TIMESTAMP = 1000L; 
    
    @InjectMocks
    IntentService service;

    @Mock
    SmsService mockSmsService;
    
    @Mock
    SendMailService mockSendMailService;

    @Mock
    AppService mockAppService;
    
    @Mock
    SubpopulationService mockSubpopService;
    
    @Mock
    ConsentService mockConsentService;
    
    @Mock
    CacheProvider mockCacheProvider;

    @Mock
    ParticipantService mockParticipantService;
    
    @Mock
    App mockApp;
    
    @Mock
    AccountService mockAccountService;
    
    @Mock
    TemplateService mockTemplateService;
    
    @Captor
    ArgumentCaptor<SubpopulationGuid> subpopGuidCaptor;

    @Captor
    ArgumentCaptor<CacheKey> keyCaptor;

    @Captor
    ArgumentCaptor<SmsMessageProvider> smsMessageProviderCaptor;
    
    @Captor
    ArgumentCaptor<MimeTypeEmailProvider> mimeTypeEmailProviderCaptor;
    
    @BeforeMethod
    public void before() {
        MockitoAnnotations.initMocks(this);
    }
    
    @Test
    public void submitIntentToParticipateWithPhone() {
        IntentToParticipate intent = TestUtils.getIntentToParticipate(TIMESTAMP).build();
        
        Map<String,String> installLinks = Maps.newHashMap();
        installLinks.put("Android", "this-is-a-link");
        
        when(mockApp.getIdentifier()).thenReturn(TEST_APP_ID);
        when(mockApp.getInstallLinks()).thenReturn(installLinks);
        when(mockAppService.getApp(intent.getAppId())).thenReturn(mockApp);
        
        TemplateRevision revision = TemplateRevision.create();
        revision.setDocumentContent("this-is-a-link");
        revision.setMimeType(TEXT);
        when(mockTemplateService.getRevisionForUser(mockApp, SMS_APP_INSTALL_LINK)).thenReturn(revision);
        
        CacheKey cacheKey = CacheKey.itp(SubpopulationGuid.create("subpopGuid"), TEST_APP_ID,
                TestConstants.PHONE);
        
        service.submitIntentToParticipate(intent);
        
        verify(mockSubpopService).getSubpopulation(eq(TEST_APP_ID), subpopGuidCaptor.capture());
        assertEquals(subpopGuidCaptor.getValue().getGuid(), intent.getSubpopGuid());
        
        verify(mockCacheProvider).setObject(keyCaptor.capture(), eq(intent), eq(4 * 60 * 60));
        assertEquals(keyCaptor.getValue(), cacheKey);

        verify(mockSmsService).sendSmsMessage(isNull(), smsMessageProviderCaptor.capture());

        SmsMessageProvider provider = smsMessageProviderCaptor.getValue();
        assertEquals(provider.getApp(), mockApp);
        assertEquals(provider.getPhone(), intent.getPhone());
        assertEquals(provider.getSmsRequest().getMessage(), "this-is-a-link");
        assertEquals(provider.getSmsType(), "Transactional");
    }
    
    // In this case when there isn't an install link for the OS or that is marked
    // "Universal," we take anything.
    @Test
    public void submitIntentToParticipateWithMismatchedInstallLinks() {
        IntentToParticipate intent = TestUtils.getIntentToParticipate(TIMESTAMP).build();
        
        Map<String,String> installLinks = Maps.newHashMap();
        installLinks.put("Android", "this-is-a-link");
        
        when(mockApp.getIdentifier()).thenReturn(TEST_APP_ID);
        when(mockApp.getInstallLinks()).thenReturn(installLinks);
        when(mockAppService.getApp(intent.getAppId())).thenReturn(mockApp);
        
        TemplateRevision revision = TemplateRevision.create();
        revision.setDocumentContent("this-is-a-link");
        revision.setMimeType(TEXT);
        when(mockTemplateService.getRevisionForUser(mockApp, SMS_APP_INSTALL_LINK)).thenReturn(revision);
        
        CacheKey cacheKey = CacheKey.itp(SubpopulationGuid.create("subpopGuid"), TEST_APP_ID,
                TestConstants.PHONE);
        
        service.submitIntentToParticipate(intent);
        
        verify(mockSubpopService).getSubpopulation(eq(TEST_APP_ID), subpopGuidCaptor.capture());
        assertEquals(subpopGuidCaptor.getValue().getGuid(), intent.getSubpopGuid());
        
        verify(mockCacheProvider).setObject(keyCaptor.capture(), eq(intent), eq(4 * 60 * 60));
        assertEquals(keyCaptor.getValue(), cacheKey);

        verify(mockSmsService).sendSmsMessage(isNull(), smsMessageProviderCaptor.capture());

        SmsMessageProvider provider = smsMessageProviderCaptor.getValue();
        assertEquals(provider.getApp(), mockApp);
        assertEquals(provider.getPhone(), intent.getPhone());
        assertEquals(provider.getSmsRequest().getMessage(), "this-is-a-link");
        assertEquals(provider.getSmsType(), "Transactional");        
    }
    
    @Test
    public void submitIntentToParticipateWithEmail() throws Exception {
        IntentToParticipate intent = TestUtils.getIntentToParticipate(TIMESTAMP).withPhone(null)
                .withOsName("iOS").withEmail("email@email.com").build();        
        
        Map<String,String> installLinks = Maps.newHashMap();
        installLinks.put("iOS", "this-is-a-link");
        installLinks.put("Android", "the-wrong-link");
        
        when(mockApp.getIdentifier()).thenReturn(TEST_APP_ID);
        when(mockApp.getInstallLinks()).thenReturn(installLinks);
        when(mockAppService.getApp(intent.getAppId())).thenReturn(mockApp);
        
        TemplateRevision revision = TemplateRevision.create();
        revision.setSubject("subject");
        revision.setDocumentContent("body ${appInstallUrl}");
        revision.setMimeType(HTML);
        when(mockTemplateService.getRevisionForUser(mockApp, EMAIL_APP_INSTALL_LINK)).thenReturn(revision);

        CacheKey cacheKey = CacheKey.itp(SubpopulationGuid.create("subpopGuid"), 
                TEST_APP_ID, "email@email.com");
        
        service.submitIntentToParticipate(intent);
        
        verify(mockSubpopService).getSubpopulation(eq(TEST_APP_ID), subpopGuidCaptor.capture());
        assertEquals(subpopGuidCaptor.getValue().getGuid(), intent.getSubpopGuid());
        
        verify(mockCacheProvider).setObject(keyCaptor.capture(), eq(intent), eq(4 * 60 * 60));
        assertEquals(keyCaptor.getValue(), cacheKey);

        verify(mockSendMailService).sendEmail(mimeTypeEmailProviderCaptor.capture());

        BasicEmailProvider provider = (BasicEmailProvider)mimeTypeEmailProviderCaptor.getValue();
        assertEquals(provider.getApp(), mockApp);
        assertEquals(Iterables.getFirst(provider.getRecipientEmails(), null), "email@email.com");
        assertEquals(provider.getType(), EmailType.APP_INSTALL);
        
        MimeTypeEmail email = provider.getMimeTypeEmail();
        assertEquals(email.getSenderAddress(), "\"null\" <null>");
        assertEquals(email.getSubject(), "subject");
        MimeBodyPart body = email.getMessageParts().get(0);
        assertEquals(body.getContent(), "body this-is-a-link");
        assertEquals(email.getRecipientAddresses().get(0), "email@email.com");
        assertEquals(email.getRecipientAddresses().size(), 1);
        assertEquals(email.getType(), EmailType.APP_INSTALL);
    }    
    
    @Test(expectedExceptions = InvalidEntityException.class)
    public void submitIntentToParticipateInvalid() {
        IntentToParticipate intent = TestUtils.getIntentToParticipate(TIMESTAMP).build();
        IntentToParticipate invalid = new IntentToParticipate.Builder().copyOf(intent).withPhone(null).build();
        
        service.submitIntentToParticipate(invalid);
    }
    
    @Test
    public void submitIntentToParticipateExists() {
        IntentToParticipate intent = TestUtils.getIntentToParticipate(TIMESTAMP).build();
        
        Map<String,String> installLinks = Maps.newHashMap();
        installLinks.put("Android", "this-is-a-link");
        
        CacheKey cacheKey = CacheKey.itp(SubpopulationGuid.create("subpopGuid"), TEST_APP_ID,
                TestConstants.PHONE);
        
        when(mockApp.getIdentifier()).thenReturn(TEST_APP_ID);
        when(mockAppService.getApp(intent.getAppId())).thenReturn(mockApp);
        when(mockCacheProvider.getObject(cacheKey, IntentToParticipate.class))
                .thenReturn(intent);

        service.submitIntentToParticipate(intent);
        
        verify(mockSubpopService).getSubpopulation(eq(TEST_APP_ID), subpopGuidCaptor.capture());
        assertEquals(subpopGuidCaptor.getValue().getGuid(), intent.getSubpopGuid());

        // These are not called.
        verify(mockCacheProvider, never()).setObject(cacheKey, intent, (4 * 60 * 60));
        verify(mockSmsService, never()).sendSmsMessage(any(), any());
    }
    
    @Test
    public void submitIntentToParticipateAccountExists() {
        IntentToParticipate intent = TestUtils.getIntentToParticipate(TIMESTAMP).build();
        
        AccountId accountId = AccountId.forPhone(TEST_APP_ID, intent.getPhone()); 
        
        Account account = Account.create();
        when(mockAccountService.getAccount(accountId)).thenReturn(account);
        
        service.submitIntentToParticipate(intent);
        
        // None of this happens...
        verifyNoMoreInteractions(mockAppService);
        verifyNoMoreInteractions(mockSubpopService);
        verifyNoMoreInteractions(mockCacheProvider);
        verifyNoMoreInteractions(mockSmsService);
    }

    @Test
    public void registerIntentToParticipateWithPhone() {
        Subpopulation subpopA = Subpopulation.create();
        subpopA.setGuidString("AAA");
        Subpopulation subpopB = Subpopulation.create();
        subpopB.setGuidString("BBB");
        
        IntentToParticipate intent = new IntentToParticipate.Builder()
                .withOsName("Android")
                .withPhone(TestConstants.PHONE)
                .withScope(SharingScope.NO_SHARING)
                .withAppId(TEST_APP_ID)
                .withSubpopGuid("BBB")
                .withConsentSignature(new ConsentSignature.Builder()
                        .withName("Test Name")
                        .withBirthdate("1975-01-01")
                        .build())
                .build();
        
        Account account = Account.create();
        account.setPhone(TestConstants.PHONE);
        
        CacheKey key = CacheKey.itp(SubpopulationGuid.create("BBB"), TEST_APP_ID, TestConstants.PHONE);
        
        when(mockApp.getIdentifier()).thenReturn(TEST_APP_ID);
        when(mockSubpopService.getSubpopulations(TEST_APP_ID, false))
                .thenReturn(Lists.newArrayList(subpopA, subpopB));
        when(mockCacheProvider.getObject(key, IntentToParticipate.class)).thenReturn(intent);
        
        service.registerIntentToParticipate(mockApp, account);
        
        verify(mockSubpopService).getSubpopulations(TEST_APP_ID, false);
        verify(mockCacheProvider).removeObject(key);
        verify(mockConsentService).consentToResearch(eq(mockApp), eq(SubpopulationGuid.create("BBB")), 
                any(), eq(intent.getConsentSignature()), eq(intent.getScope()), eq(true));
    }

    @Test
    public void registerIntentToParticipateWithEmail() {
        Subpopulation subpopA = Subpopulation.create();
        subpopA.setGuidString("AAA");
        Subpopulation subpopB = Subpopulation.create();
        subpopB.setGuidString("BBB");
        
        IntentToParticipate intent = new IntentToParticipate.Builder()
                .withOsName("Android")
                .withEmail(TestConstants.EMAIL)
                .withScope(SharingScope.NO_SHARING)
                .withAppId(TEST_APP_ID)
                .withSubpopGuid("BBB")
                .withConsentSignature(new ConsentSignature.Builder()
                        .withName("Test Name")
                        .withBirthdate("1975-01-01")
                        .build())
                .build();
        
        Account account = Account.create();
        account.setEmail(TestConstants.EMAIL);
        
        CacheKey key = CacheKey.itp(SubpopulationGuid.create("BBB"), TEST_APP_ID, TestConstants.EMAIL);
        
        when(mockApp.getIdentifier()).thenReturn(TEST_APP_ID);
        when(mockSubpopService.getSubpopulations(TEST_APP_ID, false))
                .thenReturn(Lists.newArrayList(subpopA, subpopB));
        when(mockCacheProvider.getObject(key, IntentToParticipate.class)).thenReturn(intent);
        
        service.registerIntentToParticipate(mockApp, account);
        
        verify(mockSubpopService).getSubpopulations(TEST_APP_ID, false);
        verify(mockCacheProvider).removeObject(key);
        verify(mockConsentService).consentToResearch(eq(mockApp), eq(SubpopulationGuid.create("BBB")), 
                any(), eq(intent.getConsentSignature()), eq(intent.getScope()), eq(true));
    }
    
    @Test
    public void registerIntentToParticipateWithMultipleConsents() {
        Subpopulation subpopA = Subpopulation.create();
        subpopA.setGuidString("AAA");
        Subpopulation subpopB = Subpopulation.create();
        subpopB.setGuidString("BBB");
        
        IntentToParticipate intentAAA = new IntentToParticipate.Builder()
                .withOsName("Android")
                .withPhone(TestConstants.PHONE)
                .withScope(SharingScope.NO_SHARING)
                .withAppId(TEST_APP_ID)
                .withSubpopGuid("AAA")
                .withConsentSignature(new ConsentSignature.Builder()
                        .withName("Test Name")
                        .withBirthdate("1975-01-01")
                        .build())
                .build();
        
        IntentToParticipate intentBBB = new IntentToParticipate.Builder()
                .withOsName("Android")
                .withPhone(TestConstants.PHONE)
                .withScope(SharingScope.NO_SHARING)
                .withAppId(TEST_APP_ID)
                .withSubpopGuid("BBB")
                .withConsentSignature(new ConsentSignature.Builder()
                        .withName("Test Name")
                        .withBirthdate("1975-01-01")
                        .build())
                .build();
        
        Account account = Account.create();
        account.setId("id");
        account.setPhone(TestConstants.PHONE);
        
        StudyParticipant participant = new StudyParticipant.Builder().build(); 
        
        CacheKey keyAAA = CacheKey.itp(SubpopulationGuid.create("AAA"), TEST_APP_ID, TestConstants.PHONE);
        CacheKey keyBBB = CacheKey.itp(SubpopulationGuid.create("BBB"), TEST_APP_ID, TestConstants.PHONE);
        
        when(mockApp.getIdentifier()).thenReturn(TEST_APP_ID);
        when(mockSubpopService.getSubpopulations(TEST_APP_ID, false))
                .thenReturn(Lists.newArrayList(subpopA, subpopB));
        when(mockCacheProvider.getObject(keyAAA, IntentToParticipate.class)).thenReturn(intentAAA);
        when(mockCacheProvider.getObject(keyBBB, IntentToParticipate.class)).thenReturn(intentBBB);
        when(mockParticipantService.getParticipant(mockApp, "id", true)).thenReturn(participant);
        
        service.registerIntentToParticipate(mockApp, account);
        
        verify(mockSubpopService).getSubpopulations(TEST_APP_ID, false);
        verify(mockCacheProvider).removeObject(keyAAA);
        verify(mockCacheProvider).removeObject(keyBBB);
        verify(mockConsentService).consentToResearch(eq(mockApp), eq(SubpopulationGuid.create("AAA")), 
                any(), eq(intentAAA.getConsentSignature()), eq(intentAAA.getScope()), eq(true));
        verify(mockConsentService).consentToResearch(eq(mockApp), eq(SubpopulationGuid.create("BBB")), 
                any(), eq(intentBBB.getConsentSignature()), eq(intentBBB.getScope()), eq(true));
        // Only loaded the participant once...
        verify(mockParticipantService, times(1)).getParticipant(mockApp, "id", true);
    }
    
    @Test
    public void noPhoneDoesNothing() {
        Account account = Account.create();
        
        service.registerIntentToParticipate(mockApp, account);
        
        verifyNoMoreInteractions(mockSubpopService);
        verifyNoMoreInteractions(mockCacheProvider);
        verifyNoMoreInteractions(mockConsentService); 
    }
    
    @Test
    public void noIntentDoesNothing() {
        Subpopulation subpopA = Subpopulation.create();
        subpopA.setGuidString("AAA");
        Subpopulation subpopB = Subpopulation.create();
        subpopB.setGuidString("BBB");
        
        Account account = Account.create();
        account.setPhone(TestConstants.PHONE);
        
        CacheKey key = CacheKey.itp(SubpopulationGuid.create("BBB"), TEST_APP_ID, PHONE);
        
        when(mockApp.getIdentifier()).thenReturn(TEST_APP_ID);
        when(mockSubpopService.getSubpopulations(TEST_APP_ID, false))
                .thenReturn(Lists.newArrayList(subpopA, subpopB));
        
        service.registerIntentToParticipate(mockApp, account);
        
        verify(mockSubpopService).getSubpopulations(TEST_APP_ID, false);
        verify(mockCacheProvider, never()).removeObject(key);
        verifyNoMoreInteractions(mockConsentService); 
    }
    
    @Test
    public void submitIntentToParticipateWithoutInstallLinks() {
        IntentToParticipate intent = TestUtils.getIntentToParticipate(TIMESTAMP).build();
        
        when(mockApp.getIdentifier()).thenReturn(TEST_APP_ID);
        when(mockAppService.getApp(intent.getAppId())).thenReturn(mockApp);
        
        CacheKey cacheKey = CacheKey.itp(SubpopulationGuid.create("subpopGuid"), TEST_APP_ID, PHONE);
        
        service.submitIntentToParticipate(intent);
        
        verify(mockSubpopService).getSubpopulation(eq(TEST_APP_ID), subpopGuidCaptor.capture());
        assertEquals(subpopGuidCaptor.getValue().getGuid(), intent.getSubpopGuid());
        
        // We do store the intent
        verify(mockCacheProvider).setObject(keyCaptor.capture(), eq(intent), eq(4 * 60 * 60));
        assertEquals(keyCaptor.getValue(), cacheKey);

        // But we don't send a message because installLinks map is empty
        verify(mockSmsService, never()).sendSmsMessage(any(), any());
    }    
    
    @Test
    public void installLinkCorrectlySelected() {
        Map<String,String> installLinks = Maps.newHashMap();
        installLinks.put("iPhone OS", "iphone-os-link");
        
        // Lacking android or universal, find the only link that's there.
        assertEquals(service.getInstallLink("Android", installLinks), "iphone-os-link");
        
        installLinks.put("Universal", "universal-link");
        assertEquals(service.getInstallLink("iPhone OS", installLinks), "iphone-os-link");
        assertEquals(service.getInstallLink("Android", installLinks), "universal-link");
        
        Map<String,String> emptyInstallLinks = Maps.newHashMap();
        assertNull(service.getInstallLink("iPhone OS", emptyInstallLinks));
    }

}
