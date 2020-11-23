package org.sagebionetworks.bridge.spring.controllers;

import static org.testng.AssertJUnit.assertTrue;
import static org.testng.AssertJUnit.assertEquals;
import static org.testng.AssertJUnit.assertSame;
import static org.sagebionetworks.bridge.RequestContext.NULL_INSTANCE;
import static org.sagebionetworks.bridge.Roles.ADMIN;
import static org.sagebionetworks.bridge.Roles.ORG_ADMIN;
import static org.sagebionetworks.bridge.TestConstants.TEST_APP_ID;
import static org.sagebionetworks.bridge.TestConstants.TEST_ORG_ID;
import static org.sagebionetworks.bridge.TestConstants.USER_ID;
import static org.sagebionetworks.bridge.TestUtils.assertCreate;
import static org.sagebionetworks.bridge.TestUtils.assertDelete;
import static org.sagebionetworks.bridge.TestUtils.assertGet;
import static org.sagebionetworks.bridge.TestUtils.assertPost;
import static org.sagebionetworks.bridge.TestUtils.mockRequestBody;

import java.util.function.Consumer;

import javax.servlet.http.HttpServletRequest;

import com.google.common.collect.ImmutableList;
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
import org.sagebionetworks.bridge.exceptions.EntityNotFoundException;
import org.sagebionetworks.bridge.models.AccountSummarySearch;
import org.sagebionetworks.bridge.models.PagedResourceList;
import org.sagebionetworks.bridge.models.RequestInfo;
import org.sagebionetworks.bridge.models.StatusMessage;
import org.sagebionetworks.bridge.models.accounts.Account;
import org.sagebionetworks.bridge.models.accounts.AccountId;
import org.sagebionetworks.bridge.models.accounts.AccountSummary;
import org.sagebionetworks.bridge.models.accounts.IdentifierHolder;
import org.sagebionetworks.bridge.models.accounts.StudyParticipant;
import org.sagebionetworks.bridge.models.accounts.UserSession;
import org.sagebionetworks.bridge.models.apps.App;
import org.sagebionetworks.bridge.services.AccountService;
import org.sagebionetworks.bridge.services.AppService;
import org.sagebionetworks.bridge.services.OrganizationService;
import org.sagebionetworks.bridge.services.ParticipantService;
import org.sagebionetworks.bridge.services.RequestInfoService;
import org.sagebionetworks.bridge.services.UserAdminService;
import org.sagebionetworks.bridge.services.AuthenticationService.ChannelType;

public class MembershipControllerTest extends Mockito {
    
    private static final IdentifierHolder ID = new IdentifierHolder(USER_ID);
    private static final AccountId ACCOUNT_ID = AccountId.forId(TEST_APP_ID, USER_ID);
    
    @Mock
    OrganizationService mockOrganizationService;
    
    @Mock
    ParticipantService mockParticipantService;
    
    @Mock
    UserAdminService mockUserAdminService;
    
    @Mock
    AppService mockAppService;
    
    @Mock
    AccountService mockAccountService;
    
    @Mock
    RequestInfoService mockRequestInfoService;
    
    @Mock
    HttpServletRequest mockRequest;
    
    @Captor
    ArgumentCaptor<AccountSummarySearch> searchCaptor;
    
    @Captor
    ArgumentCaptor<StudyParticipant> participantCaptor;
    
    @Captor
    ArgumentCaptor<AccountId> accountIdCaptor;

    @InjectMocks
    @Spy
    MembershipController controller;
    
    Account account;
    
    UserSession session;
    
    App app;
    
    @BeforeMethod
    public void beforeMethod() {
        MockitoAnnotations.initMocks(this);
        
        doReturn(mockRequest).when(controller).request();
        
        app = App.create();
        when(mockAppService.getApp(TEST_APP_ID)).thenReturn(app);     
        
        account = Account.create();

        session = new UserSession();
        session.setAppId(TEST_APP_ID);
    }
    
    @AfterMethod
    public void afterMethod() {
        RequestContext.set(NULL_INSTANCE);
    }
    
    private void setContext(Consumer<RequestContext.Builder> cons) {
        RequestContext.Builder builder = new RequestContext.Builder();
        cons.accept(builder);
        RequestContext.set(builder.build());
    }
    
    @Test
    public void verifyAnnotations() throws Exception { 
        assertPost(MembershipController.class, "getMembers");
        assertPost(MembershipController.class, "createMember");
        assertGet(MembershipController.class, "getMember");
        assertPost(MembershipController.class, "updateMember");
        assertDelete(MembershipController.class, "deleteMember");
        assertCreate(MembershipController.class, "addMember");
        assertDelete(MembershipController.class, "removeMember");
        assertPost(MembershipController.class, "getUnassignedAdmins");
        assertGet(MembershipController.class, "getRequestInfo");
        assertPost(MembershipController.class, "requestResetPassword");
        assertPost(MembershipController.class, "resendEmailVerification");
        assertPost(MembershipController.class, "resendPhoneVerification");
        assertPost(MembershipController.class, "signOut");
    }
    
    @Test
    public void getMembers() throws Exception {
        setContext(b -> b.withCallerAppId(TEST_APP_ID)
                .withCallerOrgMembership(TEST_ORG_ID)
                .withCallerRoles(ImmutableSet.of(ORG_ADMIN)));
        
        doReturn(session).when(controller).getAuthenticatedSession(ORG_ADMIN, ADMIN);
        
        PagedResourceList<AccountSummary> page = new PagedResourceList<AccountSummary>(ImmutableList.of(), 0);
        when(mockOrganizationService.getMembers(eq(TEST_APP_ID), eq(TEST_ORG_ID), any())).thenReturn(page);
        
        AccountSummarySearch search = new AccountSummarySearch.Builder().withEmailFilter("email").build();
        mockRequestBody(mockRequest, search);
        
        PagedResourceList<AccountSummary> retValue = controller.getMembers(TEST_ORG_ID);
        assertSame(retValue, page);
        
        verify(mockOrganizationService).getMembers(eq(TEST_APP_ID), eq(TEST_ORG_ID), searchCaptor.capture());
        assertEquals(searchCaptor.getValue().getEmailFilter(), "email");
    }
    
    @Test
    public void createMember() throws Exception {
        setContext(b -> b.withCallerAppId(TEST_APP_ID)
                .withCallerOrgMembership(TEST_ORG_ID)
                .withCallerRoles(ImmutableSet.of(ORG_ADMIN)));
        doReturn(session).when(controller).getAuthenticatedSession(ORG_ADMIN, ADMIN);
        when(mockParticipantService.createParticipant(any(), any(), anyBoolean())).thenReturn(ID);
        
        when(mockAccountService.getAccount(ACCOUNT_ID)).thenReturn(account);
        
        StudyParticipant participant = new StudyParticipant.Builder()
                .withFirstName("firstName")
                .withOrgMembership("wrong-membership").build();
        mockRequestBody(mockRequest, participant);
        
        IdentifierHolder retValue = controller.createMember(TEST_ORG_ID);
        assertEquals(retValue.getIdentifier(), USER_ID);
        
        verify(mockParticipantService)
            .createParticipant(eq(app), participantCaptor.capture(), eq(true));
        
        StudyParticipant submitted = participantCaptor.getValue();
        assertEquals(submitted.getOrgMembership(), TEST_ORG_ID);
        assertEquals(submitted.getFirstName(), "firstName");
    }
    
    @Test
    public void getMember() throws Exception {
        setContext(b -> b.withCallerAppId(TEST_APP_ID)
                .withCallerOrgMembership(TEST_ORG_ID)
                .withCallerRoles(ImmutableSet.of(ORG_ADMIN)));
        doReturn(session).when(controller).getAuthenticatedSession(ORG_ADMIN, ADMIN);
        
        account.setOrgMembership(TEST_ORG_ID);
        when(mockAccountService.getAccount(ACCOUNT_ID)).thenReturn(account);

        StudyParticipant persisted = new StudyParticipant.Builder()
                .withOrgMembership(TEST_ORG_ID).build();
        when(mockParticipantService.getParticipant(app, account, false)).thenReturn(persisted);
        
        StudyParticipant retValue = controller.getMember(TEST_ORG_ID, USER_ID);
        assertSame(retValue, persisted);
    }
    
    @Test
    public void updateMember() throws Exception {
        setContext(b -> b.withCallerAppId(TEST_APP_ID)
                .withCallerOrgMembership(TEST_ORG_ID)
                .withCallerRoles(ImmutableSet.of(ORG_ADMIN)));
        doReturn(session).when(controller).getAuthenticatedSession(ORG_ADMIN, ADMIN);
        
        session.setParticipant(new StudyParticipant.Builder().withOrgMembership(TEST_ORG_ID).build());
        
        account.setOrgMembership(TEST_ORG_ID);
        when(mockAccountService.getAccount(ACCOUNT_ID)).thenReturn(account);
        
        StudyParticipant submitted = new StudyParticipant.Builder()
                .withOrgMembership("bad-membership")
                .withId("bad-id").build();
        mockRequestBody(mockRequest, submitted);

        StudyParticipant persisted = new StudyParticipant.Builder()
                .withOrgMembership(TEST_ORG_ID).build();
        when(mockParticipantService.getParticipant(app, account, false)).thenReturn(persisted);
        
        StatusMessage retValue = controller.updateMember(USER_ID);
        assertEquals(retValue.getMessage(), "Member updated.");
        
        verify(mockParticipantService).updateParticipant(eq(app), participantCaptor.capture());
        assertEquals(participantCaptor.getValue().getId(), USER_ID);
        assertEquals(participantCaptor.getValue().getOrgMembership(), TEST_ORG_ID);
    }
    
    @Test
    public void deleteMember() throws Exception {
        setContext(b -> b.withCallerAppId(TEST_APP_ID)
                .withCallerOrgMembership(TEST_ORG_ID)
                .withCallerRoles(ImmutableSet.of(ORG_ADMIN)));
        doReturn(session).when(controller).getAuthenticatedSession(ORG_ADMIN, ADMIN);
        
        account.setOrgMembership(TEST_ORG_ID);
        when(mockAccountService.getAccount(ACCOUNT_ID)).thenReturn(account);
        
        StudyParticipant existing = new StudyParticipant.Builder()
                .withOrgMembership(TEST_ORG_ID)
                .build();
        when(mockParticipantService.getParticipant(app, USER_ID, false)).thenReturn(existing);

        StatusMessage retValue = controller.deleteMember(USER_ID);
        assertEquals(retValue.getMessage(), "Member account deleted.");
        
        verify(mockUserAdminService).deleteUser(app, USER_ID);
    }
    
    @Test
    public void addMember() {
        doReturn(session).when(controller).getAuthenticatedSession(ORG_ADMIN, ADMIN);
        
        controller.addMember(TEST_ORG_ID, USER_ID);
        
        verify(mockOrganizationService).addMember(eq(TEST_APP_ID), eq(TEST_ORG_ID), accountIdCaptor.capture());
        AccountId accountId = accountIdCaptor.getValue();
        assertEquals(TEST_APP_ID, accountId.getAppId());
        assertEquals(USER_ID, accountId.getId());
    }

    @Test
    public void removeMember() {
        doReturn(session).when(controller).getAuthenticatedSession(ORG_ADMIN, ADMIN);
        
        controller.removeMember(TEST_ORG_ID, USER_ID);
        
        verify(mockOrganizationService).removeMember(eq(TEST_APP_ID), eq(TEST_ORG_ID), accountIdCaptor.capture());
        AccountId accountId = accountIdCaptor.getValue();
        assertEquals(TEST_APP_ID, accountId.getAppId());
        assertEquals(USER_ID, accountId.getId());
    }

    @Test
    public void getUnassignedAdmins() throws Exception {
        doReturn(session).when(controller).getAdministrativeSession();
        
        AccountSummarySearch initial = new AccountSummarySearch.Builder()
            .withOrgMembership("something-to-be-overridden")
            .withEmailFilter("sagebase.org").build();
        mockRequestBody(mockRequest, initial);
        
        App app = App.create();
        when(mockAppService.getApp(TEST_APP_ID)).thenReturn(app);
        
        PagedResourceList<AccountSummary> results = new PagedResourceList<>(ImmutableList.of(), 10);
        when(mockParticipantService.getPagedAccountSummaries(eq(app), any())).thenReturn(results);
        
        PagedResourceList<AccountSummary> retValue = controller.getUnassignedAdmins();
        assertSame(retValue, results);
        
        verify(mockParticipantService).getPagedAccountSummaries(eq(app), searchCaptor.capture());
        assertEquals("sagebase.org", searchCaptor.getValue().getEmailFilter());
        assertEquals("<none>", searchCaptor.getValue().getOrgMembership());
        assertTrue(searchCaptor.getValue().isAdminOnly());
    }
    
    @Test
    public void getRequestInfo() throws Exception {
        setContext(b -> b.withCallerAppId(TEST_APP_ID)
                .withCallerOrgMembership(TEST_ORG_ID)
                .withCallerRoles(ImmutableSet.of(ORG_ADMIN)));
        
        doReturn(session).when(controller).getAuthenticatedSession(ORG_ADMIN, ADMIN);
        
        account.setOrgMembership(TEST_ORG_ID);
        when(mockAccountService.getAccount(ACCOUNT_ID)).thenReturn(account);
        
        RequestInfo requestInfo = new RequestInfo.Builder().withAppId(TEST_APP_ID).build();
        when(mockRequestInfoService.getRequestInfo(USER_ID)).thenReturn(requestInfo);
        
        String retValue = controller.getRequestInfo(TEST_ORG_ID, USER_ID);
        assertEquals(retValue, RequestInfo.REQUEST_INFO_WRITER.writeValueAsString(requestInfo));
    }
    
    @Test
    public void getRequestInfoIsNull() throws Exception {
        setContext(b -> b.withCallerAppId(TEST_APP_ID)
                .withCallerOrgMembership(TEST_ORG_ID)
                .withCallerRoles(ImmutableSet.of(ORG_ADMIN)));
        
        doReturn(session).when(controller).getAuthenticatedSession(ORG_ADMIN, ADMIN);
        
        account.setOrgMembership(TEST_ORG_ID);
        when(mockAccountService.getAccount(ACCOUNT_ID)).thenReturn(account);
        
        when(mockRequestInfoService.getRequestInfo(USER_ID)).thenReturn(null);
        
        String retValue = controller.getRequestInfo(TEST_ORG_ID, USER_ID);
        assertEquals(retValue, RequestInfo.REQUEST_INFO_WRITER
                .writeValueAsString(new RequestInfo.Builder().build()));
    }
    
    @Test
    public void requestResetPassword() throws Exception {
        setContext(b -> b.withCallerAppId(TEST_APP_ID)
                .withCallerOrgMembership(TEST_ORG_ID)
                .withCallerRoles(ImmutableSet.of(ORG_ADMIN)));
        
        doReturn(session).when(controller).getAuthenticatedSession(ORG_ADMIN, ADMIN);
        
        account.setOrgMembership(TEST_ORG_ID);
        when(mockAccountService.getAccount(ACCOUNT_ID)).thenReturn(account);

        StatusMessage retValue = controller.requestResetPassword(TEST_ORG_ID, USER_ID);
        assertEquals(retValue.getMessage(), "Request to reset password sent to user.");
        
        verify(mockParticipantService).requestResetPassword(app, USER_ID);
    }
    
    @Test
    public void resendEmailVerification() throws Exception {
        setContext(b -> b.withCallerAppId(TEST_APP_ID)
                .withCallerOrgMembership(TEST_ORG_ID)
                .withCallerRoles(ImmutableSet.of(ORG_ADMIN)));
        
        doReturn(session).when(controller).getAuthenticatedSession(ORG_ADMIN, ADMIN);
        
        account.setOrgMembership(TEST_ORG_ID);
        when(mockAccountService.getAccount(ACCOUNT_ID)).thenReturn(account);
        
        controller.resendEmailVerification(TEST_ORG_ID, USER_ID);
        
        verify(mockParticipantService).resendVerification(app, ChannelType.EMAIL, USER_ID);
    }
    
    @Test
    public void resendPhoneVerification() throws Exception {
        setContext(b -> b.withCallerAppId(TEST_APP_ID)
                .withCallerOrgMembership(TEST_ORG_ID)
                .withCallerRoles(ImmutableSet.of(ORG_ADMIN)));
        
        doReturn(session).when(controller).getAuthenticatedSession(ORG_ADMIN, ADMIN);
        
        account.setOrgMembership(TEST_ORG_ID);
        when(mockAccountService.getAccount(ACCOUNT_ID)).thenReturn(account);
        
        controller.resendPhoneVerification(TEST_ORG_ID, USER_ID);
        
        verify(mockParticipantService).resendVerification(app, ChannelType.PHONE, USER_ID);        
    }
    
    @Test
    public void signOut() throws Exception {
        setContext(b -> b.withCallerAppId(TEST_APP_ID)
                .withCallerOrgMembership(TEST_ORG_ID)
                .withCallerRoles(ImmutableSet.of(ORG_ADMIN)));
        
        doReturn(session).when(controller).getAuthenticatedSession(ORG_ADMIN, ADMIN);
        
        account.setOrgMembership(TEST_ORG_ID);
        when(mockAccountService.getAccount(ACCOUNT_ID)).thenReturn(account);
        
        controller.signOut(TEST_ORG_ID, USER_ID, true);
        
        verify(mockParticipantService).signUserOut(app, USER_ID, true);
    }
    
    @Test
    public void verifyOrgAdminIsActingOnOrgMemberSucceeds() throws Exception {
        setContext(b -> b.withCallerAppId(TEST_APP_ID)
                .withCallerOrgMembership(TEST_ORG_ID)
                .withCallerRoles(ImmutableSet.of(ORG_ADMIN)));
        
        account.setOrgMembership(TEST_ORG_ID);
        when(mockAccountService.getAccount(ACCOUNT_ID)).thenReturn(account);
        
        Account retValue = controller.verifyOrgAdminIsActingOnOrgMember(TEST_APP_ID, TEST_ORG_ID, USER_ID);
        assertSame(retValue, account);
    }
    
    @Test(expectedExceptions = EntityNotFoundException.class, 
            expectedExceptionsMessageRegExp = "Account not found.")
    public void verifyOrgAdminIsActingOnOrgMemberFailsAccountNotFound() throws Exception {
        setContext(b -> b.withCallerAppId(TEST_APP_ID)
                .withCallerOrgMembership(TEST_ORG_ID)
                .withCallerRoles(ImmutableSet.of(ORG_ADMIN)));
        
        when(mockAccountService.getAccount(ACCOUNT_ID)).thenReturn(null);
        
        Account retValue = controller.verifyOrgAdminIsActingOnOrgMember(TEST_APP_ID, TEST_ORG_ID, USER_ID);
        assertSame(retValue, account);
    }

    @Test(expectedExceptions = EntityNotFoundException.class, 
            expectedExceptionsMessageRegExp = "Account not found.")
    public void verifyOrgAdminIsActingOnOrgMemberFailsWrongOrg() throws Exception {
        setContext(b -> b.withCallerAppId(TEST_APP_ID)
                .withCallerOrgMembership(TEST_ORG_ID)
                .withCallerRoles(ImmutableSet.of(ORG_ADMIN)));
        
        account.setOrgMembership("wrong-organization-id");
        when(mockAccountService.getAccount(ACCOUNT_ID)).thenReturn(account);
        
        Account retValue = controller.verifyOrgAdminIsActingOnOrgMember(TEST_APP_ID, TEST_ORG_ID, USER_ID);
        assertSame(retValue, account);
    }
}
