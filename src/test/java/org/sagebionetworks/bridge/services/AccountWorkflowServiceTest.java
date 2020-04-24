package org.sagebionetworks.bridge.services;

import static java.lang.Boolean.TRUE;
import static org.sagebionetworks.bridge.RequestContext.NULL_INSTANCE;
import static org.sagebionetworks.bridge.TestConstants.ACCOUNT_ID;
import static org.sagebionetworks.bridge.TestConstants.TEST_APP_ID;
import static org.sagebionetworks.bridge.TestConstants.TIMESTAMP;
import static org.sagebionetworks.bridge.TestUtils.createJson;
import static org.sagebionetworks.bridge.models.studies.MimeType.TEXT;
import static org.sagebionetworks.bridge.models.templates.TemplateType.EMAIL_ACCOUNT_EXISTS;
import static org.sagebionetworks.bridge.models.templates.TemplateType.EMAIL_RESET_PASSWORD;
import static org.sagebionetworks.bridge.models.templates.TemplateType.EMAIL_SIGN_IN;
import static org.sagebionetworks.bridge.models.templates.TemplateType.EMAIL_VERIFY_EMAIL;
import static org.sagebionetworks.bridge.models.templates.TemplateType.SMS_ACCOUNT_EXISTS;
import static org.sagebionetworks.bridge.models.templates.TemplateType.SMS_PHONE_SIGN_IN;
import static org.sagebionetworks.bridge.models.templates.TemplateType.SMS_RESET_PASSWORD;
import static org.sagebionetworks.bridge.models.templates.TemplateType.SMS_VERIFY_PHONE;
import static org.sagebionetworks.bridge.services.AccountWorkflowService.CONFIG_KEY_CHANNEL_THROTTLE_MAX_REQUESTS;
import static org.sagebionetworks.bridge.services.AccountWorkflowService.CONFIG_KEY_CHANNEL_THROTTLE_TIMEOUT_SECONDS;
import static org.sagebionetworks.bridge.services.AccountWorkflowService.SIGNIN_EXPIRE_IN_SECONDS;
import static org.sagebionetworks.bridge.services.AccountWorkflowService.VERIFY_CACHE_IN_SECONDS;
import static org.sagebionetworks.bridge.services.AccountWorkflowService.VERIFY_OR_RESET_EXPIRE_IN_SECONDS;
import static org.sagebionetworks.bridge.services.AccountWorkflowService.VERIFY_TOKEN_EXPIRED;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertNotNull;
import static org.testng.Assert.assertNull;
import static org.testng.Assert.assertTrue;
import static org.testng.Assert.fail;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.mail.internet.MimeBodyPart;

import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;
import org.mockito.Spy;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import org.sagebionetworks.bridge.BridgeUtils;
import org.sagebionetworks.bridge.TestConstants;
import org.sagebionetworks.bridge.TestUtils;
import org.sagebionetworks.bridge.cache.CacheKey;
import org.sagebionetworks.bridge.cache.CacheProvider;
import org.sagebionetworks.bridge.config.BridgeConfig;
import org.sagebionetworks.bridge.exceptions.BadRequestException;
import org.sagebionetworks.bridge.exceptions.EntityNotFoundException;
import org.sagebionetworks.bridge.exceptions.InvalidEntityException;
import org.sagebionetworks.bridge.exceptions.UnauthorizedException;
import org.sagebionetworks.bridge.json.BridgeObjectMapper;
import org.sagebionetworks.bridge.models.ThrottleRequestType;
import org.sagebionetworks.bridge.models.accounts.Account;
import org.sagebionetworks.bridge.models.accounts.AccountId;
import org.sagebionetworks.bridge.models.accounts.AccountStatus;
import org.sagebionetworks.bridge.models.accounts.PasswordReset;
import org.sagebionetworks.bridge.models.accounts.Phone;
import org.sagebionetworks.bridge.models.accounts.SignIn;
import org.sagebionetworks.bridge.models.accounts.Verification;
import org.sagebionetworks.bridge.models.studies.MimeType;
import org.sagebionetworks.bridge.models.studies.App;
import org.sagebionetworks.bridge.models.templates.TemplateRevision;
import org.sagebionetworks.bridge.models.templates.TemplateType;
import org.sagebionetworks.bridge.services.AuthenticationService.ChannelType;
import org.sagebionetworks.bridge.services.email.BasicEmailProvider;
import org.sagebionetworks.bridge.services.email.EmailType;
import org.sagebionetworks.bridge.services.email.MimeTypeEmail;
import org.sagebionetworks.bridge.sms.SmsMessageProvider;

import com.fasterxml.jackson.databind.JsonNode;
import com.google.common.collect.Iterables;

public class AccountWorkflowServiceTest extends Mockito {
    private static final String SUPPORT_EMAIL = "support@support.com";
    private static final String SPTOKEN = "GHI-JKL";
    private static final String USER_ID = "userId";
    private static final String EMAIL = "email@email.com";
    private static final String TOKEN = "ABCDEF";
    private static final String PHONE_TOKEN = "012345";
    
    private static final AccountId ACCOUNT_ID_WITH_ID = AccountId.forId(TEST_APP_ID, USER_ID);
    private static final AccountId ACCOUNT_ID_WITH_EMAIL = AccountId.forEmail(TEST_APP_ID, EMAIL);
    private static final AccountId ACCOUNT_ID_WITH_PHONE = AccountId.forPhone(TEST_APP_ID, TestConstants.PHONE);
    private static final SignIn SIGN_IN_REQUEST_WITH_PHONE = new SignIn.Builder().withStudy(TEST_APP_ID)
            .withPhone(TestConstants.PHONE).build();
    private static final SignIn SIGN_IN_REQUEST_WITH_EMAIL = new SignIn.Builder().withStudy(TEST_APP_ID)
            .withEmail(EMAIL).build();
    private static final SignIn SIGN_IN_WITH_PHONE = new SignIn.Builder().withStudy(TEST_APP_ID)
            .withPhone(TestConstants.PHONE).withToken(TOKEN).build();
    private static final SignIn SIGN_IN_WITH_EMAIL = new SignIn.Builder().withEmail(EMAIL).withStudy(TEST_APP_ID)
            .withToken(TOKEN).build();

    private static final CacheKey PHONE_TOKEN_CACHE_KEY = CacheKey.verificationToken(PHONE_TOKEN);
    private static final CacheKey TOKEN_CACHE_KEY = CacheKey.verificationToken(TOKEN);
    private static final CacheKey SPTOKEN_CACHE_KEY = CacheKey.verificationToken(SPTOKEN);
    
    private static final CacheKey EMAIL_SIGNIN_CACHE_KEY = CacheKey.emailSignInRequest(SIGN_IN_WITH_EMAIL);
    private static final CacheKey PHONE_SIGNIN_CACHE_KEY = CacheKey.phoneSignInRequest(SIGN_IN_WITH_PHONE);
    private static final CacheKey PASSWORD_RESET_FOR_EMAIL = CacheKey.passwordResetForEmail(SPTOKEN, TEST_APP_ID);
    private static final CacheKey PASSWORD_RESET_FOR_PHONE = CacheKey.passwordResetForPhone(SPTOKEN, TEST_APP_ID);

    private static final CacheKey EMAIL_SIGNIN_THROTTLE_CACHE_KEY = CacheKey.channelThrottling(
            ThrottleRequestType.EMAIL_SIGNIN, USER_ID);
    private static final CacheKey PHONE_SIGNIN_THROTTLE_CACHE_KEY = CacheKey.channelThrottling(
            ThrottleRequestType.PHONE_SIGNIN, USER_ID);
    private static final CacheKey VERIFY_EMAIL_THROTTLE_CACHE_KEY = CacheKey.channelThrottling(
            ThrottleRequestType.VERIFY_EMAIL, USER_ID);
    private static final CacheKey VERIFY_PHONE_THROTTLE_CACHE_KEY = CacheKey.channelThrottling(
            ThrottleRequestType.VERIFY_PHONE, USER_ID);

    @Mock
    private BridgeConfig mockBridgeConfig;

    @Mock
    private SmsService mockSmsService;

    @Mock
    private StudyService mockStudyService;
    
    @Mock
    private SendMailService mockSendMailService;

    @Mock
    private AccountService mockAccountService;
    
    @Mock
    private CacheProvider mockCacheProvider;
    
    @Mock
    private TemplateService mockTemplateService;
    
    @Mock
    private Account mockAccount;
    
    @Captor
    private ArgumentCaptor<BasicEmailProvider> emailProviderCaptor;

    @Captor
    private ArgumentCaptor<CacheKey> keyCaptor;
    
    @Captor
    private ArgumentCaptor<String> stringCaptor;

    @Captor
    private ArgumentCaptor<SmsMessageProvider> smsMessageProviderCaptor;
    
    private App app;
    
    @Spy
    @InjectMocks
    private AccountWorkflowService service;

    private Map<String, Object> mockCacheProviderMap;

    @SuppressWarnings("unchecked")
    @BeforeMethod
    public void before() {
        MockitoAnnotations.initMocks(this);
        
        mockRevision(EMAIL_VERIFY_EMAIL, "VE ${studyName}", "Body ${url} ${emailVerificationUrl}", TEXT);
        mockRevision(EMAIL_RESET_PASSWORD, "RP ${studyName}", "Body ${url} ${resetPasswordUrl}", TEXT);
        mockRevision(EMAIL_ACCOUNT_EXISTS, "AE ${studyName}", "Body ${url} ${resetPasswordUrl} ${emailSignInUrl}",
                TEXT);
        mockRevision(EMAIL_SIGN_IN, "subject", "Body ${token}", TEXT);
        mockRevision(SMS_PHONE_SIGN_IN, null, "Enter ${token} to sign in to ${studyShortName}", TEXT);
        mockRevision(SMS_RESET_PASSWORD, null, "Reset ${studyShortName} password: ${resetPasswordUrl}", TEXT);
        mockRevision(SMS_ACCOUNT_EXISTS, null,
                "Account for ${studyShortName} already exists. Reset password: ${resetPasswordUrl} or ${token}", TEXT);
        mockRevision(SMS_VERIFY_PHONE, null, "Verify phone with ${token}", TEXT);
        
        app = App.create();
        app.setIdentifier(TEST_APP_ID);
        app.setName("This study name");
        app.setShortName("ShortName");
        app.setSupportEmail(SUPPORT_EMAIL);

        // Mock bridge config
        when(mockBridgeConfig.getInt(CONFIG_KEY_CHANNEL_THROTTLE_MAX_REQUESTS)).thenReturn(2);
        when(mockBridgeConfig.getInt(CONFIG_KEY_CHANNEL_THROTTLE_TIMEOUT_SECONDS)).thenReturn(300);
        service.setBridgeConfig(mockBridgeConfig);
        
        // Mock cache provider to do a basic in-memory map for simple gets and sets.
        mockCacheProviderMap = new HashMap<>();

        when(mockCacheProvider.getObject(any(CacheKey.class), any(Class.class))).thenAnswer(invocation -> {
            CacheKey cacheKey = invocation.getArgument(0);
            return mockCacheProviderMap.get(cacheKey.toString());
        });

        doAnswer(invocation -> {
            CacheKey cacheKey = invocation.getArgument(0);
            Object object = invocation.getArgument(1);
            mockCacheProviderMap.put(cacheKey.toString(), object);
            return null;
        }).when(mockCacheProvider).setObject(any(), any(), anyInt());

        doAnswer(invocation -> {
            CacheKey cacheKey = invocation.getArgument(0);
            mockCacheProviderMap.remove(cacheKey.toString());
            return null;
        }).when(mockCacheProvider).removeObject(any());

        // Add params to mock account.
        when(mockAccount.getId()).thenReturn(USER_ID);
        when(service.getDateTimeInMillis()).thenReturn(TIMESTAMP.getMillis());
    }
    
    @AfterMethod
    public void afterMethod() {
        BridgeUtils.setRequestContext(NULL_INSTANCE);
    }
    
    private void mockRevision(TemplateType templateType, String subject, String body, MimeType type) {
        TemplateRevision revision = TemplateRevision.create();
        revision.setSubject(subject);
        revision.setDocumentContent(body);
        revision.setMimeType(type);
        when(mockTemplateService.getRevisionForUser(any(), eq(templateType))).thenReturn(revision);
    }
    
    @Test
    public void sendEmailVerificationToken() throws Exception {
        when(service.getNextToken()).thenReturn(SPTOKEN);
        
        service.sendEmailVerificationToken(app, USER_ID, EMAIL);
        
        verify(mockSendMailService).sendEmail(emailProviderCaptor.capture());
        verify(mockCacheProvider).setObject(eq(SPTOKEN_CACHE_KEY), stringCaptor.capture(), eq(VERIFY_CACHE_IN_SECONDS));
        
        String string = stringCaptor.getValue();
        JsonNode node = BridgeObjectMapper.get().readTree(string);
        assertEquals(node.get("appId").textValue(), TEST_APP_ID);
        assertEquals(node.get("userId").textValue(), "userId");
        assertEquals(node.get("type").textValue(), "email");
        assertEquals(node.get("expiresOn").longValue(),
                TIMESTAMP.getMillis() + (VERIFY_OR_RESET_EXPIRE_IN_SECONDS * 1000));
        
        BasicEmailProvider provider = emailProviderCaptor.getValue();
        Map<String,String> tokens = provider.getTokenMap();
        assertEquals(tokens.get("sptoken"), SPTOKEN);
        assertEquals(tokens.get("emailVerificationExpirationPeriod"), "2 hours");
        
        MimeTypeEmail email = provider.getMimeTypeEmail();
        assertEquals(email.getSenderAddress(), "\"This study name\" <support@support.com>");
        assertEquals(email.getRecipientAddresses().size(), 1);
        assertEquals(email.getRecipientAddresses().get(0), EMAIL);
        assertEquals(email.getSubject(), "VE This study name");
        MimeBodyPart body = email.getMessageParts().get(0);
        String bodyString = (String)body.getContent();
        assertTrue(bodyString.contains("/mobile/verifyEmail.html?study=" + TEST_APP_ID + "&sptoken="+SPTOKEN));
        assertTrue(bodyString.contains("/ve?study=" + TEST_APP_ID + "&sptoken="+SPTOKEN));
        assertEquals(email.getType(), EmailType.VERIFY_EMAIL);

        // Verify throttling cache calls.
        verify(mockCacheProvider).getObject(VERIFY_EMAIL_THROTTLE_CACHE_KEY, Integer.class);
        verify(mockCacheProvider).setObject(eq(VERIFY_EMAIL_THROTTLE_CACHE_KEY), any(), anyInt());

        verifyNoMoreInteractions(mockCacheProvider);
    }
    
    @Test
    public void sendEmailVerificationTokenNoEmail() {
        service.sendEmailVerificationToken(app, USER_ID, null);
        verify(mockSendMailService, never()).sendEmail(any());
        verifyNoMoreInteractions(mockCacheProvider);
    }

    @Test
    public void sendEmailVerificationTokenThrottled() {
        // Throttle limit is 2. Make 3 requests, and send only 2 emails.
        when(service.getNextToken()).thenReturn(TOKEN);
        service.sendEmailVerificationToken(app, USER_ID, EMAIL);
        service.sendEmailVerificationToken(app, USER_ID, EMAIL);
        service.sendEmailVerificationToken(app, USER_ID, EMAIL);
        verify(mockSendMailService, times(2)).sendEmail(any());
    }

    @Test
    public void sendPhoneVerificationToken() throws Exception {
        when(service.getNextPhoneToken()).thenReturn("012345");

        service.sendPhoneVerificationToken(app, USER_ID, TestConstants.PHONE);

        verify(mockSmsService).sendSmsMessage(eq(USER_ID), smsMessageProviderCaptor.capture());
        verify(mockCacheProvider).setObject(eq(PHONE_TOKEN_CACHE_KEY), stringCaptor.capture(), eq(VERIFY_CACHE_IN_SECONDS));
        
        String string = stringCaptor.getValue();
        JsonNode node = BridgeObjectMapper.get().readTree(string);
        assertEquals(node.get("appId").textValue(), TEST_APP_ID);
        assertEquals(node.get("userId").textValue(), "userId");
        assertEquals(node.get("type").textValue(), "phone");
        assertEquals(node.get("expiresOn").longValue(),
                TIMESTAMP.getMillis() + (VERIFY_OR_RESET_EXPIRE_IN_SECONDS * 1000));
        
        SmsMessageProvider provider = smsMessageProviderCaptor.getValue();
        Map<String,String> tokens = provider.getTokenMap();
        assertEquals(tokens.get("token"), "012-345");
        assertEquals(tokens.get("phoneVerificationExpirationPeriod"), "2 hours");
        assertEquals(provider.getSmsType(), "Transactional");
        
        String message = provider.getSmsRequest().getMessage();
        assertTrue(message.contains("012-345"));

        // Verify throttling cache calls.
        verify(mockCacheProvider).getObject(VERIFY_PHONE_THROTTLE_CACHE_KEY, Integer.class);
        verify(mockCacheProvider).setObject(eq(VERIFY_PHONE_THROTTLE_CACHE_KEY), any(), anyInt());

        verifyNoMoreInteractions(mockCacheProvider);
    }
    
    @Test
    public void sendPhoneVerificationTokenNoPhone() {
        service.sendPhoneVerificationToken(app, USER_ID, null);
        verify(mockSmsService, never()).sendSmsMessage(any(), any());
        verify(mockCacheProvider, never()).setObject(any(), any(), anyInt());
        verifyNoMoreInteractions(mockCacheProvider);
    }

    @Test
    public void sendPhoneVerificationTokenThrottled() {
        // Throttle limit is 2. Make 3 requests, and send only 2 emails.
        service.sendPhoneVerificationToken(app, USER_ID, TestConstants.PHONE);
        service.sendPhoneVerificationToken(app, USER_ID, TestConstants.PHONE);
        service.sendPhoneVerificationToken(app, USER_ID, TestConstants.PHONE);
        verify(mockSmsService, times(2)).sendSmsMessage(eq(USER_ID), smsMessageProviderCaptor.capture());
    }
    
    @Test
    public void resendEmailVerificationToken() {
        when(service.getNextToken()).thenReturn(TOKEN);
        when(mockStudyService.getStudy(TEST_APP_ID)).thenReturn(app);
        when(mockAccountService.getAccount(ACCOUNT_ID_WITH_EMAIL)).thenReturn(mockAccount);
        when(mockAccount.getId()).thenReturn(USER_ID);
        when(mockAccount.getEmail()).thenReturn(EMAIL);
        
        service.resendVerificationToken(ChannelType.EMAIL, ACCOUNT_ID_WITH_EMAIL);
        
        verify(service).sendEmailVerificationToken(app, USER_ID, EMAIL);
        verify(mockCacheProvider).setObject(eq(TOKEN_CACHE_KEY), any(), eq(VERIFY_CACHE_IN_SECONDS));
    }
    
    @Test(expectedExceptions = UnsupportedOperationException.class)
    public void resendEmailVerificationTokenUnsupportedType() {
        when(mockStudyService.getStudy(TEST_APP_ID)).thenReturn(app);
        when(mockAccountService.getAccount(ACCOUNT_ID_WITH_EMAIL)).thenReturn(mockAccount);
        
        // Use null so we don't have to create an unsupported channel type
        service.resendVerificationToken(null, ACCOUNT_ID_WITH_EMAIL);
    }
    
    @Test
    public void resendEmailVerificationTokenFailsWithMissingStudy() {
        when(mockStudyService.getStudy(TEST_APP_ID)).thenThrow(new EntityNotFoundException(App.class));
        
        try {
            service.resendVerificationToken(ChannelType.EMAIL, ACCOUNT_ID_WITH_EMAIL);
            fail("Should have thrown exception");
        } catch(EntityNotFoundException e) {
            // expected exception
        }
        verify(service, never()).sendEmailVerificationToken(any(), any(), any());
        verifyNoMoreInteractions(mockCacheProvider);
    }
    
    @Test
    public void resendEmailVerificationTokenFailsQuietlyWithMissingAccount() {
        when(mockAccountService.getAccount(ACCOUNT_ID_WITH_EMAIL)).thenReturn(null);
        
        service.resendVerificationToken(ChannelType.EMAIL, ACCOUNT_ID_WITH_EMAIL);
        
        verify(service, never()).sendEmailVerificationToken(any(), any(), any());
        verifyNoMoreInteractions(mockCacheProvider);
    }
    
    @Test
    public void resendPhoneVerificationToken() {
        when(service.getNextPhoneToken()).thenReturn(PHONE_TOKEN);
        when(mockStudyService.getStudy(TEST_APP_ID)).thenReturn(app);
        when(mockAccountService.getAccount(ACCOUNT_ID_WITH_PHONE)).thenReturn(mockAccount);
        when(mockAccount.getId()).thenReturn(USER_ID);
        when(mockAccount.getPhone()).thenReturn(TestConstants.PHONE);
        
        service.resendVerificationToken(ChannelType.PHONE, ACCOUNT_ID_WITH_PHONE);
        
        verify(service).sendPhoneVerificationToken(app, USER_ID, TestConstants.PHONE);
        
        verify(mockCacheProvider).setObject(eq(PHONE_TOKEN_CACHE_KEY), stringCaptor.capture(),
                eq(VERIFY_CACHE_IN_SECONDS));
    }
    
    @Test
    public void resendPhoneVerificationTokenFailsWithMissingStudy() {
        when(mockStudyService.getStudy(TEST_APP_ID)).thenThrow(new EntityNotFoundException(App.class));
        
        try {
            service.resendVerificationToken(ChannelType.EMAIL, ACCOUNT_ID_WITH_PHONE);
            fail("Should have thrown exception");
        } catch(EntityNotFoundException e) {
            // expected exception
        }
        verify(service, never()).sendPhoneVerificationToken(any(), any(), any());
        verifyNoMoreInteractions(mockCacheProvider);
    }
    
    @Test
    public void resendPhoneVerificationTokenFailsQuietlyWithMissingAccount() {
        when(mockAccountService.getAccount(ACCOUNT_ID_WITH_PHONE)).thenReturn(null);
        
        service.resendVerificationToken(ChannelType.EMAIL, ACCOUNT_ID_WITH_PHONE);
        
        verify(service, never()).sendPhoneVerificationToken(any(), any(), any());
        verifyNoMoreInteractions(mockCacheProvider);
    }
    
    @Test
    public void verifyEmail() {
        when(service.getDateTimeInMillis()).thenReturn(TIMESTAMP.getMillis());
        when(mockCacheProvider.getObject(SPTOKEN_CACHE_KEY, String.class)).thenReturn(
            createJson("{'studyId':'"+TEST_APP_ID+"','type':'email','userId':'userId',"+
                    "'expiresOn':"+ TIMESTAMP.getMillis()+"}"));
        when(mockStudyService.getStudy(TEST_APP_ID)).thenReturn(app);
        when(mockAccountService.getAccount(ACCOUNT_ID_WITH_ID)).thenReturn(mockAccount);
        when(mockAccount.getId()).thenReturn("accountId");
        
        Verification verification = new Verification(SPTOKEN);
        
        Account account = service.verifyChannel(ChannelType.EMAIL, verification);
        assertEquals(account.getId(), "accountId");
        verify(mockCacheProvider).getObject(SPTOKEN_CACHE_KEY, String.class);
    }
    
    @Test(expectedExceptions = BadRequestException.class, 
            expectedExceptionsMessageRegExp=VERIFY_TOKEN_EXPIRED)
    public void verifyWithoutCreatedFailsCorrectly() {
        // This is a dumb test, but prior to the introduction of the expiresOn value, the verification 
        // object's TTL is the timeout value for the link working... the cache returns null and 
        // the correct error is thrown.
        service.verifyChannel(ChannelType.EMAIL, new Verification(SPTOKEN));
    }
    
    // This almost seems logically impossible, but maybe if an admin deleted an account
    // and an email was hanging out there...
    @Test(expectedExceptions = EntityNotFoundException.class, 
            expectedExceptionsMessageRegExp = ".*Account not found.*")
    public void verifyNoAccount() {
        when(service.getDateTimeInMillis()).thenReturn(TIMESTAMP.getMillis());
        when(mockCacheProvider.getObject(SPTOKEN_CACHE_KEY, String.class)).thenReturn(
                createJson("{'studyId':'" + TEST_APP_ID + "','type':'email','userId':'userId','expiresOn':"
                        + TIMESTAMP.getMillis() + "}"));
        when(mockStudyService.getStudy(TEST_APP_ID)).thenReturn(app);
        
        Verification verification = new Verification(SPTOKEN);
        service.verifyChannel(ChannelType.EMAIL, verification);
    }
    
    @Test(expectedExceptions = BadRequestException.class, 
            expectedExceptionsMessageRegExp=VERIFY_TOKEN_EXPIRED)
    public void verifyWithMismatchedChannel() {
        when(service.getDateTimeInMillis()).thenReturn(TIMESTAMP.getMillis());
        when(mockCacheProvider.getObject(SPTOKEN_CACHE_KEY, String.class)).thenReturn(
                createJson("{'studyId':'" + TEST_APP_ID + "','type':'email','userId':'userId','expiresOn':"
                        + TIMESTAMP.getMillis() + "}"));
        when(mockStudyService.getStudy(TEST_APP_ID)).thenReturn(app);
        
        Verification verification = new Verification(SPTOKEN);
        // Should be email but was called through the phone API
        service.verifyChannel(ChannelType.PHONE, verification);
    }    
    
    @Test(expectedExceptions = BadRequestException.class, 
            expectedExceptionsMessageRegExp=VERIFY_TOKEN_EXPIRED)
    public void verifyEmailExpired() {
        when(service.getDateTimeInMillis()).thenReturn(TIMESTAMP.getMillis()+1);
        when(mockCacheProvider.getObject(SPTOKEN_CACHE_KEY, String.class)).thenReturn(
                createJson("{'studyId':'" + TEST_APP_ID + "','type':'email','userId':'userId','expiresOn':"
                        + TIMESTAMP.getMillis() + "}"));
        when(mockStudyService.getStudy(TEST_APP_ID)).thenReturn(app);
        when(mockAccountService.getAccount(ACCOUNT_ID_WITH_ID)).thenReturn(mockAccount);
        
        Verification verification = new Verification(SPTOKEN);
        service.verifyChannel(ChannelType.EMAIL, verification);
    }
    
    @Test(expectedExceptions = BadRequestException.class, 
            expectedExceptionsMessageRegExp=".*That email address has already been verified.*")
    public void verifyEmailAlreadyVerified() {
        when(service.getDateTimeInMillis()).thenReturn(TIMESTAMP.getMillis()+1);
        when(mockCacheProvider.getObject(SPTOKEN_CACHE_KEY, String.class)).thenReturn(
            createJson("{'studyId':'"+TEST_APP_ID+"','type':'email','userId':'userId','expiresOn':"+
                    TIMESTAMP.getMillis()+"}"));
        when(mockStudyService.getStudy(TEST_APP_ID)).thenReturn(app);
        when(mockAccountService.getAccount(ACCOUNT_ID_WITH_ID)).thenReturn(mockAccount);
        when(mockAccount.getId()).thenReturn("accountId");
        when(mockAccount.getEmailVerified()).thenReturn(TRUE);
        
        Verification verification = new Verification(SPTOKEN);
        service.verifyChannel(ChannelType.EMAIL, verification);        
    }
    
    @Test(expectedExceptions = BadRequestException.class, 
            expectedExceptionsMessageRegExp=VERIFY_TOKEN_EXPIRED)
    public void verifyEmailBadSptokenThrowsException() {
        when(mockCacheProvider.getObject(SPTOKEN_CACHE_KEY, String.class)).thenReturn(null);
        
        Verification verification = new Verification(SPTOKEN);
        service.verifyChannel(ChannelType.EMAIL, verification);
    }
    
    @Test
    public void verifyPhone() {
        when(service.getDateTimeInMillis()).thenReturn(TIMESTAMP.getMillis());
        when(mockCacheProvider.getObject(SPTOKEN_CACHE_KEY, String.class)).thenReturn(
                TestUtils.createJson("{'studyId':'"+TEST_APP_ID+"','type':'phone','userId':'userId','expiresOn':"+
                        TIMESTAMP.getMillis()+"}"));
        when(mockStudyService.getStudy(TEST_APP_ID)).thenReturn(app);
        when(mockAccountService.getAccount(ACCOUNT_ID_WITH_ID)).thenReturn(mockAccount);
        when(mockAccount.getId()).thenReturn("accountId");
        
        Verification verification = new Verification(SPTOKEN);
        Account account = service.verifyChannel(ChannelType.PHONE, verification);
        
        assertEquals(account.getId(), "accountId");
        verify(mockCacheProvider).getObject(SPTOKEN_CACHE_KEY, String.class);
    }
    
    @Test(expectedExceptions = BadRequestException.class,
            expectedExceptionsMessageRegExp=".*That phone number has already been verified.*")
    public void verifyPhoneAlreadyVerified() {
        when(service.getDateTimeInMillis()).thenReturn(TIMESTAMP.getMillis());
        when(mockCacheProvider.getObject(SPTOKEN_CACHE_KEY, String.class)).thenReturn(
                TestUtils.createJson("{'studyId':'"+TEST_APP_ID+"','type':'phone','userId':'userId','expiresOn':"+
                        TIMESTAMP.getMillis()+"}"));
        when(mockStudyService.getStudy(TEST_APP_ID)).thenReturn(app);
        when(mockAccountService.getAccount(ACCOUNT_ID_WITH_ID)).thenReturn(mockAccount);
        when(mockAccount.getId()).thenReturn("accountId");
        when(mockAccount.getPhoneVerified()).thenReturn(TRUE);
        
        Verification verification = new Verification(SPTOKEN);
        service.verifyChannel(ChannelType.PHONE, verification);
    }
    
    @Test(expectedExceptions = BadRequestException.class, 
            expectedExceptionsMessageRegExp=VERIFY_TOKEN_EXPIRED)
    public void verifyPhoneExpired() {
        when(service.getDateTimeInMillis()).thenReturn(TIMESTAMP.getMillis()+1);
        when(mockCacheProvider.getObject(SPTOKEN_CACHE_KEY, String.class)).thenReturn(
                TestUtils.createJson("{'studyId':'"+TEST_APP_ID+"','type':'phone','userId':'userId','expiresOn':"+
                        TIMESTAMP.getMillis()+"}"));
        when(mockStudyService.getStudy(TEST_APP_ID)).thenReturn(app);
        when(mockAccountService.getAccount(ACCOUNT_ID_WITH_ID)).thenReturn(mockAccount);
        
        Verification verification = new Verification(SPTOKEN);
        service.verifyChannel(ChannelType.PHONE, verification);
    }
    
    @Test(expectedExceptions = BadRequestException.class, 
            expectedExceptionsMessageRegExp=VERIFY_TOKEN_EXPIRED)
    public void verifyEmailViaPhoneFails() {
        when(mockCacheProvider.getObject(SPTOKEN_CACHE_KEY, String.class)).thenReturn(
                TestUtils.createJson("{'studyId':'"+TEST_APP_ID+"','type':'email','userId':'userId'}"));
        
        Verification verification = new Verification(SPTOKEN);
        service.verifyChannel(ChannelType.PHONE, verification);
        
        verifyNoMoreInteractions(mockCacheProvider);
    }
    
    @Test(expectedExceptions = BadRequestException.class, 
            expectedExceptionsMessageRegExp=VERIFY_TOKEN_EXPIRED)
    public void verifyPhoneViaEmailFails() {
        when(mockCacheProvider.getObject(SPTOKEN_CACHE_KEY, String.class)).thenReturn(
                TestUtils.createJson("{'studyId':'"+TEST_APP_ID+"','type':'phone','userId':'userId'}"));
        
        Verification verification = new Verification(SPTOKEN);
        service.verifyChannel(ChannelType.EMAIL, verification);
        
        verifyNoMoreInteractions(mockCacheProvider);
    }
    
    @Test
    public void notifyAccountExistsForEmail() throws Exception {
        app.setEmailVerificationEnabled(true);
        // In this path email sign in is also enabled, so we will generate a link to sign in that can 
        // be used in lieu of directing the user to a password reset.
        app.setEmailSignInEnabled(true);
        AccountId accountId = AccountId.forId(TEST_APP_ID, USER_ID);
        when(mockStudyService.getStudy(TEST_APP_ID)).thenReturn(app);
        when(service.getNextToken()).thenReturn(SPTOKEN, TOKEN);
        when(mockAccount.getEmail()).thenReturn(EMAIL);
        when(mockAccount.getEmailVerified()).thenReturn(Boolean.TRUE);
        when(mockAccountService.getAccount(accountId)).thenReturn(mockAccount);
        when(mockAccountService.getAccount(AccountId.forEmail(TEST_APP_ID, EMAIL))).thenReturn(mockAccount);
        
        service.notifyAccountExists(app, accountId);
        
        verify(mockCacheProvider).setObject(PASSWORD_RESET_FOR_EMAIL, EMAIL, 60*60*2);
        verify(mockSendMailService).sendEmail(emailProviderCaptor.capture());
        
        BasicEmailProvider provider = emailProviderCaptor.getValue();
        assertEquals(provider.getTokenMap().get("token"), TOKEN);
        assertEquals(provider.getTokenMap().get("sptoken"), SPTOKEN);
        assertEquals(provider.getTokenMap().get("expirationPeriod"), "2 hours");
        assertEquals(provider.getTokenMap().get("email"), BridgeUtils.encodeURIComponent(EMAIL));
        assertEquals(provider.getTokenMap().get("expirationWindow"), "2");
        
        MimeTypeEmail email = provider.getMimeTypeEmail();
        assertEquals(email.getSenderAddress(), "\"This study name\" <support@support.com>");
        assertEquals(email.getRecipientAddresses().size(), 1);
        assertEquals(email.getRecipientAddresses().get(0), EMAIL);
        assertEquals(email.getSubject(), "AE This study name");
        assertEquals(email.getType(), EmailType.RESET_PASSWORD);
        
        MimeBodyPart body = email.getMessageParts().get(0);
        String bodyString = (String)body.getContent();
        assertTrue(bodyString.contains("/mobile/resetPassword.html?study="+TEST_APP_ID+"&sptoken="+SPTOKEN));
        assertTrue(bodyString.contains("/rp?study="+TEST_APP_ID+"&sptoken="+SPTOKEN));
        // This was recently added and is only used in one study where we've hard-coded it. Remove it
        // so that ${url} continues to work for the reset password link. We're moving all links 
        // towad the short form, in stepped releases.
        assertTrue(bodyString.contains("/s/"+TEST_APP_ID+"?email=email%40email.com&token="+TOKEN));
        
        // All the template variables have been replaced
        assertFalse(bodyString.contains("${url}"));
        assertFalse(bodyString.contains("${resetPasswordUrl}"));
        assertFalse(bodyString.contains("${emailSignInUrl}"));
        assertFalse(bodyString.contains("${shortUrl}"));
        assertFalse(bodyString.contains("${shortResetPasswordUrl}"));
        assertFalse(bodyString.contains("${shortEmailSignInUrl}"));
    }

    @Test
    public void notifyAccountExistsForEmailNoSignIn() throws Exception {
        // In this path email sign in is also enabled, so we will generate a link to sign in that can 
        // be used in lieu of directing the user to a password reset.
        app.setEmailSignInEnabled(false);
        app.setEmailVerificationEnabled(true);
        AccountId accountId = AccountId.forId(TEST_APP_ID, USER_ID);
        when(service.getNextToken()).thenReturn(SPTOKEN);
        when(mockAccount.getEmail()).thenReturn(EMAIL);
        when(mockAccount.getEmailVerified()).thenReturn(Boolean.TRUE);
        when(mockAccountService.getAccount(accountId)).thenReturn(mockAccount);
        
        service.notifyAccountExists(app, accountId);
        
        verify(mockCacheProvider).setObject(PASSWORD_RESET_FOR_EMAIL, EMAIL, 60*60*2);
        verify(mockSendMailService).sendEmail(emailProviderCaptor.capture());
        
        BasicEmailProvider provider = emailProviderCaptor.getValue();
        // not set, no email sign in
        assertNull(provider.getTokenMap().get("token"));
        assertNull(provider.getTokenMap().get("email"));
        assertEquals(provider.getTokenMap().get("sptoken"), SPTOKEN);
        assertEquals(provider.getTokenMap().get("expirationPeriod"), "2 hours");
        assertEquals(provider.getTokenMap().get("expirationWindow"), "2");
        
        MimeTypeEmail email = provider.getMimeTypeEmail();
        assertEquals(email.getSenderAddress(), "\"This study name\" <support@support.com>");
        assertEquals(email.getRecipientAddresses().size(), 1);
        assertEquals(email.getRecipientAddresses().get(0), EMAIL);
        assertEquals(email.getSubject(), "AE This study name");
        
        MimeBodyPart body = email.getMessageParts().get(0);
        String bodyString = (String)body.getContent();
        assertTrue(bodyString.contains("/mobile/resetPassword.html?study="+TEST_APP_ID+"&sptoken="+SPTOKEN));
        assertTrue(bodyString.contains("/rp?study="+TEST_APP_ID+"&sptoken="+SPTOKEN));
        assertTrue(bodyString.contains("${emailSignInUrl}"));
        
        // The remaining template variables have been replaced.
        assertFalse(bodyString.contains("${url}"));
        assertFalse(bodyString.contains("${resetPasswordUrl}"));
        assertFalse(bodyString.contains("${shortUrl}"));
        assertFalse(bodyString.contains("${shortResetPasswordUrl}"));
        assertFalse(bodyString.contains("${shortEmailSignInUrl}"));
    }    
    @Test
    public void notifyAccountForEmailSignInDoesntThrottle() throws Exception {
        app.setEmailSignInEnabled(true);
        app.setEmailVerificationEnabled(true);
        AccountId accountId = AccountId.forId(TEST_APP_ID, USER_ID);
        when(mockStudyService.getStudy(TEST_APP_ID)).thenReturn(app);
        when(mockAccount.getEmail()).thenReturn(EMAIL);
        when(mockAccount.getEmailVerified()).thenReturn(Boolean.TRUE);
        when(mockAccountService.getAccount(any())).thenReturn(mockAccount);

        // Note that password reset token (sptoken) is never cached, so we generate it 3 times. The email sign-in token
        // (token), is cached, so we only generate it the first time around.
        when(service.getNextToken()).thenReturn(SPTOKEN, TOKEN, SPTOKEN, SPTOKEN);

        // Throttle limit is 2, but it doesn't apply to notifyAccount(). Call this 3 times, and expect 3 emails with
        // email sign-in URL.
        service.notifyAccountExists(app, accountId);
        service.notifyAccountExists(app, accountId);
        service.notifyAccountExists(app, accountId);

        verify(mockSendMailService, times(3)).sendEmail(emailProviderCaptor.capture());

        List<BasicEmailProvider> emailProviderList = emailProviderCaptor.getAllValues();
        for (BasicEmailProvider oneEmailProvider : emailProviderList) {
            assertEquals(oneEmailProvider.getTokenMap().get("expirationWindow"), "2");
            assertEquals(oneEmailProvider.getTokenMap().get("sptoken"), SPTOKEN);
            assertEquals(oneEmailProvider.getTokenMap().get("token"), TOKEN);
            assertEquals(oneEmailProvider.getTokenMap().get("expirationPeriod"), "2 hours");
            assertEquals(oneEmailProvider.getTokenMap().get("email"), BridgeUtils.encodeURIComponent(EMAIL));
            
            // Email content is verified in test above. Just verify email sign-in URL.
            MimeTypeEmail email = oneEmailProvider.getMimeTypeEmail();
            MimeBodyPart body = email.getMessageParts().get(0);
            String bodyString = (String)body.getContent();
            assertTrue(bodyString.contains("/s/"+TEST_APP_ID+"?email=email%40email.com&token="+TOKEN));
            assertFalse(bodyString.contains("${emailSignInUrl}"));
            assertFalse(bodyString.contains("${shortEmailSignInUrl}"));
        }
        verify(mockCacheProvider, times(3)).setObject(PASSWORD_RESET_FOR_EMAIL, EMAIL,
                VERIFY_OR_RESET_EXPIRE_IN_SECONDS);
    }

    @Test
    public void notifyAccountExistsForEmailWithoutEmailSignIn() throws Exception {
        app.setEmailVerificationEnabled(true);
        // A successful notification of an existing account where email sign in is not enabled. The 
        // emailSignIn template variable will not be replaced.
        AccountId accountId = AccountId.forId(TEST_APP_ID, USER_ID);
        when(service.getNextToken()).thenReturn(SPTOKEN);
        when(mockAccount.getEmail()).thenReturn(EMAIL);
        when(mockAccount.getEmailVerified()).thenReturn(Boolean.TRUE);
        when(mockAccountService.getAccount(accountId)).thenReturn(mockAccount);
        
        service.notifyAccountExists(app, accountId);
        
        verify(mockSendMailService).sendEmail(emailProviderCaptor.capture());
        
        BasicEmailProvider provider = emailProviderCaptor.getValue();
        assertEquals(provider.getTokenMap().get("sptoken"), SPTOKEN);
        assertEquals(provider.getTokenMap().get("expirationWindow"), "2");
        assertEquals(provider.getTokenMap().get("expirationPeriod"), "2 hours");

        String bodyString = (String) provider.getMimeTypeEmail().getMessageParts().get(0).getContent();
        assertTrue(bodyString.contains("${emailSignInUrl}"));
        
        verify(mockCacheProvider).setObject(PASSWORD_RESET_FOR_EMAIL, EMAIL,
                VERIFY_OR_RESET_EXPIRE_IN_SECONDS);
    }    
    
    @Test
    public void notifyAccountExistsForPhone() throws Exception {
        when(mockStudyService.getStudy(TEST_APP_ID)).thenReturn(app);
        app.setPhoneSignInEnabled(true);
        AccountId accountId = AccountId.forPhone(TEST_APP_ID, TestConstants.PHONE);
        when(service.getNextToken()).thenReturn(SPTOKEN);
        when(service.getNextPhoneToken()).thenReturn(PHONE_TOKEN);
        when(mockAccount.getPhone()).thenReturn(TestConstants.PHONE);
        when(mockAccount.getPhoneVerified()).thenReturn(Boolean.TRUE);
        when(mockAccountService.getAccount(accountId)).thenReturn(mockAccount);
        
        service.notifyAccountExists(app, accountId);
        
        verify(mockCacheProvider).setObject(PASSWORD_RESET_FOR_PHONE, 
                BridgeObjectMapper.get().writeValueAsString(TestConstants.PHONE), 
                VERIFY_OR_RESET_EXPIRE_IN_SECONDS);
        verify(mockSmsService).sendSmsMessage(eq(USER_ID), smsMessageProviderCaptor.capture());
        
        String message = smsMessageProviderCaptor.getValue().getSmsRequest().getMessage();
        assertTrue(message.contains("Account for ShortName already exists. Reset password: "));
        assertTrue(message.contains("/rp?study="+TEST_APP_ID+"&sptoken="+SPTOKEN));
        assertTrue(message.contains(" or "+PHONE_TOKEN.substring(0,3) + "-" + PHONE_TOKEN.substring(3,6)));
        assertEquals(smsMessageProviderCaptor.getValue().getSmsType(), "Transactional");
    }
    
    @Test
    public void notifyAccountExistsForPhoneNoSignIn() throws Exception {
        app.setPhoneSignInEnabled(false);
        AccountId accountId = AccountId.forPhone(TEST_APP_ID, TestConstants.PHONE);
        when(service.getNextToken()).thenReturn(SPTOKEN);
        when(mockAccount.getPhone()).thenReturn(TestConstants.PHONE);
        when(mockAccount.getPhoneVerified()).thenReturn(Boolean.TRUE);
        when(mockAccountService.getAccount(accountId)).thenReturn(mockAccount);
        
        service.notifyAccountExists(app, accountId);
        
        verify(mockCacheProvider).setObject(PASSWORD_RESET_FOR_PHONE, 
                BridgeObjectMapper.get().writeValueAsString(TestConstants.PHONE), 
                VERIFY_OR_RESET_EXPIRE_IN_SECONDS);
        verify(mockSmsService).sendSmsMessage(eq(USER_ID), smsMessageProviderCaptor.capture());
        
        String message = smsMessageProviderCaptor.getValue().getSmsRequest().getMessage();
        assertTrue(message.contains("Account for ShortName already exists. Reset password: "));
        assertTrue(message.contains("/rp?study="+TEST_APP_ID+"&sptoken="+SPTOKEN));
        assertTrue(message.contains(" or ${token}"));
        assertEquals(smsMessageProviderCaptor.getValue().getSmsType(), "Transactional");
        verify(service, never()).getNextPhoneToken();
    }
    
    @Test
    public void notifyAccountExistsWithPhoneAutoVerifySuppressed() {
        app.setPhoneSignInEnabled(true);
        app.setAutoVerificationPhoneSuppressed(true);
        
        AccountId accountId = AccountId.forPhone(TEST_APP_ID, TestConstants.PHONE);
        when(mockAccount.getPhone()).thenReturn(TestConstants.PHONE);
        when(mockAccount.getPhoneVerified()).thenReturn(Boolean.TRUE);
        when(mockAccountService.getAccount(accountId)).thenReturn(mockAccount);
        
        service.notifyAccountExists(app, accountId);
        
        verify(mockCacheProvider, never()).setObject(any(), any(), anyInt());
        verify(mockSmsService, never()).sendSmsMessage(any(), any());
    }
    
    @Test
    public void notifyAccountExistsWithEmailAutoVerifySuppressed() {
        // In this path email sign in is also enabled, so we will generate a link to sign in that can 
        // be used in lieu of directing the user to a password reset.
        app.setEmailSignInEnabled(true);
        app.setAutoVerificationEmailSuppressed(true);
        
        AccountId accountId = AccountId.forId(TEST_APP_ID, USER_ID);
        when(mockAccount.getEmail()).thenReturn(EMAIL);
        when(mockAccount.getEmailVerified()).thenReturn(Boolean.TRUE);
        when(mockAccountService.getAccount(accountId)).thenReturn(mockAccount);
        
        service.notifyAccountExists(app, accountId);
        
        verify(mockCacheProvider, never()).setObject(any(), any(), anyInt());
        verify(mockSendMailService, never()).sendEmail(any());
    }
    
    @Test
    public void notifyAccountExistsWithEmailVerifyOff() {
        // In this path email sign in is also enabled, so we will generate a link to sign in that can 
        // be used in lieu of directing the user to a password reset.
        app.setEmailSignInEnabled(true);
        app.setAutoVerificationEmailSuppressed(false);
        app.setEmailVerificationEnabled(false);
        
        AccountId accountId = AccountId.forId(TEST_APP_ID, USER_ID);
        when(mockAccount.getEmail()).thenReturn(EMAIL);
        when(mockAccount.getEmailVerified()).thenReturn(Boolean.TRUE);
        when(mockAccountService.getAccount(accountId)).thenReturn(mockAccount);
        
        service.notifyAccountExists(app, accountId);
        
        verify(mockCacheProvider, never()).setObject(any(), any(), anyInt());
        verify(mockSendMailService, never()).sendEmail(any());
    }

    @Test
    public void notifyAccountExistsUnverifiedEmailUnverifiedPhone() {
        // Set study flags so that it would send emails/SMS if they were verified.
        app.setEmailVerificationEnabled(true);
        app.setAutoVerificationEmailSuppressed(false);
        app.setAutoVerificationPhoneSuppressed(false);

        // Mock account DAO.
        AccountId accountId = AccountId.forId(TEST_APP_ID, USER_ID);
        when(mockAccount.getEmail()).thenReturn(EMAIL);
        when(mockAccount.getEmailVerified()).thenReturn(Boolean.FALSE);
        when(mockAccount.getPhone()).thenReturn(TestConstants.PHONE);
        when(mockAccount.getPhoneVerified()).thenReturn(Boolean.FALSE);
        when(mockAccountService.getAccount(accountId)).thenReturn(mockAccount);

        // Execute.
        service.notifyAccountExists(app, accountId);

        // We never send email nor SMS.
        verify(mockSendMailService, never()).sendEmail(any());
        verify(mockSmsService, never()).sendSmsMessage(any(), any());
    }

    @Test
    public void notifyAccountExistsEmailVerifiedNullPhoneVerifiedNull() {
        // Set study flags so that it would send emails/SMS if they were verified.
        app.setEmailVerificationEnabled(true);
        app.setAutoVerificationEmailSuppressed(false);
        app.setAutoVerificationPhoneSuppressed(false);

        // Mock account DAO.
        AccountId accountId = AccountId.forId(TEST_APP_ID, USER_ID);
        when(mockAccount.getEmail()).thenReturn(EMAIL);
        when(mockAccount.getEmailVerified()).thenReturn(null);
        when(mockAccount.getPhone()).thenReturn(TestConstants.PHONE);
        when(mockAccount.getPhoneVerified()).thenReturn(null);
        when(mockAccountService.getAccount(accountId)).thenReturn(mockAccount);

        // Execute.
        service.notifyAccountExists(app, accountId);

        // We never send email nor SMS.
        verify(mockSendMailService, never()).sendEmail(any());
        verify(mockSmsService, never()).sendSmsMessage(any(), any());
    }
    
    @Test
    public void notifyAccountExistsNotFound() {
        service.notifyAccountExists(app, ACCOUNT_ID);
        
        verify(mockTemplateService, never()).getRevisionForUser(any(), any());
        verify(mockSendMailService, never()).sendEmail(any());
        verify(mockSmsService, never()).sendSmsMessage(any(), any());
    }

    @Test
    public void requestResetPasswordWithEmail() throws Exception {
        when(service.getNextToken()).thenReturn(SPTOKEN);
        when(mockAccountService.getAccount(ACCOUNT_ID_WITH_EMAIL)).thenReturn(mockAccount);
        when(mockAccount.getEmail()).thenReturn(EMAIL);
        when(mockAccount.getEmailVerified()).thenReturn(Boolean.TRUE);        
        
        service.requestResetPassword(app, false, ACCOUNT_ID_WITH_EMAIL);
        
        verify(mockCacheProvider).setObject(PASSWORD_RESET_FOR_EMAIL, EMAIL, 60*60*2);
        verify(mockSendMailService).sendEmail(emailProviderCaptor.capture());
        
        BasicEmailProvider provider = emailProviderCaptor.getValue();
        assertEquals(provider.getTokenMap().get("sptoken"), SPTOKEN);
        assertEquals(provider.getTokenMap().get("expirationWindow"), "2");
        assertEquals(provider.getTokenMap().get("expirationPeriod"), "2 hours");
        
        MimeTypeEmail email = provider.getMimeTypeEmail();
        assertEquals(email.getSenderAddress(), "\"This study name\" <support@support.com>");
        assertEquals(email.getRecipientAddresses().size(), 1);
        assertEquals(email.getRecipientAddresses().get(0), EMAIL);
        assertEquals(email.getSubject(), "RP This study name");
        MimeBodyPart body = email.getMessageParts().get(0);
        String bodyString = (String)body.getContent();
        assertTrue(bodyString.contains("/rp?study="+TEST_APP_ID+"&sptoken="+SPTOKEN));
        assertEquals(email.getType(), EmailType.RESET_PASSWORD);
        verifyNoMoreInteractions(mockCacheProvider);
    }
    
    @Test
    public void requestResetPasswordWithPhone() throws Exception {
        when(service.getNextToken()).thenReturn(SPTOKEN);
        when(mockAccountService.getAccount(ACCOUNT_ID_WITH_PHONE)).thenReturn(mockAccount);
        when(mockAccount.getPhone()).thenReturn(TestConstants.PHONE);
        when(mockAccount.getPhoneVerified()).thenReturn(Boolean.TRUE);        
        
        service.requestResetPassword(app, false, ACCOUNT_ID_WITH_PHONE);
        
        verify(mockCacheProvider).setObject(eq(PASSWORD_RESET_FOR_PHONE), stringCaptor.capture(), eq(60*60*2));
        verify(mockSmsService).sendSmsMessage(eq(USER_ID), smsMessageProviderCaptor.capture());
        
        assertEquals(smsMessageProviderCaptor.getValue().getStudy(), app);
        assertEquals(smsMessageProviderCaptor.getValue().getPhone(), TestConstants.PHONE);
        assertEquals(smsMessageProviderCaptor.getValue().getSmsType(), "Transactional");
        String message = smsMessageProviderCaptor.getValue().getSmsRequest().getMessage();
        assertTrue(message.contains("Reset ShortName password: "));
        assertTrue(message.contains("/rp?study="+TEST_APP_ID+"&sptoken="+SPTOKEN));
        
        Phone captured = BridgeObjectMapper.get().readValue(stringCaptor.getValue(), Phone.class);
        assertEquals(captured, TestConstants.PHONE); 
        verifyNoMoreInteractions(mockCacheProvider);
    }

    @Test
    public void requestResetPasswordFailsQuietlyIfEmailPhoneUnverifiedUsingEmail() {
        when(mockAccountService.getAccount(ACCOUNT_ID_WITH_PHONE)).thenReturn(mockAccount);
        when(mockAccount.getEmail()).thenReturn(EMAIL);
        when(mockAccount.getPhoneVerified()).thenReturn(Boolean.FALSE);
        when(mockAccount.getEmailVerified()).thenReturn(Boolean.FALSE);

        service.requestResetPassword(app, false, ACCOUNT_ID_WITH_PHONE);
        
        verify(mockCacheProvider, never()).setObject(any(), any(), anyInt());
        verify(mockSmsService, never()).sendSmsMessage(any(), any());
        verifyNoMoreInteractions(mockCacheProvider);
    }

    @Test
    public void requestResetPasswordFailsQuietlyIfEmailPhoneUnverifiedUsingPhone() {
        when(mockAccountService.getAccount(ACCOUNT_ID_WITH_PHONE)).thenReturn(mockAccount);
        when(mockAccount.getPhone()).thenReturn(TestConstants.PHONE);
        when(mockAccount.getPhoneVerified()).thenReturn(Boolean.FALSE);
        when(mockAccount.getEmailVerified()).thenReturn(Boolean.FALSE);
        
        service.requestResetPassword(app, false, ACCOUNT_ID_WITH_PHONE);
        
        verify(mockCacheProvider, never()).setObject(any(), any(), anyInt());
        verify(mockSmsService, never()).sendSmsMessage(any(), any());
        verifyNoMoreInteractions(mockCacheProvider);
    }
    
    @Test
    public void requestResetPasswordInvalidEmailFailsQuietly() {
        when(mockAccountService.getAccount(ACCOUNT_ID_WITH_EMAIL)).thenReturn(null);
        
        service.requestResetPassword(app, false, ACCOUNT_ID_WITH_EMAIL);
        
        verify(mockCacheProvider, never()).setObject(any(), any(), anyInt());
        verify(mockSendMailService, never()).sendEmail(any());
        verifyNoMoreInteractions(mockCacheProvider);
    }

    @Test
    public void requestRestPasswordUnverifiedEmailFailsQuietly() {
        when(mockAccount.getEmail()).thenReturn(EMAIL);
        when(mockAccountService.getAccount(ACCOUNT_ID_WITH_EMAIL)).thenReturn(mockAccount);
        
        service.requestResetPassword(app, false, ACCOUNT_ID_WITH_EMAIL);
        
        verifyNoMoreInteractions(mockSendMailService);
        verifyNoMoreInteractions(mockSmsService);
        verifyNoMoreInteractions(mockCacheProvider);
    }
    
    @Test
    public void requestRestPasswordUnverifiedPhoneFailsQuietly() {
        when(mockAccount.getPhone()).thenReturn(TestConstants.PHONE);
        when(mockAccountService.getAccount(ACCOUNT_ID_WITH_PHONE)).thenReturn(mockAccount);
        
        service.requestResetPassword(app, false, ACCOUNT_ID_WITH_PHONE);
        
        verifyNoMoreInteractions(mockSendMailService);
        verifyNoMoreInteractions(mockSmsService);
        verifyNoMoreInteractions(mockCacheProvider);
    }
    
    @Test
    public void requestResetPasswordByAdminDoesNotRequireEmailVerification() {
        when(service.getNextToken()).thenReturn(SPTOKEN);
        when(mockAccount.getEmail()).thenReturn(EMAIL);
        when(mockAccountService.getAccount(ACCOUNT_ID_WITH_PHONE)).thenReturn(mockAccount);
        
        service.requestResetPassword(app, true, ACCOUNT_ID_WITH_PHONE);
        
        verify(mockCacheProvider).setObject(PASSWORD_RESET_FOR_EMAIL, EMAIL, 60*60*2);
        verify(mockSendMailService).sendEmail(any());
    }
    
    @Test
    public void requestResetPasswordByAdminDoesNotRequirePhoneVerification() {
        when(service.getNextToken()).thenReturn(SPTOKEN);
        when(mockAccountService.getAccount(ACCOUNT_ID_WITH_PHONE)).thenReturn(mockAccount);
        when(mockAccount.getPhone()).thenReturn(TestConstants.PHONE);
        
        service.requestResetPassword(app, true, ACCOUNT_ID_WITH_PHONE);
        
        verify(mockCacheProvider).setObject(eq(PASSWORD_RESET_FOR_PHONE), stringCaptor.capture(), eq(60*60*2));
        verify(mockSmsService).sendSmsMessage(any(), any());
    }
    
    @Test
    public void requestResetPasswordQuietlyFailsForDisabledAccount() {
        when(mockAccountService.getAccount(ACCOUNT_ID_WITH_PHONE)).thenReturn(mockAccount);
        when(mockAccount.getStatus()).thenReturn(AccountStatus.DISABLED);
        
        service.requestResetPassword(app, false, ACCOUNT_ID_WITH_PHONE);
        
        verify(mockCacheProvider, never()).setObject(any(), any(), anyInt());
        verify(mockSmsService, never()).sendSmsMessage(any(), any());
        verifyNoMoreInteractions(mockCacheProvider);
    }
    
    @Test
    public void resetPasswordWithEmail() {
        when(mockCacheProvider.getObject(PASSWORD_RESET_FOR_EMAIL, String.class)).thenReturn(EMAIL);
        when(mockStudyService.getStudy(TEST_APP_ID)).thenReturn(app);
        when(mockAccountService.getAccount(ACCOUNT_ID_WITH_EMAIL)).thenReturn(mockAccount);

        PasswordReset passwordReset = new PasswordReset("newPassword", SPTOKEN, TEST_APP_ID);
        service.resetPassword(passwordReset);
        
        verify(mockCacheProvider).getObject(PASSWORD_RESET_FOR_EMAIL, String.class);
        verify(mockCacheProvider).removeObject(PASSWORD_RESET_FOR_EMAIL);
        verify(mockAccountService).changePassword(mockAccount, ChannelType.EMAIL, "newPassword");
    }
    
    @Test
    public void resetPasswordWithPhone() {
        when(mockCacheProvider.getObject(PASSWORD_RESET_FOR_PHONE, Phone.class)).thenReturn(TestConstants.PHONE);
        when(mockStudyService.getStudy(TEST_APP_ID)).thenReturn(app);
        when(mockAccountService.getAccount(ACCOUNT_ID_WITH_PHONE)).thenReturn(mockAccount);

        PasswordReset passwordReset = new PasswordReset("newPassword", SPTOKEN, TEST_APP_ID);
        service.resetPassword(passwordReset);
        
        verify(mockCacheProvider).getObject(PASSWORD_RESET_FOR_PHONE, Phone.class);
        verify(mockCacheProvider).removeObject(PASSWORD_RESET_FOR_PHONE);
        verify(mockAccountService).changePassword(mockAccount, ChannelType.PHONE, "newPassword");
    }
    
    @Test
    public void resetPasswordInvalidSptokenThrowsException() {
        when(mockCacheProvider.getObject(PASSWORD_RESET_FOR_EMAIL, String.class)).thenReturn(null);

        PasswordReset passwordReset = new PasswordReset("newPassword", SPTOKEN, TEST_APP_ID);
        try {
            service.resetPassword(passwordReset);
            fail("Should have thrown exception");
        } catch(BadRequestException e) {
            assertEquals(e.getMessage(), "Password reset token has expired (or already been used).");
        }
        verify(mockCacheProvider).getObject(PASSWORD_RESET_FOR_EMAIL, String.class);
        verify(mockCacheProvider, never()).removeObject(any());
        verify(mockAccountService, never()).changePassword(any(), any(ChannelType.class), any());
    }
    
    @Test
    public void resetPasswordInvalidAccount() {
        when(mockCacheProvider.getObject(PASSWORD_RESET_FOR_EMAIL, String.class)).thenReturn(EMAIL);
        when(mockStudyService.getStudy(TEST_APP_ID)).thenReturn(app);
        when(mockAccountService.getAccount(ACCOUNT_ID_WITH_EMAIL)).thenReturn(null);

        PasswordReset passwordReset = new PasswordReset("newPassword", SPTOKEN, TEST_APP_ID);
        
        try {
            service.resetPassword(passwordReset);
            fail("Should have thrown an exception");
        } catch(EntityNotFoundException e) {
            // expected exception
        }
        verify(mockCacheProvider).getObject(PASSWORD_RESET_FOR_EMAIL, String.class);
        verify(mockCacheProvider).removeObject(PASSWORD_RESET_FOR_EMAIL);
        verify(mockAccountService, never()).changePassword(any(), any(ChannelType.class), any());
    }
    
    @Test
    public void requestEmailSignIn() throws Exception {
        // Mock.
        app.setEmailSignInEnabled(true);
        when(mockAccountService.getAccount(SIGN_IN_REQUEST_WITH_EMAIL.getAccountId())).thenReturn(mockAccount);
        when(mockStudyService.getStudy(app.getIdentifier())).thenReturn(app);
        when(service.getNextToken()).thenReturn(TOKEN);

        // Execute.
        String userId = service.requestEmailSignIn(SIGN_IN_REQUEST_WITH_EMAIL);
        assertEquals(userId, USER_ID);

        // Verify dependent services.
        verify(mockCacheProvider).getObject(keyCaptor.capture(), eq(String.class));
        assertEquals(keyCaptor.getValue(), EMAIL_SIGNIN_CACHE_KEY);
        
        verify(mockAccountService).getAccount(SIGN_IN_REQUEST_WITH_EMAIL.getAccountId());
        
        verify(mockCacheProvider).setObject(eq(EMAIL_SIGNIN_CACHE_KEY), stringCaptor.capture(), eq(SIGNIN_EXPIRE_IN_SECONDS));
        assertNotNull(stringCaptor.getValue());

        verify(mockSendMailService).sendEmail(emailProviderCaptor.capture());
        
        BasicEmailProvider provider = emailProviderCaptor.getValue();
        assertEquals(provider.getTokenMap().get("email"), BridgeUtils.encodeURIComponent(EMAIL));
        assertEquals(provider.getTokenMap().get("token"), TOKEN);
        assertEquals(provider.getTokenMap().get("emailSignInExpirationPeriod"), "1 hour");
        
        String token = provider.getTokenMap().get("token");
        
        // app exists in this portion of the URL, indicating variable substitution occurred
        assertTrue(provider.getTokenMap().get("url").contains("/mobile/"+TEST_APP_ID+"/startSession.html"));
        assertTrue(provider.getTokenMap().get("shortUrl").contains("/s/" + TEST_APP_ID));
        assertTrue(provider.getTokenMap().get("url").contains(token));
        assertTrue(provider.getTokenMap().get("shortUrl").contains(token));
        assertEquals(provider.getStudy(), app);
        assertEquals(Iterables.getFirst(provider.getRecipientEmails(), null), EMAIL);
        assertEquals(provider.getMimeTypeEmail().getMessageParts().get(0).getContent(), "Body " + provider.getTokenMap().get("token"));
        assertEquals(provider.getType(), EmailType.EMAIL_SIGN_IN);

        // Verify throttling cache calls.
        verify(mockCacheProvider).getObject(EMAIL_SIGNIN_THROTTLE_CACHE_KEY, Integer.class);
        verify(mockCacheProvider).setObject(eq(EMAIL_SIGNIN_THROTTLE_CACHE_KEY), any(), anyInt());

        verifyNoMoreInteractions(mockCacheProvider);
    }
    
    @Test
    public void requestEmailSignInFailureDelays() {
        app.setEmailSignInEnabled(true);
        service.getEmailSignInRequestInMillis().set(1000);
        when(mockAccountService.getAccount(any())).thenReturn(null);
        when(mockStudyService.getStudy(app.getIdentifier())).thenReturn(app);
                 
        long start = System.currentTimeMillis();
        service.requestEmailSignIn(SIGN_IN_REQUEST_WITH_EMAIL);
        long total = System.currentTimeMillis()-start;
        assertTrue(total >= 1000);
        service.getEmailSignInRequestInMillis().set(0);
        verifyNoMoreInteractions(mockCacheProvider);
    }    
    
    @Test(expectedExceptions = InvalidEntityException.class)
    public void emailSignInRequestMissingStudy() {
        SignIn signInRequest = new SignIn.Builder().withEmail(EMAIL).withToken(TOKEN).build();

        service.requestEmailSignIn(signInRequest);
    }
    
    @Test(expectedExceptions = InvalidEntityException.class)
    public void emailSignInRequestMissingEmail() {
        SignIn signInRequest = new SignIn.Builder().withStudy(TEST_APP_ID).withToken(TOKEN).build();
        
        service.requestEmailSignIn(signInRequest);
    }
    
    @Test(expectedExceptions = UnauthorizedException.class)
    public void requestEmailSignInDisabled() {
        app.setEmailSignInEnabled(false);
        when(mockStudyService.getStudy(app.getIdentifier())).thenReturn(app);
        
        service.requestEmailSignIn(SIGN_IN_REQUEST_WITH_EMAIL);
    }

    @Test(expectedExceptions = UnauthorizedException.class)
    public void requestPhoneSignInDisabled() {
        app.setPhoneSignInEnabled(false);
        when(mockStudyService.getStudy(app.getIdentifier())).thenReturn(app);
        
        service.requestPhoneSignIn(SIGN_IN_REQUEST_WITH_PHONE);
    }
    
    @Test
    public void requestEmailSignInThrottles() {
        app.setEmailSignInEnabled(true);
        when(service.getNextToken()).thenReturn(SPTOKEN);
        when(mockAccountService.getAccount(any())).thenReturn(mockAccount);
        when(mockStudyService.getStudy(app.getIdentifier())).thenReturn(app);

        // Throttle limit is 2. Request 3 times. Get 2 emails. (Each call should still return userId.
        String userId = service.requestEmailSignIn(SIGN_IN_REQUEST_WITH_EMAIL);
        assertEquals(userId, USER_ID);

        userId = service.requestEmailSignIn(SIGN_IN_REQUEST_WITH_EMAIL);
        assertEquals(userId, USER_ID);

        userId = service.requestEmailSignIn(SIGN_IN_REQUEST_WITH_EMAIL);
        assertEquals(userId, USER_ID);

        verify(mockSendMailService, times(2)).sendEmail(any());
    }

    @Test
    public void requestEmailSignInTwiceReturnsSameToken() throws Exception {
        // In this case, where there is a value and an account, we do't generate a new one,
        // we just send the message again.
        app.setEmailSignInEnabled(true);
        when(mockCacheProvider.getObject(EMAIL_SIGNIN_CACHE_KEY, String.class)).thenReturn(TOKEN);
        when(mockAccountService.getAccount(any())).thenReturn(mockAccount);
        when(mockStudyService.getStudy(app.getIdentifier())).thenReturn(app);
        
        service.requestEmailSignIn(SIGN_IN_REQUEST_WITH_EMAIL);
        
        verify(mockCacheProvider, never()).setObject(eq(EMAIL_SIGNIN_CACHE_KEY), any(), anyInt());
        verify(mockSendMailService).sendEmail(emailProviderCaptor.capture());
        
        BasicEmailProvider provider = emailProviderCaptor.getValue();
        assertEquals(provider.getTokenMap().get("email"), BridgeUtils.encodeURIComponent(EMAIL));
        assertEquals(provider.getTokenMap().get("token"), TOKEN);
        assertEquals(provider.getTokenMap().get("emailSignInExpirationPeriod"), "1 hour");
        
        assertEquals(provider.getMimeTypeEmail().getRecipientAddresses().get(0), EMAIL);
        assertEquals(provider.getPlainSenderEmail(), SUPPORT_EMAIL);
        String bodyString = (String)provider.getMimeTypeEmail().getMessageParts().get(0).getContent();
        assertEquals(bodyString, "Body "+TOKEN);
        
        verify(mockCacheProvider).getObject(EMAIL_SIGNIN_CACHE_KEY, String.class);
    }

    @Test
    public void requestEmailSignInEmailNotRegistered() {
        // Mock.
        app.setEmailSignInEnabled(true);
        when(mockStudyService.getStudy(app.getIdentifier())).thenReturn(app);

        // Execute. Returns null userId.
        String userId = service.requestEmailSignIn(SIGN_IN_REQUEST_WITH_EMAIL);
        assertNull(userId);

        // Verify dependent services are never called.
        verify(mockCacheProvider, never()).setObject(any(), any(), anyInt());
        verify(mockSendMailService, never()).sendEmail(any());
        verifyNoMoreInteractions(mockCacheProvider);
    }
    
    @Test
    public void requestPhoneSignIn() {
        // Mock.
        app.setPhoneSignInEnabled(true);
        app.setShortName("AppName");
        when(mockAccountService.getAccount(SIGN_IN_WITH_PHONE.getAccountId())).thenReturn(mockAccount);
        when(service.getNextPhoneToken()).thenReturn("123456");
        when(mockStudyService.getStudy(app.getIdentifier())).thenReturn(app);

        // Execute.
        String userId = service.requestPhoneSignIn(SIGN_IN_REQUEST_WITH_PHONE);
        assertEquals(userId, USER_ID);

        // Verify dependent services.
        verify(mockCacheProvider).getObject(PHONE_SIGNIN_CACHE_KEY, String.class);
        verify(mockCacheProvider).setObject(PHONE_SIGNIN_CACHE_KEY, "123456", SIGNIN_EXPIRE_IN_SECONDS);
        verify(mockSmsService).sendSmsMessage(eq(USER_ID), smsMessageProviderCaptor.capture());

        assertEquals(smsMessageProviderCaptor.getValue().getStudy(), app);
        assertEquals(smsMessageProviderCaptor.getValue().getPhone(), TestConstants.PHONE);
        assertEquals(smsMessageProviderCaptor.getValue().getSmsType(), "Transactional");
        String message = smsMessageProviderCaptor.getValue().getSmsRequest().getMessage();
        assertEquals(message, "Enter 123-456 to sign in to AppName");

        // Verify throttling cache calls.
        verify(mockCacheProvider).getObject(PHONE_SIGNIN_THROTTLE_CACHE_KEY, Integer.class);
        verify(mockCacheProvider).setObject(eq(PHONE_SIGNIN_THROTTLE_CACHE_KEY), any(), anyInt());

        verifyNoMoreInteractions(mockCacheProvider);
    }
    
    @Test
    public void requestPhoneSignInFails() {
        app.setPhoneSignInEnabled(true);
        when(mockStudyService.getStudy(app.getIdentifier())).thenReturn(app);

        // This should fail silently, or we risk giving away information about accounts in the system.
        String userId = service.requestPhoneSignIn(SIGN_IN_REQUEST_WITH_PHONE);
        assertNull(userId);

        verify(mockCacheProvider, never()).setObject(any(), any(), anyInt());
        verify(mockSmsService, never()).sendSmsMessage(any(),any());
        verifyNoMoreInteractions(mockCacheProvider);
    }

    @Test
    public void requestPhoneSignInThrottles() {
        app.setPhoneSignInEnabled(true);
        when(mockAccountService.getAccount(any())).thenReturn(mockAccount);
        when(mockStudyService.getStudy(app.getIdentifier())).thenReturn(app);

        // Throttle limit is 2. Request 3 times. Get 2 texts. (Each call should still return userId.)
        String userId = service.requestPhoneSignIn(SIGN_IN_REQUEST_WITH_PHONE);
        assertEquals(userId, USER_ID);

        userId = service.requestPhoneSignIn(SIGN_IN_REQUEST_WITH_PHONE);
        assertEquals(userId, USER_ID);

        userId = service.requestPhoneSignIn(SIGN_IN_REQUEST_WITH_PHONE);
        assertEquals(userId, USER_ID);

        verify(mockSmsService, times(2)).sendSmsMessage(any(), any());
    }

}
