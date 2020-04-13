package org.sagebionetworks.bridge.spring.controllers;

import static org.sagebionetworks.bridge.TestConstants.HEALTH_CODE;
import static org.sagebionetworks.bridge.TestConstants.SESSION_TOKEN;
import static org.sagebionetworks.bridge.TestConstants.SIGNATURE;
import static org.sagebionetworks.bridge.TestConstants.SUBPOP_GUID;
import static org.sagebionetworks.bridge.TestConstants.TEST_APP_ID;
import static org.sagebionetworks.bridge.TestConstants.TIMESTAMP;
import static org.sagebionetworks.bridge.TestConstants.USER_ID;
import static org.sagebionetworks.bridge.TestConstants.WITHDRAWAL;
import static org.sagebionetworks.bridge.TestUtils.assertCreate;
import static org.sagebionetworks.bridge.TestUtils.assertCrossOrigin;
import static org.sagebionetworks.bridge.TestUtils.assertGet;
import static org.sagebionetworks.bridge.TestUtils.assertPost;
import static org.sagebionetworks.bridge.TestUtils.createJson;
import static org.sagebionetworks.bridge.TestUtils.mockRequestBody;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertTrue;

import java.util.function.Consumer;

import javax.servlet.http.Cookie;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import com.fasterxml.jackson.databind.JsonNode;
import com.google.common.collect.ImmutableMap;

import org.joda.time.DateTime;
import org.joda.time.DateTimeUtils;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;
import org.mockito.Spy;
import org.mockito.invocation.InvocationOnMock;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import org.sagebionetworks.bridge.TestConstants;
import org.sagebionetworks.bridge.TestUtils;
import org.sagebionetworks.bridge.cache.CacheProvider;
import org.sagebionetworks.bridge.config.BridgeConfig;
import org.sagebionetworks.bridge.exceptions.EntityNotFoundException;
import org.sagebionetworks.bridge.exceptions.InvalidEntityException;
import org.sagebionetworks.bridge.json.BridgeObjectMapper;
import org.sagebionetworks.bridge.models.StatusMessage;
import org.sagebionetworks.bridge.models.accounts.Account;
import org.sagebionetworks.bridge.models.accounts.SharingScope;
import org.sagebionetworks.bridge.models.accounts.StudyParticipant;
import org.sagebionetworks.bridge.models.accounts.UserSession;
import org.sagebionetworks.bridge.models.studies.Study;
import org.sagebionetworks.bridge.models.subpopulations.ConsentSignature;
import org.sagebionetworks.bridge.models.subpopulations.SubpopulationGuid;
import org.sagebionetworks.bridge.services.AccountService;
import org.sagebionetworks.bridge.services.AuthenticationService;
import org.sagebionetworks.bridge.services.ConsentService;
import org.sagebionetworks.bridge.services.SessionUpdateService;
import org.sagebionetworks.bridge.services.StudyService;

public class ConsentControllerTest extends Mockito {

    private static final String ORIGINAL_SESSION_TOKEN = "original-session-token";
    
    @InjectMocks
    @Spy
    ConsentController controller;

    @Mock
    StudyService mockStudyService;
    
    @Mock
    ConsentService mockConsentService;
    
    @Mock
    AccountService mockAccountService;
    
    @Mock
    CacheProvider mockCacheProvider;
    
    @Mock
    AuthenticationService mockAuthService;
    
    @Mock
    BridgeConfig mockConfig;
    
    @Mock
    SessionUpdateService mockSessionUpdateService;
    
    @Mock
    HttpServletRequest mockRequest;
    
    @Mock
    HttpServletResponse mockResponse;
    
    @Captor
    ArgumentCaptor<Consumer<Account>> accountConsumerCaptor;
    
    UserSession session;
    
    Study study;
    
    UserSession updatedSession;
    
    @BeforeMethod
    public void before() {
        MockitoAnnotations.initMocks(this);
        DateTimeUtils.setCurrentMillisFixed(TIMESTAMP.getMillis());
        
        study = Study.create();
        study.setIdentifier(TEST_APP_ID);
        when(mockStudyService.getStudy(TEST_APP_ID)).thenReturn(study);

        StudyParticipant participant = new StudyParticipant.Builder()
                .withHealthCode(HEALTH_CODE).withId(USER_ID).build();
        session = new UserSession(participant);
        session.setStudyIdentifier(TEST_APP_ID);
        session.setSessionToken(ORIGINAL_SESSION_TOKEN);
        
        // The session token is just a marker to verify that we have retrieved an updated session.
        updatedSession = new UserSession();
        updatedSession.setSessionToken(SESSION_TOKEN);
        
        doReturn(session).when(controller).getAuthenticatedAndConsentedSession();
        doReturn(mockRequest).when(controller).request();
        doReturn(mockResponse).when(controller).response();
    }

    @AfterMethod
    public void after() {
        DateTimeUtils.setCurrentMillisSystem();
    }
    
    @Test
    public void verifyAnnotations() throws Exception {
        assertCrossOrigin(ConsentController.class);
        assertGet(ConsentController.class, "getConsentSignature");
        assertCreate(ConsentController.class, "giveV1");
        assertCreate(ConsentController.class, "giveV2");
        assertPost(ConsentController.class, "emailCopy");
        assertPost(ConsentController.class, "suspendDataSharing");
        assertPost(ConsentController.class, "resumeDataSharing");
        assertPost(ConsentController.class, "changeSharingScope");
        assertPost(ConsentController.class, "withdrawConsent");
        assertGet(ConsentController.class, "getConsentSignatureV2");
        assertCreate(ConsentController.class, "giveV3");
        assertPost(ConsentController.class, "withdrawConsentV2");
        assertPost(ConsentController.class, "withdrawFromStudy");
        assertPost(ConsentController.class, "resendConsentAgreement");
    }
    
    @Test
    public void changeSharingScope() throws Exception {
        mockRequestBody(mockRequest, "{\"scope\":\"all_qualified_researchers\"}");
        
        JsonNode result = controller.changeSharingScope();
        
        // Session is edited by sessionUpdateService, not reloaded
        UserSession retrievedSession = BridgeObjectMapper.get().treeToValue(result, UserSession.class);
        assertEquals(retrievedSession.getSessionToken(), ORIGINAL_SESSION_TOKEN);
        
        verify(mockAccountService).editAccount(eq(TEST_APP_ID), eq(HEALTH_CODE), accountConsumerCaptor.capture());
        verify(mockSessionUpdateService).updateSharingScope(session, SharingScope.ALL_QUALIFIED_RESEARCHERS);
        
        // This works as a verification because the lambda carries a closure that includes the correct sharing 
        // scope... so re-executing it should do what we expect and set the correct sharing scope.
        Consumer<Account> accountConsumer = accountConsumerCaptor.getValue();
        Account account = mock(Account.class);
        accountConsumer.accept(account);
        verify(account).setSharingScope(SharingScope.ALL_QUALIFIED_RESEARCHERS);
    }
    
    @Test(expectedExceptions = InvalidEntityException.class)
    public void changeSharingScopeNoPayload() throws Exception {
        controller.changeSharingScope();
    }
    
    @SuppressWarnings("deprecation")
    @Test
    public void getConsentSignatureV1() throws Exception {
        SubpopulationGuid defaultGuid = SubpopulationGuid.create(TEST_APP_ID);
        when(mockConsentService.getConsentSignature(study, defaultGuid, USER_ID)).thenReturn(SIGNATURE);
        
        String result = controller.getConsentSignature();
        
        ConsentSignature retrieved = BridgeObjectMapper.get().readValue(result, ConsentSignature.class);
        assertEquals(SIGNATURE.getName(), retrieved.getName());
        assertEquals(SIGNATURE.getBirthdate(), retrieved.getBirthdate());
        
        verify(mockConsentService).getConsentSignature(study, defaultGuid, USER_ID);
    }
    
    @Test
    public void getConsentSignatureV2() throws Exception {
        when(mockConsentService.getConsentSignature(study, SUBPOP_GUID, USER_ID)).thenReturn(SIGNATURE);
        
        String result = controller.getConsentSignatureV2(SUBPOP_GUID.getGuid());
        
        ConsentSignature retrieved = BridgeObjectMapper.get().readValue(result, ConsentSignature.class);
        assertEquals(SIGNATURE.getName(), retrieved.getName());
        assertEquals(SIGNATURE.getBirthdate(), retrieved.getBirthdate());
        
        verify(mockConsentService).getConsentSignature(study, SUBPOP_GUID, USER_ID);
    }
    
    @SuppressWarnings("deprecation")
    @Test
    public void giveV1() throws Exception {
        String json = createJson("{'name':'Jack Aubrey','birthdate':'1970-10-10',"+
                "'imageData':'data:asdf','imageMimeType':'image/png','scope':'no_sharing'}");
        mockRequestBody(mockRequest, json);
        
        String studyId = TestConstants.REQUIRED_UNSIGNED.getSubpopulationGuid();
        
        // Need to adjust the study session to match the subpopulation in the unconsented status map
        session.setStudyIdentifier(studyId);
        when(mockAuthService.getSession(any(), any())).thenReturn(updatedSession);
        doReturn(session).when(controller).getAuthenticatedSession();
        when(mockConsentService.getConsentStatuses(any())).thenReturn(TestConstants.UNCONSENTED_STATUS_MAP);
        when(mockStudyService.getStudy(studyId)).thenReturn(study);
        
        JsonNode result = controller.giveV1();
        
        // Session is recreated from scratch, verify it is retrieved and returned
        UserSession retrievedSession = BridgeObjectMapper.get().treeToValue(result, UserSession.class);
        assertEquals(SESSION_TOKEN, retrievedSession.getSessionToken());
        
        verify(mockConsentService).consentToResearch(study, SUBPOP_GUID, session.getParticipant(), 
                SIGNATURE, SharingScope.NO_SHARING, true);
        verify(mockSessionUpdateService).updateSession(session, updatedSession);
    }
    
    @SuppressWarnings("deprecation")
    @Test
    public void giveV2() throws Exception {
        String json = createJson("{'name':'Jack Aubrey','birthdate':'1970-10-10',"+
                "'imageData':'data:asdf','imageMimeType':'image/png','scope':'no_sharing'}");
        mockRequestBody(mockRequest, json);
        
        String studyId = TestConstants.REQUIRED_UNSIGNED.getSubpopulationGuid();
        
        // Need to adjust the study session to match the subpopulation in the unconsented status map
        session.setStudyIdentifier(studyId);
        when(mockAuthService.getSession(any(), any())).thenReturn(updatedSession);
        doReturn(session).when(controller).getAuthenticatedSession();
        when(mockConsentService.getConsentStatuses(any())).thenReturn(TestConstants.UNCONSENTED_STATUS_MAP);
        when(mockStudyService.getStudy(studyId)).thenReturn(study);
        
        JsonNode result = controller.giveV2();
        
        // Session is recreated from scratch, verify it is retrieved and returned
        UserSession retrievedSession = BridgeObjectMapper.get().treeToValue(result, UserSession.class);
        assertEquals(SESSION_TOKEN, retrievedSession.getSessionToken());
        
        verify(mockConsentService).consentToResearch(study, SUBPOP_GUID, session.getParticipant(), 
                SIGNATURE, SharingScope.NO_SHARING, true);
        verify(mockSessionUpdateService).updateSession(session, updatedSession);
    }
    
    @Test
    public void giveV3() throws Exception {
        DateTime badSignedOn = TIMESTAMP.minusHours(1);
        
        String json = createJson("{'name':'Jack Aubrey','birthdate':'1970-10-10',"+
                "'imageData':'data:asdf','imageMimeType':'image/png','scope':'no_sharing',"+
                "'signedOn': '" + badSignedOn.toString() + "'}");
        mockRequestBody(mockRequest, json);
        
        when(mockAuthService.getSession(any(), any())).thenReturn(updatedSession);
        doReturn(session).when(controller).getAuthenticatedSession();
        
        when(mockConsentService.getConsentStatuses(any())).thenReturn(TestConstants.UNCONSENTED_STATUS_MAP);
        
        JsonNode result = controller.giveV3(SUBPOP_GUID.getGuid());
        
        // Session is recreated from scratch, verify it is retrieved and returned
        UserSession retrievedSession = BridgeObjectMapper.get().treeToValue(result, UserSession.class);
        assertEquals(SESSION_TOKEN, retrievedSession.getSessionToken());
        
        verify(mockConsentService).consentToResearch(study, SUBPOP_GUID, session.getParticipant(), 
                SIGNATURE, SharingScope.NO_SHARING, true);
        verify(mockSessionUpdateService).updateSession(session, updatedSession);
    }
    
    @Test(expectedExceptions = EntityNotFoundException.class)
    public void givingConsentToInvalidSubpopulation() throws Exception {
        String json = createJson("{'name':'Jack Aubrey','birthdate':'1970-10-10','imageData':'data:asdf',"+
                "'imageMimeType':'image/png','scope':'no_sharing'}");
        mockRequestBody(mockRequest, json);
        
        doReturn(session).when(controller).getAuthenticatedSession();
        
        // It will not find the correct subpopulation.
        when(mockConsentService.getConsentStatuses(any())).thenReturn(ImmutableMap.of());
        
        controller.giveV3("bad-guid");
    }    
    
    @SuppressWarnings("deprecation")
    @Test
    public void withdrawConsent() throws Exception {
        SubpopulationGuid defaultGuid = SubpopulationGuid.create(TEST_APP_ID);
        mockRequestBody(mockRequest, WITHDRAWAL);
        
        // You do not need to be fully consented for this call to succeed. Nothing should prevent
        // this call from succeeding unless it's absolutely necessary (see BRIDGE-2418 about 
        // removing the requirement that a withdrawal reason be submitted).
        doReturn(session).when(controller).getAuthenticatedSession();
        when(mockAuthService.getSession(any(), any())).thenReturn(updatedSession);
        session.setConsentStatuses(TestConstants.UNCONSENTED_STATUS_MAP);

        JsonNode result = controller.withdrawConsent();
        
        UserSession retrievedSession = BridgeObjectMapper.get().treeToValue(result, UserSession.class);
        // This call recreates the session from scratch
        assertEquals(SESSION_TOKEN, retrievedSession.getSessionToken());
        
        verify(mockConsentService).withdrawConsent(eq(study), eq(defaultGuid), eq(session.getParticipant()), any(),
                eq(WITHDRAWAL), eq(TIMESTAMP.getMillis()));
        verify(mockSessionUpdateService).updateSession(session, updatedSession);
    }
    
    @Test
    public void withdrawConsentV2() throws Exception {
        mockRequestBody(mockRequest, WITHDRAWAL);
        
        // You do not need to be fully consented for this call to succeed. Nothing should prevent
        // this call from succeeding unless it's absolutely necessary (see BRIDGE-2418 about 
        // removing the requirement that a withdrawal reason be submitted).
        doReturn(session).when(controller).getAuthenticatedSession();
        when(mockAuthService.getSession(any(), any())).thenReturn(updatedSession);
        session.setConsentStatuses(TestConstants.UNCONSENTED_STATUS_MAP);

        JsonNode result = controller.withdrawConsentV2(SUBPOP_GUID.getGuid());
        
        UserSession retrievedSession = BridgeObjectMapper.get().treeToValue(result, UserSession.class);
        // This call recreates the session from scratch
        assertEquals(SESSION_TOKEN, retrievedSession.getSessionToken());
        
        verify(mockConsentService).withdrawConsent(eq(study), eq(SUBPOP_GUID), eq(session.getParticipant()), any(),
                eq(WITHDRAWAL), eq(TIMESTAMP.getMillis()));
        verify(mockSessionUpdateService).updateSession(session, updatedSession);
    }
    
    @Test
    public void withdrawFromStudy() throws Exception {
        mockRequestBody(mockRequest, WITHDRAWAL);

        when(mockConfig.get("domain")).thenReturn("domain");
        
        // You do not need to be fully consented for this call to succeed.
        doReturn(session).when(controller).getAuthenticatedSession();
        
        StatusMessage result = controller.withdrawFromStudy();
        
        assertEquals(result.getMessage(), "Signed out.");
        
        verify(mockConsentService).withdrawFromStudy(study, session.getParticipant(), WITHDRAWAL, TIMESTAMP.getMillis());
        verify(mockAuthService).signOut(session);

        ArgumentCaptor<Cookie> cookieCaptor = ArgumentCaptor.forClass(Cookie.class);
        verify(mockResponse).addCookie(cookieCaptor.capture());
        
        Cookie cookie = cookieCaptor.getValue();
        assertEquals(cookie.getValue(), "");
        assertEquals(cookie.getMaxAge(), 0);
    }

    @Test
    public void resendConsentAgreement() throws Exception {
        StatusMessage result = controller.resendConsentAgreement(SUBPOP_GUID.getGuid());
        
        assertEquals(result.getMessage(), "Signed consent agreement resent.");
        
        verify(mockConsentService).resendConsentAgreement(study, SUBPOP_GUID, session.getParticipant());        
    }
    
    @Test
    @SuppressWarnings("deprecation")
    public void dataSharingSuspendedUpdatesSession() throws Exception {
        Account account = Mockito.mock(Account.class);
        TestUtils.mockEditAccount(mockAccountService, account);
        
        doAnswer((InvocationOnMock invocation) -> {
            session.setParticipant(new StudyParticipant.Builder().copyOf(session.getParticipant())
                    .withSharingScope(SharingScope.NO_SHARING).build());
            return null;
        }).when(mockSessionUpdateService).updateSharingScope(session, SharingScope.NO_SHARING);

        JsonNode result = controller.suspendDataSharing();

        assertEquals(result.get("sharingScope").asText(), "no_sharing");
        assertFalse(result.get("dataSharing").asBoolean());

        verify(account).setSharingScope(SharingScope.NO_SHARING);
        
        verify(mockAccountService).editAccount(eq(TEST_APP_ID), eq(HEALTH_CODE), any());
    }
    
    @Test
    @SuppressWarnings("deprecation")
    public void dataSharingResumedUpdatesSession() throws Exception {
        Account account = Mockito.mock(Account.class);
        TestUtils.mockEditAccount(mockAccountService, account);

        doAnswer((InvocationOnMock invocation) -> {
            session.setParticipant(new StudyParticipant.Builder().copyOf(session.getParticipant())
                    .withSharingScope(SharingScope.SPONSORS_AND_PARTNERS).build());
            return null;
        }).when(mockSessionUpdateService).updateSharingScope(session, SharingScope.SPONSORS_AND_PARTNERS);
        
        JsonNode result = controller.resumeDataSharing();

        assertEquals(result.get("sharingScope").asText(), "sponsors_and_partners");
        assertTrue(result.get("dataSharing").asBoolean());
        
        verify(account).setSharingScope(SharingScope.SPONSORS_AND_PARTNERS);
    }
}
