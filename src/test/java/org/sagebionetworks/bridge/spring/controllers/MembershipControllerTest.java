package org.sagebionetworks.bridge.spring.controllers;

import static org.testng.AssertJUnit.assertEquals;
import static org.testng.AssertJUnit.assertSame;
import static org.sagebionetworks.bridge.RequestContext.NULL_INSTANCE;
import static org.sagebionetworks.bridge.Roles.ADMIN;
import static org.sagebionetworks.bridge.Roles.ORG_ADMIN;
import static org.sagebionetworks.bridge.TestConstants.TEST_APP_ID;
import static org.sagebionetworks.bridge.TestConstants.TEST_ORG_ID;
import static org.sagebionetworks.bridge.TestConstants.TEST_USER_ID;
import static org.sagebionetworks.bridge.TestUtils.assertCreate;
import static org.sagebionetworks.bridge.TestUtils.assertDelete;
import static org.sagebionetworks.bridge.TestUtils.assertPost;
import static org.sagebionetworks.bridge.TestUtils.mockRequestBody;

import java.util.function.Consumer;

import javax.servlet.http.HttpServletRequest;

import com.google.common.collect.ImmutableList;

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
import org.sagebionetworks.bridge.exceptions.UnauthorizedException;
import org.sagebionetworks.bridge.models.AccountSummarySearch;
import org.sagebionetworks.bridge.models.PagedResourceList;
import org.sagebionetworks.bridge.models.accounts.Account;
import org.sagebionetworks.bridge.models.accounts.AccountId;
import org.sagebionetworks.bridge.models.accounts.AccountSummary;
import org.sagebionetworks.bridge.models.accounts.StudyParticipant;
import org.sagebionetworks.bridge.models.accounts.UserSession;
import org.sagebionetworks.bridge.models.apps.App;
import org.sagebionetworks.bridge.services.OrganizationService;

public class MembershipControllerTest extends Mockito {
    
    @Mock
    OrganizationService mockOrganizationService;
    
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
        assertCreate(MembershipController.class, "addMember");
        assertDelete(MembershipController.class, "removeMember");
        assertPost(MembershipController.class, "getUnassignedAdmins");
    }
    
    @Test
    public void getMembers() throws Exception {
        setContext(b -> b.withCallerAppId(TEST_APP_ID)
                .withCallerOrgMembership(TEST_ORG_ID));
        
        doReturn(session).when(controller).getAdministrativeSession();
        
        PagedResourceList<AccountSummary> page = new PagedResourceList<AccountSummary>(ImmutableList.of(), 0);
        when(mockOrganizationService.getMembers(eq(TEST_APP_ID), eq(TEST_ORG_ID), any())).thenReturn(page);
        
        AccountSummarySearch search = new AccountSummarySearch.Builder().withEmailFilter("email").build();
        mockRequestBody(mockRequest, search);
        
        PagedResourceList<AccountSummary> retValue = controller.getMembers(TEST_ORG_ID);
        assertSame(retValue, page);
        
        verify(mockOrganizationService).getMembers(eq(TEST_APP_ID), eq(TEST_ORG_ID), searchCaptor.capture());
        assertEquals(searchCaptor.getValue().getEmailFilter(), "email");
    }
    
    @Test(expectedExceptions = UnauthorizedException.class)
    public void getMembersInaccessibleToOtherOrgs() throws Exception {
        setContext(b -> b.withCallerAppId(TEST_APP_ID)
                .withCallerOrgMembership("another-organization"));
        
        doReturn(session).when(controller).getAdministrativeSession();
        
        PagedResourceList<AccountSummary> page = new PagedResourceList<AccountSummary>(ImmutableList.of(), 0);
        when(mockOrganizationService.getMembers(eq(TEST_APP_ID), eq(TEST_ORG_ID), any())).thenReturn(page);
        
        AccountSummarySearch search = new AccountSummarySearch.Builder().withEmailFilter("email").build();
        mockRequestBody(mockRequest, search);
        
        controller.getMembers(TEST_ORG_ID);
    }
    
    @Test
    public void addMember() {
        doReturn(session).when(controller).getAuthenticatedSession(ORG_ADMIN, ADMIN);
        
        controller.addMember(TEST_ORG_ID, TEST_USER_ID);
        
        verify(mockOrganizationService).addMember(eq(TEST_APP_ID), eq(TEST_ORG_ID), accountIdCaptor.capture());
        AccountId accountId = accountIdCaptor.getValue();
        assertEquals(TEST_APP_ID, accountId.getAppId());
        assertEquals(TEST_USER_ID, accountId.getId());
    }

    @Test
    public void removeMember() {
        doReturn(session).when(controller).getAuthenticatedSession(ORG_ADMIN, ADMIN);
        
        controller.removeMember(TEST_ORG_ID, TEST_USER_ID);
        
        verify(mockOrganizationService).removeMember(eq(TEST_APP_ID), eq(TEST_ORG_ID), accountIdCaptor.capture());
        AccountId accountId = accountIdCaptor.getValue();
        assertEquals(TEST_APP_ID, accountId.getAppId());
        assertEquals(TEST_USER_ID, accountId.getId());
    }

    @Test
    public void getUnassignedAdmins() throws Exception {
        doReturn(session).when(controller).getAdministrativeSession();
        
        AccountSummarySearch initial = new AccountSummarySearch.Builder().build();
        mockRequestBody(mockRequest, initial);
        
        PagedResourceList<AccountSummary> results = new PagedResourceList<>(ImmutableList.of(), 10);
        when(mockOrganizationService.getUnassignedAdmins(eq(TEST_APP_ID), any())).thenReturn(results);
        
        PagedResourceList<AccountSummary> retValue = controller.getUnassignedAdmins();
        assertSame(retValue, results);
        
        verify(mockOrganizationService).getUnassignedAdmins(eq(TEST_APP_ID), searchCaptor.capture());
        assertEquals(searchCaptor.getValue(), initial);
    }
}
