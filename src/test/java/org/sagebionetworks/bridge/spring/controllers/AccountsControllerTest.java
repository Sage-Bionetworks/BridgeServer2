package org.sagebionetworks.bridge.spring.controllers;

import static org.sagebionetworks.bridge.RequestContext.NULL_INSTANCE;
import static org.sagebionetworks.bridge.Roles.ADMIN;
import static org.sagebionetworks.bridge.Roles.DEVELOPER;
import static org.sagebionetworks.bridge.Roles.ORG_ADMIN;
import static org.sagebionetworks.bridge.Roles.RESEARCHER;
import static org.sagebionetworks.bridge.Roles.SUPERADMIN;
import static org.sagebionetworks.bridge.TestConstants.EMAIL;
import static org.sagebionetworks.bridge.TestConstants.PASSWORD;
import static org.sagebionetworks.bridge.TestConstants.PHONE;
import static org.sagebionetworks.bridge.TestConstants.SYNAPSE_USER_ID;
import static org.sagebionetworks.bridge.TestConstants.TEST_APP_ID;
import static org.sagebionetworks.bridge.TestConstants.TEST_ORG_ID;
import static org.sagebionetworks.bridge.TestConstants.TEST_USER_ID;
import static org.sagebionetworks.bridge.TestUtils.assertCreate;
import static org.sagebionetworks.bridge.TestUtils.assertDelete;
import static org.sagebionetworks.bridge.TestUtils.assertGet;
import static org.sagebionetworks.bridge.TestUtils.assertPost;
import static org.sagebionetworks.bridge.TestUtils.mockRequestBody;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertNull;
import static org.testng.Assert.assertSame;

import java.util.Set;

import javax.servlet.http.HttpServletRequest;

import com.fasterxml.jackson.databind.JsonNode;
import com.google.common.collect.ImmutableSet;

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

import org.sagebionetworks.bridge.RequestContext;
import org.sagebionetworks.bridge.Roles;
import org.sagebionetworks.bridge.cache.CacheProvider;
import org.sagebionetworks.bridge.exceptions.EntityNotFoundException;
import org.sagebionetworks.bridge.exceptions.NotAuthenticatedException;
import org.sagebionetworks.bridge.exceptions.UnauthorizedException;
import org.sagebionetworks.bridge.models.AccountSummarySearch;
import org.sagebionetworks.bridge.models.CriteriaContext;
import org.sagebionetworks.bridge.models.RequestInfo;
import org.sagebionetworks.bridge.models.StatusMessage;
import org.sagebionetworks.bridge.models.accounts.Account;
import org.sagebionetworks.bridge.models.accounts.AccountId;
import org.sagebionetworks.bridge.models.accounts.IdentifierHolder;
import org.sagebionetworks.bridge.models.accounts.IdentifierUpdate;
import org.sagebionetworks.bridge.models.accounts.SignIn;
import org.sagebionetworks.bridge.models.accounts.StudyParticipant;
import org.sagebionetworks.bridge.models.accounts.UserSession;
import org.sagebionetworks.bridge.models.apps.App;
import org.sagebionetworks.bridge.services.AccountService;
import org.sagebionetworks.bridge.services.AppService;
import org.sagebionetworks.bridge.services.NotificationTopicService;
import org.sagebionetworks.bridge.services.AuthenticationService.ChannelType;
import org.sagebionetworks.bridge.services.ConsentService;
import org.sagebionetworks.bridge.services.OrganizationService;
import org.sagebionetworks.bridge.services.ParticipantService;
import org.sagebionetworks.bridge.services.RequestInfoService;
import org.sagebionetworks.bridge.services.SessionUpdateService;
import org.sagebionetworks.bridge.services.UserAdminService;

public class AccountsControllerTest extends Mockito {
    
    private static final IdentifierHolder ID = new IdentifierHolder(TEST_USER_ID);
    private static final AccountId ACCOUNT_ID = AccountId.forId(TEST_APP_ID, TEST_USER_ID);
    private static final Set<Roles> CALLER_ROLES = ImmutableSet.of(RESEARCHER);
    private static final Set<String> CALLER_STUDIES = ImmutableSet.of("studyA");
    private static final SignIn EMAIL_PASSWORD_SIGN_IN_REQUEST = new SignIn.Builder()
            .withAppId(TEST_APP_ID).withEmail(EMAIL)
            .withPassword(PASSWORD).build();
    private static final SignIn PHONE_PASSWORD_SIGN_IN_REQUEST = new SignIn.Builder()
            .withAppId(TEST_APP_ID).withPhone(PHONE)
            .withPassword(PASSWORD).build();    
    private static final IdentifierUpdate PHONE_UPDATE = new IdentifierUpdate(EMAIL_PASSWORD_SIGN_IN_REQUEST, null,
            PHONE, null);
    private static final IdentifierUpdate EMAIL_UPDATE = new IdentifierUpdate(PHONE_PASSWORD_SIGN_IN_REQUEST,
            EMAIL, null, null);
    private static final IdentifierUpdate SYNAPSE_ID_UPDATE = new IdentifierUpdate(EMAIL_PASSWORD_SIGN_IN_REQUEST, null,
            null, SYNAPSE_USER_ID);

    @Mock
    OrganizationService mockOrganizationService;
    
    @Mock
    ParticipantService mockParticipantService;
    
    @Mock
    UserAdminService mockUserAdminService;
    
    @Mock
    AccountService mockAccountService;
    
    @Mock
    AppService mockAppService;
    
    @Mock
    RequestInfoService mockRequestInfoService;

    @Mock
    CacheProvider mockCacheProvider;
    
    @Mock
    HttpServletRequest mockRequest;
    
    @Captor
    ArgumentCaptor<AccountSummarySearch> searchCaptor;
    
    @Captor
    ArgumentCaptor<StudyParticipant> participantCaptor;
    
    @Captor
    ArgumentCaptor<AccountId> accountIdCaptor;

    @Captor
    ArgumentCaptor<CriteriaContext> contextCaptor;

    @Captor
    ArgumentCaptor<IdentifierUpdate> identifierUpdateCaptor;

    @Captor
    ArgumentCaptor<UserSession> sessionCaptor;

    @InjectMocks
    @Spy
    AccountsController controller;
    
    Account account;
    
    UserSession session;
    
    App app;
    
    @BeforeMethod
    public void beforeMethod() {
        MockitoAnnotations.initMocks(this);
        
        doReturn(mockRequest).when(controller).request();
        
        SessionUpdateService sessionUpdateService = new SessionUpdateService();
        sessionUpdateService.setCacheProvider(mockCacheProvider);
        sessionUpdateService.setConsentService(mock(ConsentService.class));
        sessionUpdateService.setNotificationTopicService(mock(NotificationTopicService.class));
        controller.setSessionUpdateService(sessionUpdateService);
        
        app = App.create();
        when(mockAppService.getApp(TEST_APP_ID)).thenReturn(app);     
        
        account = Account.create();

        session = new UserSession();
        session.setAppId(TEST_APP_ID);
        session.setParticipant(new StudyParticipant.Builder().withOrgMembership(TEST_ORG_ID).build());
    }
    
    @AfterMethod
    public void afterMethod() {
        RequestContext.set(NULL_INSTANCE);
    }
    
    @Test
    public void verifyAnnotations() throws Exception { 
        assertCreate(AccountsController.class, "createAccount");
        assertGet(AccountsController.class, "getAccount");
        assertPost(AccountsController.class, "updateAccount");
        assertDelete(AccountsController.class, "deleteAccount");
        assertGet(AccountsController.class, "getRequestInfo");
        assertPost(AccountsController.class, "requestResetPassword");
        assertPost(AccountsController.class, "resendEmailVerification");
        assertPost(AccountsController.class, "resendPhoneVerification");
        assertPost(AccountsController.class, "signOut");
        assertPost(AccountsController.class, "updateIdentifiers");
    }
    
    @Test
    public void createAccount() throws Exception {
        RequestContext.set(new RequestContext.Builder()
                .withCallerOrgMembership(TEST_ORG_ID)
                .withCallerRoles(ImmutableSet.of(ORG_ADMIN)).build());
        doReturn(session).when(controller).getAuthenticatedSession(ORG_ADMIN, ADMIN);
        when(mockParticipantService.createParticipant(any(), any(), anyBoolean())).thenReturn(ID);
        
        when(mockAccountService.getAccount(ACCOUNT_ID)).thenReturn(account);
        
        StudyParticipant participant = new StudyParticipant.Builder()
                .withFirstName("firstName")
                .withOrgMembership("wrong-membership").build();
        mockRequestBody(mockRequest, participant);
        
        IdentifierHolder retValue = controller.createAccount();
        assertEquals(retValue.getIdentifier(), TEST_USER_ID);
        
        verify(mockParticipantService)
            .createParticipant(eq(app), participantCaptor.capture(), eq(true));
        
        StudyParticipant submitted = participantCaptor.getValue();
        assertEquals(submitted.getOrgMembership(), TEST_ORG_ID);
        assertEquals(submitted.getFirstName(), "firstName");
    }
    
    @Test
    public void getAccount() throws Exception {
        RequestContext.set(new RequestContext.Builder()
                .withCallerOrgMembership(TEST_ORG_ID)
                .withCallerRoles(ImmutableSet.of(ORG_ADMIN)).build());
        doReturn(session).when(controller).getAuthenticatedSession(DEVELOPER, ORG_ADMIN, ADMIN);
        
        account.setOrgMembership(TEST_ORG_ID);
        when(mockAccountService.getAccount(ACCOUNT_ID)).thenReturn(account);

        Account retValue = controller.getAccount(TEST_USER_ID);
        assertSame(retValue, account);
    }
    
    @Test
    public void updateAccount() throws Exception {
        RequestContext.set(new RequestContext.Builder()
                .withCallerOrgMembership(TEST_ORG_ID)
                .withCallerRoles(ImmutableSet.of(ORG_ADMIN)).build());
        doReturn(session).when(controller).getAuthenticatedSession(DEVELOPER, ORG_ADMIN, ADMIN);
        
        session.setParticipant(new StudyParticipant.Builder().withOrgMembership(TEST_ORG_ID).build());
        
        account.setOrgMembership(TEST_ORG_ID);
        when(mockAccountService.getAccount(ACCOUNT_ID)).thenReturn(account);
        
        StudyParticipant submitted = new StudyParticipant.Builder()
                .withOrgMembership("bad-membership")
                .withId("bad-id").build();
        mockRequestBody(mockRequest, submitted);

        StudyParticipant persisted = new StudyParticipant.Builder()
                .withId(TEST_USER_ID)
                .withOrgMembership(TEST_ORG_ID).build();
        when(mockParticipantService.getParticipant(app, account, false)).thenReturn(persisted);
        
        StatusMessage retValue = controller.updateAccount(TEST_USER_ID);
        assertEquals(retValue.getMessage(), "Member updated.");
        
        verify(mockParticipantService).updateParticipant(eq(app), participantCaptor.capture());
        assertEquals(participantCaptor.getValue().getId(), TEST_USER_ID);
        assertEquals(participantCaptor.getValue().getOrgMembership(), TEST_ORG_ID);
    }
    
    @Test
    public void deleteAccount() throws Exception {
        RequestContext.set(new RequestContext.Builder()
                .withCallerOrgMembership(TEST_ORG_ID)
                .withCallerRoles(ImmutableSet.of(ORG_ADMIN)).build());
        doReturn(session).when(controller).getAuthenticatedSession(ORG_ADMIN, ADMIN);
        
        account.setOrgMembership(TEST_ORG_ID);
        when(mockAccountService.getAccount(ACCOUNT_ID)).thenReturn(account);
        
        StudyParticipant existing = new StudyParticipant.Builder()
                .withOrgMembership(TEST_ORG_ID)
                .build();
        when(mockParticipantService.getParticipant(app, TEST_USER_ID, false)).thenReturn(existing);

        StatusMessage retValue = controller.deleteAccount(TEST_USER_ID);
        assertEquals(retValue.getMessage(), "Member account deleted.");
        
        verify(mockUserAdminService).deleteUser(app, TEST_USER_ID);
    }
    
    @Test
    public void getRequestInfo() throws Exception {
        RequestContext.set(new RequestContext.Builder()
                .withCallerOrgMembership(TEST_ORG_ID)
                .withCallerRoles(ImmutableSet.of(ORG_ADMIN)).build());
        
        doReturn(session).when(controller).getAuthenticatedSession(DEVELOPER, ORG_ADMIN, ADMIN);
        
        account.setOrgMembership(TEST_ORG_ID);
        when(mockAccountService.getAccount(ACCOUNT_ID)).thenReturn(account);
        
        RequestInfo requestInfo = new RequestInfo.Builder().withAppId(TEST_APP_ID).build();
        when(mockRequestInfoService.getRequestInfo(TEST_USER_ID)).thenReturn(requestInfo);
        
        String retValue = controller.getRequestInfo(TEST_USER_ID);
        assertEquals(retValue, RequestInfo.REQUEST_INFO_WRITER.writeValueAsString(requestInfo));
    }
    
    @Test
    public void getRequestInfoIsNull() throws Exception {
        RequestContext.set(new RequestContext.Builder()
                .withCallerOrgMembership(TEST_ORG_ID)
                .withCallerRoles(ImmutableSet.of(ORG_ADMIN)).build());
        
        doReturn(session).when(controller).getAuthenticatedSession(DEVELOPER, ORG_ADMIN, ADMIN);
        
        account.setOrgMembership(TEST_ORG_ID);
        when(mockAccountService.getAccount(ACCOUNT_ID)).thenReturn(account);
        
        when(mockRequestInfoService.getRequestInfo(TEST_USER_ID)).thenReturn(null);
        
        String retValue = controller.getRequestInfo(TEST_USER_ID);
        assertEquals(retValue, RequestInfo.REQUEST_INFO_WRITER
                .writeValueAsString(new RequestInfo.Builder().build()));
    }
    
    @Test
    public void requestResetPassword() throws Exception {
        RequestContext.set(new RequestContext.Builder()
                .withCallerOrgMembership(TEST_ORG_ID)
                .withCallerRoles(ImmutableSet.of(ORG_ADMIN)).build());
        
        doReturn(session).when(controller).getAuthenticatedSession(DEVELOPER, ORG_ADMIN, ADMIN);
        
        account.setOrgMembership(TEST_ORG_ID);
        when(mockAccountService.getAccount(ACCOUNT_ID)).thenReturn(account);

        StatusMessage retValue = controller.requestResetPassword(TEST_USER_ID);
        assertEquals(retValue.getMessage(), "Request to reset password sent to user.");
        
        verify(mockParticipantService).requestResetPassword(app, TEST_USER_ID);
    }
    
    @Test
    public void resendEmailVerification() throws Exception {
        RequestContext.set(new RequestContext.Builder()
                .withCallerOrgMembership(TEST_ORG_ID)
                .withCallerRoles(ImmutableSet.of(ORG_ADMIN)).build());
        
        doReturn(session).when(controller).getAuthenticatedSession(DEVELOPER, ORG_ADMIN, ADMIN);
        
        account.setOrgMembership(TEST_ORG_ID);
        when(mockAccountService.getAccount(ACCOUNT_ID)).thenReturn(account);
        
        controller.resendEmailVerification(TEST_USER_ID);
        
        verify(mockParticipantService).resendVerification(app, ChannelType.EMAIL, TEST_USER_ID);
    }
    
    @Test
    public void resendPhoneVerification() throws Exception {
        RequestContext.set(new RequestContext.Builder()
                .withCallerOrgMembership(TEST_ORG_ID)
                .withCallerRoles(ImmutableSet.of(ORG_ADMIN)).build());
        
        doReturn(session).when(controller).getAuthenticatedSession(DEVELOPER, ORG_ADMIN, ADMIN);
        
        account.setOrgMembership(TEST_ORG_ID);
        when(mockAccountService.getAccount(ACCOUNT_ID)).thenReturn(account);
        
        controller.resendPhoneVerification(TEST_USER_ID);
        
        verify(mockParticipantService).resendVerification(app, ChannelType.PHONE, TEST_USER_ID);        
    }
    
    @Test
    public void signOut() throws Exception {
        RequestContext.set(new RequestContext.Builder()
                .withCallerOrgMembership(TEST_ORG_ID)
                .withCallerRoles(ImmutableSet.of(ORG_ADMIN)).build());
        
        doReturn(session).when(controller).getAuthenticatedSession(DEVELOPER, ORG_ADMIN, ADMIN);
        
        account.setOrgMembership(TEST_ORG_ID);
        when(mockAccountService.getAccount(ACCOUNT_ID)).thenReturn(account);
        
        controller.signOut(TEST_USER_ID, true);
        
        verify(mockParticipantService).signUserOut(app, TEST_USER_ID, true);
    }
    
    @Test
    public void verifyOrgAdminIsActingOnOrgMemberSucceeds() throws Exception {
        RequestContext.set(new RequestContext.Builder()
                .withCallerAppId(TEST_APP_ID)
                .withCallerOrgMembership(TEST_ORG_ID)
                .withCallerRoles(ImmutableSet.of(ORG_ADMIN)).build());
        
        Account account = Account.create();
        account.setOrgMembership(TEST_ORG_ID);
        
        when(mockAccountService.getAccount(ACCOUNT_ID)).thenReturn(account);
        
        Account retValue = controller.verifyOrgAdminIsActingOnOrgMember(session, TEST_USER_ID);
        assertSame(retValue, account);
    }
    
    @Test(expectedExceptions = EntityNotFoundException.class, 
            expectedExceptionsMessageRegExp = "Account not found.")
    public void verifyOrgAdminIsActingOnOrgMemberFailsAccountNotFound() throws Exception {
        AccountService mockAccountService = mock(AccountService.class);
        when(mockAccountService.getAccount(ACCOUNT_ID)).thenReturn(null);
        
        controller.verifyOrgAdminIsActingOnOrgMember(session, TEST_USER_ID);
    }

    @Test(expectedExceptions = EntityNotFoundException.class, 
            expectedExceptionsMessageRegExp = "Account not found.")
    public void verifyOrgAdminIsActingOnOrgMemberFailsNotOrgAdmin() {
        RequestContext.set(new RequestContext.Builder()
                .withCallerUserId("callerUserId")
                .withCallerAppId(TEST_APP_ID)
                .withCallerOrgMembership(TEST_ORG_ID).build());
        
        Account account = Account.create();
        account.setOrgMembership(TEST_ORG_ID);
        
        when(mockAccountService.getAccount(ACCOUNT_ID)).thenReturn(account);
        
        controller.verifyOrgAdminIsActingOnOrgMember(session, TEST_USER_ID);
    }

    @Test(expectedExceptions = UnauthorizedException.class)
    public void verifyOrgAdminIsActingOnOrgAccountNotInOrg() {
        RequestContext.set(new RequestContext.Builder()
                .withCallerAppId(TEST_APP_ID)
                .withCallerOrgMembership(TEST_ORG_ID)
                .withCallerRoles(ImmutableSet.of(ORG_ADMIN)).build());
        
        Account account = Account.create();
        account.setOrgMembership("different-organization");
        when(mockAccountService.getAccount(ACCOUNT_ID)).thenReturn(account);
        
        controller.verifyOrgAdminIsActingOnOrgMember(session, TEST_USER_ID);
    }
    
    @Test(expectedExceptions = UnauthorizedException.class)
    public void verifyOrgAdminIsActingOnOrgMemberNotAnOrgMember() {
        session.setParticipant(new StudyParticipant.Builder()
                .withOrgMembership(null).build());
        
        when(mockAccountService.getAccount(ACCOUNT_ID)).thenReturn(account);
        
        controller.verifyOrgAdminIsActingOnOrgMember(session, TEST_USER_ID);
    }
    
    @Test
    public void verifySuperadminCanAccessAccount() {
        // Not part of the target organization, but it doesn't matter
        session.setParticipant(new StudyParticipant.Builder()
                .withRoles(ImmutableSet.of(SUPERADMIN))
                .withOrgMembership(null).build());
        
        when(mockAccountService.getAccount(ACCOUNT_ID)).thenReturn(account);
        
        Account retValue = controller.verifyOrgAdminIsActingOnOrgMember(session, TEST_USER_ID);
        assertEquals(retValue, account);
    }
    
    @Test
    public void verifyAdminCanAccessAccount() {
        // Not part of the target organization, but it doesn't matter
        session.setParticipant(new StudyParticipant.Builder()
                .withRoles(ImmutableSet.of(ADMIN))
                .withOrgMembership(null).build());
        
        when(mockAccountService.getAccount(ACCOUNT_ID)).thenReturn(account);
        
        Account retValue = controller.verifyOrgAdminIsActingOnOrgMember(session, TEST_USER_ID);
        assertEquals(retValue, account);
    }
    
    @Test
    public void updateIdentifiersWithPhone() throws Exception {
        mockRequestBody(mockRequest, PHONE_UPDATE);
        
        doReturn(session).when(controller).getAuthenticatedSession();
        
        StudyParticipant participant = new StudyParticipant.Builder().withRoles(CALLER_ROLES).withStudyIds(CALLER_STUDIES)
                .withId(TEST_USER_ID).build();

        when(mockParticipantService.updateIdentifiers(eq(app), any(), any())).thenReturn(participant);

        JsonNode result = controller.updateIdentifiers();

        assertEquals(result.get("id").textValue(), TEST_USER_ID);

        verify(mockParticipantService).updateIdentifiers(eq(app), contextCaptor.capture(),
                identifierUpdateCaptor.capture());
        verify(mockCacheProvider).setUserSession(sessionCaptor.capture());
        assertEquals(sessionCaptor.getValue().getId(), participant.getId());

        IdentifierUpdate update = identifierUpdateCaptor.getValue();
        assertEquals(update.getSignIn().getEmail(), EMAIL_PASSWORD_SIGN_IN_REQUEST.getEmail());
        assertEquals(update.getSignIn().getPassword(), EMAIL_PASSWORD_SIGN_IN_REQUEST.getPassword());
        assertEquals(update.getPhoneUpdate(), PHONE);
        assertNull(update.getEmailUpdate());
    }

    @Test
    public void updateIdentifiersWithEmail() throws Exception {
        mockRequestBody(mockRequest, EMAIL_UPDATE);
        
        doReturn(session).when(controller).getAuthenticatedSession();
        
        StudyParticipant participant = new StudyParticipant.Builder().withRoles(CALLER_ROLES).withStudyIds(CALLER_STUDIES)
                .withId(TEST_USER_ID).build();
        
        when(mockParticipantService.updateIdentifiers(eq(app), any(), any())).thenReturn(participant);

        JsonNode result = controller.updateIdentifiers();

        assertEquals(result.get("id").textValue(), TEST_USER_ID);

        verify(mockParticipantService).updateIdentifiers(eq(app), contextCaptor.capture(),
                identifierUpdateCaptor.capture());

        IdentifierUpdate update = identifierUpdateCaptor.getValue();
        assertEquals(update.getSignIn().getPhone(), PHONE_PASSWORD_SIGN_IN_REQUEST.getPhone());
        assertEquals(update.getSignIn().getPassword(), PHONE_PASSWORD_SIGN_IN_REQUEST.getPassword());
        assertEquals(update.getEmailUpdate(), EMAIL);
        assertNull(update.getPhoneUpdate());
    }

    @Test
    public void updateIdentifiersWithSynapseUserId() throws Exception {
        mockRequestBody(mockRequest, SYNAPSE_ID_UPDATE);
        
        doReturn(session).when(controller).getAuthenticatedSession();
        
        StudyParticipant participant = new StudyParticipant.Builder().withRoles(CALLER_ROLES).withStudyIds(CALLER_STUDIES)
                .withId(TEST_USER_ID).build();
        
        when(mockParticipantService.updateIdentifiers(eq(app), any(), any())).thenReturn(participant);

        JsonNode result = controller.updateIdentifiers();

        assertEquals(result.get("id").textValue(), TEST_USER_ID);

        verify(mockParticipantService).updateIdentifiers(eq(app), contextCaptor.capture(),
                identifierUpdateCaptor.capture());

        IdentifierUpdate update = identifierUpdateCaptor.getValue();
        assertEquals(update.getSignIn().getEmail(), EMAIL_PASSWORD_SIGN_IN_REQUEST.getEmail());
        assertEquals(update.getSignIn().getPassword(), EMAIL_PASSWORD_SIGN_IN_REQUEST.getPassword());
        assertEquals(update.getSynapseUserIdUpdate(), SYNAPSE_USER_ID);
        assertNull(update.getPhoneUpdate());
    }

    @Test(expectedExceptions = NotAuthenticatedException.class)
    public void updateIdentifierRequiresAuthentication() throws Exception {
        doReturn(null).when(controller).getSessionIfItExists();

        mockRequestBody(mockRequest, PHONE_UPDATE);

        controller.updateIdentifiers();
    }
}
