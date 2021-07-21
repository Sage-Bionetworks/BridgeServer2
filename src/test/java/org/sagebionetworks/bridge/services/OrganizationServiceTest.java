package org.sagebionetworks.bridge.services;

import org.mockito.Mock;

import static org.sagebionetworks.bridge.BridgeConstants.NEGATIVE_OFFSET_ERROR;
import static org.sagebionetworks.bridge.BridgeConstants.PAGE_SIZE_ERROR;
import static org.sagebionetworks.bridge.Roles.ADMIN;
import static org.sagebionetworks.bridge.Roles.ORG_ADMIN;
import static org.sagebionetworks.bridge.TestConstants.ACCOUNT_ID;
import static org.sagebionetworks.bridge.TestConstants.CREATED_ON;
import static org.sagebionetworks.bridge.TestConstants.MODIFIED_ON;
import static org.sagebionetworks.bridge.TestConstants.TEST_APP_ID;
import static org.sagebionetworks.bridge.TestConstants.TEST_ORG_ID;
import static org.sagebionetworks.bridge.TestConstants.USER_DATA_GROUPS;
import static org.sagebionetworks.bridge.TestUtils.mockEditAccount;
import static org.sagebionetworks.bridge.TestConstants.TEST_USER_ID;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertNull;
import static org.testng.Assert.assertSame;
import static org.testng.Assert.assertTrue;

import java.util.Optional;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;

import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;
import org.mockito.Spy;
import org.sagebionetworks.bridge.dao.AssessmentDao;
import org.sagebionetworks.bridge.exceptions.ConstraintViolationException;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import org.sagebionetworks.bridge.RequestContext;
import org.sagebionetworks.bridge.cache.CacheKey;
import org.sagebionetworks.bridge.cache.CacheProvider;
import org.sagebionetworks.bridge.dao.OrganizationDao;
import org.sagebionetworks.bridge.exceptions.BadRequestException;
import org.sagebionetworks.bridge.exceptions.EntityAlreadyExistsException;
import org.sagebionetworks.bridge.exceptions.EntityNotFoundException;
import org.sagebionetworks.bridge.exceptions.InvalidEntityException;
import org.sagebionetworks.bridge.models.AccountSummarySearch;
import org.sagebionetworks.bridge.models.PagedResourceList;
import org.sagebionetworks.bridge.models.accounts.Account;
import org.sagebionetworks.bridge.models.accounts.AccountSummary;
import org.sagebionetworks.bridge.models.organizations.Organization;

public class OrganizationServiceTest extends Mockito {

    private static final String NAME = "aName";
    private static final String IDENTIFIER = "an-identifier";

    @Mock
    OrganizationDao mockOrgDao;
    
    @Mock
    AccountService mockAccountService;

    @Mock
    AssessmentDao mockAssessmentDao;
    
    @Mock
    CacheProvider mockCacheProvider;
    
    @Mock
    SessionUpdateService mockSessionUpdateService;
    
    @InjectMocks
    @Spy
    OrganizationService service;
    
    @Captor
    ArgumentCaptor<AccountSummarySearch> searchCaptor;
    
    @Captor
    ArgumentCaptor<Account> accountCaptor;
    
    @BeforeMethod
    public void beforeMethod() {
        MockitoAnnotations.initMocks(this);
        
        when(service.getCreatedOn()).thenReturn(CREATED_ON);
        when(service.getModifiedOn()).thenReturn(MODIFIED_ON);
    }
    
    @AfterMethod
    public void afterMethod() {
        RequestContext.set(RequestContext.NULL_INSTANCE);
    }
    
    @Test
    public void getOrganizations() {
        RequestContext.set(new RequestContext.Builder().withCallerRoles(ImmutableSet.of(ADMIN)).build());

        PagedResourceList<Organization> page = new PagedResourceList<>(
                ImmutableList.of(Organization.create(), Organization.create()), 10);
        when(mockOrgDao.getOrganizations(TEST_APP_ID, 100, 20)).thenReturn(page);
        
        PagedResourceList<Organization> retList = service.getOrganizations(TEST_APP_ID, 100, 20);
        assertEquals(retList.getRequestParams().get("offsetBy"), 100);
        assertEquals(retList.getRequestParams().get("pageSize"), 20);
        assertEquals(retList.getTotal(), Integer.valueOf(10));
        assertEquals(retList.getItems().size(), 2);
    }
    
    @Test
    public void getOrganizationsOnlyReturnsMemberOrgForNonAdmins() {
        RequestContext.set(new RequestContext.Builder()
                .withCallerRoles(ImmutableSet.of(ORG_ADMIN))
                .withCallerOrgMembership(TEST_ORG_ID).build());
        
        Organization org = Organization.create();
        when(mockOrgDao.getOrganization(TEST_APP_ID, TEST_ORG_ID))
            .thenReturn(Optional.of(org));
        
        PagedResourceList<Organization> retList = service.getOrganizations(TEST_APP_ID, 100, 20);
        assertEquals(retList.getRequestParams().get("offsetBy"), 100);
        assertEquals(retList.getRequestParams().get("pageSize"), 20);
        assertEquals(retList.getTotal(), Integer.valueOf(1));
        assertEquals(retList.getItems().size(), 1);
        assertEquals(retList.getItems().get(0), org);
    }
    @Test
    public void getOrganizationsNullArguments() {
        RequestContext.set(new RequestContext.Builder().withCallerRoles(ImmutableSet.of(ADMIN)).build());
        
        PagedResourceList<Organization> page = new PagedResourceList<>(
                ImmutableList.of(Organization.create(), Organization.create()), 10);
        when(mockOrgDao.getOrganizations(TEST_APP_ID, null, null)).thenReturn(page);
        
        PagedResourceList<Organization> retList = service.getOrganizations(TEST_APP_ID, null, null);
        // Normally these are set to defaults in the controller, but some calls want to load
        // all organizations in the system...these would have null values, but we're not referring
        // to them internally.
        assertNull(retList.getRequestParams().get("offsetBy"));
        assertNull(retList.getRequestParams().get("pageSize"));
        assertEquals(retList.getTotal(), Integer.valueOf(10));
        assertEquals(retList.getItems().size(), 2);
    }
    
    @Test(expectedExceptions = BadRequestException.class, 
            expectedExceptionsMessageRegExp = NEGATIVE_OFFSET_ERROR)
    public void getOrganizationsNegativeOffset() {
        service.getOrganizations(TEST_APP_ID, -5, 0);
    }
    
    @Test(expectedExceptions = BadRequestException.class, 
            expectedExceptionsMessageRegExp = PAGE_SIZE_ERROR)
    public void getOrganizationsPageTooSmall() {
        service.getOrganizations(TEST_APP_ID, 0, 0);
    }
    
    @Test(expectedExceptions = BadRequestException.class, 
            expectedExceptionsMessageRegExp = PAGE_SIZE_ERROR)
    public void getOrganizationsPageTooLarge() {
        service.getOrganizations(TEST_APP_ID, 0, 1000);
    }
    
    @Test
    public void createOrganization() {
        Organization org = Organization.create();
        org.setAppId(TEST_APP_ID);
        org.setIdentifier(IDENTIFIER);
        org.setName(NAME);
        org.setVersion(3L);
        
        when(mockOrgDao.createOrganization(org)).thenReturn(org);

        Organization retValue = service.createOrganization(org);
        assertEquals(retValue.getCreatedOn(), CREATED_ON);
        assertEquals(retValue.getModifiedOn(), CREATED_ON);
        assertNull(retValue.getVersion());
        
        verify(mockOrgDao).createOrganization(org);
    }
    
    @Test(expectedExceptions = EntityAlreadyExistsException.class)
    public void createOrganizationAlreadyExists() {
        Organization existing = Organization.create();
        when(mockOrgDao.getOrganization(TEST_APP_ID, IDENTIFIER)).thenReturn(Optional.of(existing));
        
        Organization org = Organization.create();
        org.setAppId(TEST_APP_ID);
        org.setIdentifier(IDENTIFIER);
        org.setName(NAME);

        service.createOrganization(org);
    }

    @Test(expectedExceptions = InvalidEntityException.class)
    public void createOrganizationNotValid() {
        Organization org = Organization.create();
        service.createOrganization(org);
    }
    
    @Test
    public void updateOrganization() {
        RequestContext.set(new RequestContext.Builder()
                .withCallerOrgMembership(IDENTIFIER)
                .withCallerRoles(ImmutableSet.of(ORG_ADMIN)).build());
        Organization org = Organization.create();
        org.setAppId(TEST_APP_ID);
        org.setIdentifier(IDENTIFIER);
        org.setName(NAME);
        
        Organization existing = Organization.create();
        existing.setAppId(TEST_APP_ID);
        existing.setIdentifier(IDENTIFIER);
        existing.setCreatedOn(CREATED_ON);
        when(mockOrgDao.getOrganization(TEST_APP_ID, IDENTIFIER)).thenReturn(Optional.of(existing));
        
        when(mockOrgDao.updateOrganization(org)).thenReturn(org);
        
        Organization retValue = service.updateOrganization(org);
        assertEquals(retValue.getAppId(), TEST_APP_ID);
        assertEquals(retValue.getIdentifier(), IDENTIFIER);
        assertEquals(retValue.getName(), NAME);
        assertEquals(retValue.getCreatedOn(), CREATED_ON);
        assertEquals(retValue.getModifiedOn(), MODIFIED_ON);
    }
    
    @Test(expectedExceptions = EntityNotFoundException.class, 
            expectedExceptionsMessageRegExp = "Organization not found.")
    public void updateOrganizationNotFound() {
        RequestContext.set(new RequestContext.Builder()
                .withCallerOrgMembership(IDENTIFIER)
                .withCallerRoles(ImmutableSet.of(ORG_ADMIN)).build());
        when(mockOrgDao.getOrganization(TEST_APP_ID, IDENTIFIER)).thenReturn(Optional.empty());
        
        Organization org = Organization.create();
        org.setAppId(TEST_APP_ID);
        org.setIdentifier(IDENTIFIER);
        org.setName(NAME);
        
        service.updateOrganization(org);
    }
    
    @Test(expectedExceptions = InvalidEntityException.class)
    public void updateOrganizationNotValid() {
        RequestContext.set(new RequestContext.Builder()
                .withCallerOrgMembership(IDENTIFIER)
                .withCallerRoles(ImmutableSet.of(ORG_ADMIN)).build());
        Organization org = Organization.create();
        org.setIdentifier(IDENTIFIER);
        
        service.updateOrganization(org);
    }
    
    @Test
    public void getOrganization() {
        Organization org = Organization.create();
        when(mockOrgDao.getOrganization(TEST_APP_ID, IDENTIFIER))
            .thenReturn(Optional.of(org));
        
        Organization retValue = service.getOrganization(TEST_APP_ID, IDENTIFIER);
        assertSame(retValue, org);
        
        verify(mockOrgDao).getOrganization(TEST_APP_ID, IDENTIFIER);
    }
    
    @Test(expectedExceptions = EntityNotFoundException.class, 
            expectedExceptionsMessageRegExp = "Organization not found.")
    public void getOrganizationNotFound() {
        when(mockOrgDao.getOrganization(TEST_APP_ID, IDENTIFIER))
            .thenReturn(Optional.empty());
        
        service.getOrganization(TEST_APP_ID, IDENTIFIER);
    }
    
    @Test
    public void getOrganizationOpt() {
        when(mockOrgDao.getOrganization(TEST_APP_ID, IDENTIFIER))
            .thenReturn(Optional.of(Organization.create()));
        
        Optional<Organization> retValue = service.getOrganizationOpt(TEST_APP_ID, IDENTIFIER);
        assertTrue(retValue.isPresent());
    }
    
    @Test
    public void deleteOrganization() {
        RequestContext.set(new RequestContext.Builder()
                .withCallerRoles(ImmutableSet.of(ADMIN)).build());
        Organization org = Organization.create();
        when(mockOrgDao.getOrganization(TEST_APP_ID, IDENTIFIER)).thenReturn(Optional.of(org));
        
        service.deleteOrganization(TEST_APP_ID, IDENTIFIER);
        
        verify(mockOrgDao).deleteOrganization(org);
        verify(mockCacheProvider).removeObject(CacheKey.orgSponsoredStudies(TEST_APP_ID, IDENTIFIER));
    }

    @Test
    public void deleteOrganizationAsAdmin() {
        RequestContext.set(new RequestContext.Builder()
                .withCallerRoles(ImmutableSet.of(ADMIN)).build());
        Organization org = Organization.create();
        when(mockOrgDao.getOrganization(TEST_APP_ID, IDENTIFIER)).thenReturn(Optional.of(org));
        
        service.deleteOrganization(TEST_APP_ID, IDENTIFIER);
        
        verify(mockOrgDao).deleteOrganization(org);
        verify(mockCacheProvider).removeObject(CacheKey.orgSponsoredStudies(TEST_APP_ID, IDENTIFIER));
    }
    
    @Test(expectedExceptions = ConstraintViolationException.class)
    public void deleteOrganizationWhileHasAssessments() {
        RequestContext.set(new RequestContext.Builder()
                .withCallerRoles(ImmutableSet.of(ADMIN)).build());
        Organization org = Organization.create();
        when(mockOrgDao.getOrganization(TEST_APP_ID, IDENTIFIER)).thenReturn(Optional.of(org));
        when(mockAssessmentDao.hasAssessmentFromOrg(eq(TEST_APP_ID), eq(IDENTIFIER))).thenReturn(true);
        service.deleteOrganization(TEST_APP_ID, IDENTIFIER);
    }

    @Test(expectedExceptions = EntityNotFoundException.class,
            expectedExceptionsMessageRegExp = "Organization not found.")
    public void deleteOrganizationNotFound() {
        RequestContext.set(new RequestContext.Builder()
                .withCallerRoles(ImmutableSet.of(ADMIN)).build());
        when(mockOrgDao.getOrganization(TEST_APP_ID, IDENTIFIER)).thenReturn(Optional.empty());
        
        service.deleteOrganization(TEST_APP_ID, IDENTIFIER);
    }
    
    @Test
    public void getMembers() {
        RequestContext.set(new RequestContext.Builder()
                .withCallerOrgMembership(IDENTIFIER)
                .withCallerRoles(ImmutableSet.of(ORG_ADMIN)).build());
        
        when(mockOrgDao.getOrganization(TEST_APP_ID, IDENTIFIER)).thenReturn(Optional.of(Organization.create()));
        
        PagedResourceList<AccountSummary> page = new PagedResourceList<>(ImmutableList.of(), 0); 
        when(mockAccountService.getPagedAccountSummaries(eq(TEST_APP_ID), any())).thenReturn(page);
        
        AccountSummarySearch search = new AccountSummarySearch.Builder().withLanguage("en").build();        
        
        PagedResourceList<AccountSummary> retValue =  service.getMembers(TEST_APP_ID, IDENTIFIER, search);
        assertSame(retValue, page);
        
        verify(mockAccountService).getPagedAccountSummaries(eq(TEST_APP_ID), searchCaptor.capture());
        assertEquals(searchCaptor.getValue().getLanguage(), "en");
        assertEquals(searchCaptor.getValue().getOrgMembership(), IDENTIFIER);
        assertNull(searchCaptor.getValue().isAdminOnly());
    }
    
    @Test
    public void getMembersAsSuperadmin() {
        RequestContext.set(new RequestContext.Builder()
                .withCallerRoles(ImmutableSet.of(ADMIN)).build());
        
        when(mockOrgDao.getOrganization(TEST_APP_ID, IDENTIFIER)).thenReturn(Optional.of(Organization.create()));
        
        PagedResourceList<AccountSummary> page = new PagedResourceList<>(ImmutableList.of(), 0); 
        when(mockAccountService.getPagedAccountSummaries(eq(TEST_APP_ID), any())).thenReturn(page);
        
        AccountSummarySearch search = new AccountSummarySearch.Builder().withLanguage("en").build();        
        
        PagedResourceList<AccountSummary> retValue =  service.getMembers(TEST_APP_ID, IDENTIFIER, search);
        assertSame(retValue, page);
    }

    public void addMember() {
        RequestContext.set(new RequestContext.Builder()
                .withCallerOrgMembership(IDENTIFIER)
                .withCallerRoles(ImmutableSet.of(ORG_ADMIN)).build());
        
        Account account = Account.create();
        account.setId(TEST_USER_ID);
        mockEditAccount(mockAccountService, account);
        
        service.addMember(TEST_APP_ID, IDENTIFIER, TEST_USER_ID);
        
        verify(mockAccountService).editAccount(eq(ACCOUNT_ID), any());
        assertEquals(account.getOrgMembership(), IDENTIFIER);
        
        verify(mockSessionUpdateService).updateOrgMembership(TEST_USER_ID, IDENTIFIER);
    }
    
    @Test
    public void addMemberAsSuperadmin() {
        RequestContext.set(new RequestContext.Builder()
                .withCallerRoles(ImmutableSet.of(ADMIN)).build());
        
        Account account = Account.create();
        account.setId(TEST_USER_ID);
        mockEditAccount(mockAccountService, account);
        
        service.addMember(TEST_APP_ID, IDENTIFIER, TEST_USER_ID);
        
        verify(mockAccountService).editAccount(eq(ACCOUNT_ID), any());
        assertEquals(account.getOrgMembership(), IDENTIFIER);
    }
    
    @Test(expectedExceptions = EntityNotFoundException.class, 
            expectedExceptionsMessageRegExp = "Account not found.")
    public void addMemberAccountNotFound() {
        RequestContext.set(new RequestContext.Builder()
                .withCallerRoles(ImmutableSet.of(ADMIN)).build());

        doThrow(new EntityNotFoundException(Account.class)).when(mockAccountService)
            .editAccount(any(), any());
        
        service.addMember(TEST_APP_ID, IDENTIFIER, TEST_USER_ID);
    }
    
    @Test(expectedExceptions = BadRequestException.class)
    public void addMemberFailsForAssignedAccount() {
        RequestContext.set(new RequestContext.Builder()
                .withCallerOrgMembership(IDENTIFIER)
                .withCallerRoles(ImmutableSet.of(ORG_ADMIN)).build());

        Account account = Account.create();
        account.setOrgMembership("another-organization");
        mockEditAccount(mockAccountService, account);

        service.addMember(TEST_APP_ID, IDENTIFIER, TEST_USER_ID);
    }

    @Test
    public void addMemberSucceedsForAssignedAccountAdmin() {
        RequestContext.set(new RequestContext.Builder()
                .withCallerRoles(ImmutableSet.of(ADMIN)).build());

        Account account = Account.create();
        account.setOrgMembership("another-organization");
        mockEditAccount(mockAccountService, account);

        service.addMember(TEST_APP_ID, IDENTIFIER, TEST_USER_ID);
        
        verify(mockAccountService).editAccount(eq(ACCOUNT_ID), any());
        assertEquals(account.getOrgMembership(), IDENTIFIER);
    }
    
    @Test
    public void getUnassignedAdmins() { 
        AccountSummarySearch search = new AccountSummarySearch.Builder()
                .withAllOfGroups(USER_DATA_GROUPS) // this should be preserved
                .withOrgMembership("some-org") // this should be overwritten
                .withAdminOnly(false) // this should be overwritten
                .build();
        
        service.getUnassignedAdmins(TEST_APP_ID, search);
        
        verify(mockAccountService).getPagedAccountSummaries(eq(TEST_APP_ID), searchCaptor.capture());
        
        AccountSummarySearch captured = searchCaptor.getValue(); 
        assertTrue(captured.isAdminOnly());
        assertEquals(captured.getOrgMembership(), "<none>");
        assertEquals(captured.getAllOfGroups(), USER_DATA_GROUPS);
    }
    
    @Test
    public void removeMember() {
        RequestContext.set(new RequestContext.Builder()
                .withCallerOrgMembership(IDENTIFIER)
                .withCallerRoles(ImmutableSet.of(ORG_ADMIN)).build());
        
        Account account = Account.create();
        account.setOrgMembership(IDENTIFIER);
        account.setId(TEST_USER_ID);
        mockEditAccount(mockAccountService, account);

        service.removeMember(TEST_APP_ID, IDENTIFIER, TEST_USER_ID);
        
        verify(mockAccountService).editAccount(eq(ACCOUNT_ID), any());
        assertNull(account.getOrgMembership());
        
        verify(mockSessionUpdateService).updateOrgMembership(TEST_USER_ID, null);
    }
    
    @Test
    public void removeMemberAsSuperadmin() {
        RequestContext.set(new RequestContext.Builder()
                .withCallerRoles(ImmutableSet.of(ADMIN)).build());
        
        Account account = Account.create();
        account.setOrgMembership(IDENTIFIER);
        mockEditAccount(mockAccountService, account);

        service.removeMember(TEST_APP_ID, IDENTIFIER, TEST_USER_ID);
        
        verify(mockAccountService).editAccount(eq(ACCOUNT_ID), any());
        assertNull(account.getOrgMembership());
    }

    @Test(expectedExceptions = BadRequestException.class, 
            expectedExceptionsMessageRegExp = ".*Account is not a member of organization.*")
    public void removeMemberNotAMember() {
        RequestContext.set(new RequestContext.Builder()
                .withCallerRoles(ImmutableSet.of(ADMIN)).build());

        Account account = Account.create();
        account.setOrgMembership("some-other-org");
        mockEditAccount(mockAccountService, account);
        
        service.removeMember(TEST_APP_ID, IDENTIFIER, TEST_USER_ID);
    }
    
    @Test(expectedExceptions = BadRequestException.class, 
            expectedExceptionsMessageRegExp = ".*Account is not a member of organization.*")
    public void removeMemberNoMembership() {
        RequestContext.set(new RequestContext.Builder()
                .withCallerRoles(ImmutableSet.of(ADMIN)).build());
        
        Account account = Account.create();
        mockEditAccount(mockAccountService, account);

        service.removeMember(TEST_APP_ID, IDENTIFIER, TEST_USER_ID);
    }
    
    @Test(expectedExceptions = EntityNotFoundException.class, 
            expectedExceptionsMessageRegExp = "Account not found.")
    public void removeMemberAccountNotFound() {
        RequestContext.set(new RequestContext.Builder()
                .withCallerRoles(ImmutableSet.of(ADMIN)).build());
        
        doThrow(new EntityNotFoundException(Account.class))
            .when(mockAccountService).editAccount(any(), any());

        service.removeMember(TEST_APP_ID, IDENTIFIER, TEST_USER_ID);
    }

    @Test
    public void deleteAllOrganizations() {
        service.deleteAllOrganizations(TEST_APP_ID);
        verify(mockOrgDao).deleteAllOrganizations(TEST_APP_ID);
    }
}
