package org.sagebionetworks.bridge.services;

import static java.lang.Boolean.FALSE;
import static java.lang.Boolean.TRUE;
import static org.sagebionetworks.bridge.BridgeConstants.TEST_USER_GROUP;
import static org.sagebionetworks.bridge.RequestContext.NULL_INSTANCE;
import static org.sagebionetworks.bridge.Roles.ADMIN;
import static org.sagebionetworks.bridge.Roles.DEVELOPER;
import static org.sagebionetworks.bridge.Roles.ORG_ADMIN;
import static org.sagebionetworks.bridge.Roles.RESEARCHER;
import static org.sagebionetworks.bridge.Roles.SUPERADMIN;
import static org.sagebionetworks.bridge.Roles.WORKER;
import static org.sagebionetworks.bridge.TestConstants.CREATED_ON;
import static org.sagebionetworks.bridge.TestConstants.EMAIL;
import static org.sagebionetworks.bridge.TestConstants.GUID;
import static org.sagebionetworks.bridge.TestConstants.MODIFIED_ON;
import static org.sagebionetworks.bridge.TestConstants.PHONE;
import static org.sagebionetworks.bridge.TestConstants.SYNAPSE_USER_ID;
import static org.sagebionetworks.bridge.TestConstants.TEST_APP_ID;
import static org.sagebionetworks.bridge.TestConstants.TEST_ORG_ID;
import static org.sagebionetworks.bridge.TestConstants.TEST_USER_ID;
import static org.sagebionetworks.bridge.dao.AccountDao.MIGRATION_VERSION;
import static org.sagebionetworks.bridge.models.accounts.AccountStatus.DISABLED;
import static org.sagebionetworks.bridge.models.accounts.AccountStatus.ENABLED;
import static org.sagebionetworks.bridge.models.accounts.AccountStatus.UNVERIFIED;
import static org.sagebionetworks.bridge.models.accounts.PasswordAlgorithm.DEFAULT_PASSWORD_ALGORITHM;
import static org.sagebionetworks.bridge.models.accounts.SharingScope.NO_SHARING;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertNotNull;
import static org.testng.Assert.assertNull;

import java.util.List;
import java.util.Optional;
import java.util.Set;

import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;
import org.mockito.Spy;
import org.sagebionetworks.bridge.RequestContext;
import org.sagebionetworks.bridge.Roles;
import org.sagebionetworks.bridge.TestConstants;
import org.sagebionetworks.bridge.TestUtils;
import org.sagebionetworks.bridge.cache.CacheProvider;
import org.sagebionetworks.bridge.dao.AccountDao;
import org.sagebionetworks.bridge.exceptions.BadRequestException;
import org.sagebionetworks.bridge.exceptions.EntityNotFoundException;
import org.sagebionetworks.bridge.exceptions.InvalidEntityException;
import org.sagebionetworks.bridge.exceptions.LimitExceededException;
import org.sagebionetworks.bridge.models.PagedResourceList;
import org.sagebionetworks.bridge.models.accounts.Account;
import org.sagebionetworks.bridge.models.accounts.AccountId;
import org.sagebionetworks.bridge.models.accounts.AccountStatus;
import org.sagebionetworks.bridge.models.accounts.AccountSummary;
import org.sagebionetworks.bridge.models.accounts.Phone;
import org.sagebionetworks.bridge.models.apps.App;
import org.sagebionetworks.bridge.models.apps.PasswordPolicy;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;

public class AdminAccountServiceTest extends Mockito {
    
    @Mock
    AppService mockAppService;
    
    @Mock
    AccountWorkflowService mockAccountWorkflowService;
    
    @Mock
    SmsService mockSmsService;
    
    @Mock
    AccountDao mockAccountDao;
    
    @Mock
    CacheProvider mockCacheProvider;
    
    @Mock
    PermissionService mockPermissionService;
    
    @Mock
    RequestInfoService mockRequestInfoService;
    
    @Captor
    ArgumentCaptor<AccountId> accountIdCaptor;
    
    @Captor
    ArgumentCaptor<Account> accountCaptor;

    @InjectMocks
    @Spy
    AdminAccountService service;
    
    @BeforeMethod
    public void beforeMethod() {
        MockitoAnnotations.initMocks(this);
        
        when(service.getCreatedOn()).thenReturn(CREATED_ON);
        when(service.getModifiedOn()).thenReturn(MODIFIED_ON);
        when(service.generateGUID()).thenReturn(GUID);
    }
    
    @AfterMethod
    public void afterMethod() {
        RequestContext.set(RequestContext.NULL_INSTANCE);
    }
    
    @Test
    public void getAccount_forSelf() {
        RequestContext.set(new RequestContext.Builder().withCallerUserId(TEST_USER_ID).build());
        AccountId accountId = AccountId.forSynapseUserId(TEST_APP_ID, "12345");
        
        Account account = Account.create();
        account.setAdmin(true);
        account.setId(TEST_USER_ID);
        when(mockAccountDao.getAccount(accountId)).thenReturn(Optional.of(account));

        Optional<Account> retValue = service.getAccount(TEST_APP_ID, "synapseuserid:12345");
        assertEquals(retValue.get(), account);
        
        verify(mockAccountDao).getAccount(accountId);
    }
    
    @Test
    public void getAccount_forOrgMember() {
        RequestContext.set(new RequestContext.Builder().withCallerUserId(TEST_USER_ID)
                .withCallerOrgMembership(TEST_ORG_ID).build());
        AccountId accountId = AccountId.forSynapseUserId(TEST_APP_ID, "12345");
        
        Account account = Account.create();
        account.setAdmin(true);
        account.setId("some-other-person");
        account.setOrgMembership(TEST_ORG_ID);
        when(mockAccountDao.getAccount(accountId)).thenReturn(Optional.of(account));

        Optional<Account> retValue = service.getAccount(TEST_APP_ID, "synapseuserid:12345");
        assertEquals(retValue.get(), account);
        
        verify(mockAccountDao).getAccount(accountId);
    }
    
    @Test
    public void getAccount_forAdmin() {
        RequestContext.set(new RequestContext.Builder().withCallerUserId(TEST_USER_ID)
                .withCallerRoles(ImmutableSet.of(ADMIN)).build());
        AccountId accountId = AccountId.forSynapseUserId(TEST_APP_ID, "12345");
        
        Account account = Account.create();
        account.setAdmin(true);
        account.setId("some-other-person");
        when(mockAccountDao.getAccount(accountId)).thenReturn(Optional.of(account));

        Optional<Account> retValue = service.getAccount(TEST_APP_ID, "synapseuserid:12345");
        assertEquals(retValue.get(), account);
        
        verify(mockAccountDao).getAccount(accountId);
    }
    
    @Test
    public void getAccount_noAccount() {
        RequestContext.set(new RequestContext.Builder().withCallerUserId(TEST_USER_ID)
                .withCallerRoles(ImmutableSet.of(ADMIN)).build());
        AccountId accountId = AccountId.forSynapseUserId(TEST_APP_ID, "12345");
        
        when(mockAccountDao.getAccount(accountId)).thenReturn(Optional.empty());
        
        Optional<Account> optional = service.getAccount(TEST_APP_ID, "synapseuserid:12345");
        assertFalse(optional.isPresent());
    }
    
    @Test
    public void getAccount_accountIsNotAnAdmin() {
        RequestContext.set(new RequestContext.Builder().withCallerUserId(TEST_USER_ID)
                .withCallerRoles(ImmutableSet.of(ADMIN)).build());
        AccountId accountId = AccountId.forSynapseUserId(TEST_APP_ID, "12345");
        
        Account account = Account.create();
        when(mockAccountDao.getAccount(accountId)).thenReturn(Optional.of(account));
        
        Optional<Account> optional = service.getAccount(TEST_APP_ID, "synapseuserid:12345");
        assertFalse(optional.isPresent());
    }
    
    @Test
    public void getAppIdsForUser() {
        List<String> apps = ImmutableList.of("app1", "app2");
        when(mockAccountDao.getAppIdForUser(SYNAPSE_USER_ID)).thenReturn(apps);

        List<String> returnVal = service.getAppIdsForUser(SYNAPSE_USER_ID);
        assertEquals(returnVal, apps);
        verify(mockAccountDao).getAppIdForUser(SYNAPSE_USER_ID);
    }

    @Test(expectedExceptions = BadRequestException.class)
    public void getAppIdsForUser_noSynapseuserId() {
        service.getAppIdsForUser("  ");
    }

    @Test(expectedExceptions = BadRequestException.class, expectedExceptionsMessageRegExp =
            "Account does not have a Synapse user")
    public void getAppIdsForUser_NullSynapseUserId() {
        service.getAppIdsForUser(null);
    }

    @Test(expectedExceptions = BadRequestException.class, expectedExceptionsMessageRegExp =
            "Account does not have a Synapse user")
    public void getAppIdsForUser_EmptySynapseUserId() {
        service.getAppIdsForUser("");
    }

    @Test(expectedExceptions = BadRequestException.class, expectedExceptionsMessageRegExp =
            "Account does not have a Synapse user")
    public void getAppIdsForUser_BlankSynapseUserId() {
        service.getAppIdsForUser("   ");
    }

    @Test
    public void createAccount() {
        RequestContext.set(new RequestContext.Builder().withCallerOrgMembership("another-org").build());
        
        App app = App.create();
        app.setPasswordPolicy(PasswordPolicy.DEFAULT_PASSWORD_POLICY);
        app.setUserProfileAttributes(ImmutableSet.of("a"));
        when(mockAppService.getApp(TEST_APP_ID)).thenReturn(app);
        
        Account account = Account.create();
        account.setAppId(TEST_APP_ID);
        account.setEmail(EMAIL);
        account.setPhone(PHONE);
        account.setFirstName("firstName");
        account.setLastName("lastName");
        account.setSynapseUserId(SYNAPSE_USER_ID);
        account.setClientData(TestUtils.getClientData());
        account.setLanguages(TestConstants.LANGUAGES);
        account.setClientTimeZone("America/Los_Angeles");
        account.setOrgMembership(TEST_ORG_ID);
        account.getAttributes().put("a", "test");
        account.setPassword("P@ssword!1");

        Account retValue = service.createAccount(TEST_APP_ID, account);
        
        assertEquals(retValue.getId(), GUID);
        assertEquals(retValue.getAppId(), TEST_APP_ID);
        assertEquals(retValue.getEmailVerified(), FALSE);
        assertEquals(retValue.getPhoneVerified(), FALSE);
        assertEquals(retValue.getHealthCode(), GUID);
        assertEquals(retValue.getStatus(), ENABLED); // because synapse user ID is present
        assertEquals(retValue.getCreatedOn(), CREATED_ON);
        assertEquals(retValue.getModifiedOn(), CREATED_ON);
        assertEquals(retValue.getPasswordModifiedOn(), CREATED_ON);
        assertEquals(retValue.getMigrationVersion(), MIGRATION_VERSION);
        assertEquals(retValue.getDataGroups(), ImmutableSet.of(TEST_USER_GROUP));
        assertEquals(retValue.getSharingScope(), NO_SHARING);
        assertEquals(retValue.getNotifyByEmail(), FALSE);
        assertEquals(retValue.getAppId(), TEST_APP_ID);
        assertEquals(retValue.getFirstName(), "firstName");
        assertEquals(retValue.getLastName(), "lastName");
        assertEquals(retValue.getSynapseUserId(), SYNAPSE_USER_ID);
        assertEquals(retValue.getClientData(), TestUtils.getClientData());
        assertEquals(retValue.getLanguages(), TestConstants.LANGUAGES);
        assertEquals(retValue.getClientTimeZone(), "America/Los_Angeles");
        assertEquals(retValue.getOrgMembership(), "another-org");
        assertEquals(retValue.getAttributes().size(), 1);
        assertEquals(retValue.getAttributes().get("a"), "test");
        assertEquals(retValue.getPasswordAlgorithm(), DEFAULT_PASSWORD_ALGORITHM);
        assertNotNull(retValue.getPasswordHash());
        assertNull(retValue.getPassword());
        
        verify(mockAccountWorkflowService).sendEmailVerificationToken(app, GUID, EMAIL);
        verify(mockSmsService).optInPhoneNumber(GUID, PHONE);
        verify(mockAccountWorkflowService).sendPhoneVerificationToken(app, GUID, PHONE);
    }
    
    @Test
    public void createAccount_updatesPermissionsFromRoles() {
        RequestContext.set(new RequestContext.Builder()
                .withCallerRoles(ImmutableSet.of(ADMIN)).build());
        
        App app = App.create();
        when(mockAppService.getApp(TEST_APP_ID)).thenReturn(app);
        
        Account account = Account.create();
        account.setEmail(EMAIL);
        account.setRoles(ImmutableSet.of(DEVELOPER));
        
        service.createAccount(TEST_APP_ID, account);
        
        verify(mockPermissionService).updatePermissionsFromRoles(accountCaptor.capture(), accountCaptor.capture());
        
        List<Account> capturedAccounts = accountCaptor.getAllValues();
        assertEquals(capturedAccounts.get(0).getRoles(), ImmutableSet.of(DEVELOPER));
        assertEquals(capturedAccounts.get(1).getRoles(), ImmutableSet.of());
    }
    
    @Test
    public void createAccount_permissionsDoNotUpdateWithNoRoles() {
        RequestContext.set(new RequestContext.Builder()
                .withCallerRoles(ImmutableSet.of(ADMIN)).build());
        
        App app = App.create();
        when(mockAppService.getApp(TEST_APP_ID)).thenReturn(app);
        
        Account account = Account.create();
        account.setEmail(EMAIL);
        account.setRoles(ImmutableSet.of());
        
        service.createAccount(TEST_APP_ID, account);
        
        verifyZeroInteractions(mockPermissionService);
    }
    
    @Test
    public void createAccount_adminCanSetOrgMembership() {
        RequestContext.set(new RequestContext.Builder()
                .withCallerRoles(ImmutableSet.of(ADMIN)).build());
        
        App app = App.create();
        app.setPasswordPolicy(PasswordPolicy.DEFAULT_PASSWORD_POLICY);
        app.setUserProfileAttributes(ImmutableSet.of());
        when(mockAppService.getApp(TEST_APP_ID)).thenReturn(app);
        
        Account account = Account.create();
        account.setAppId(TEST_APP_ID);
        account.setEmail(EMAIL);
        account.setOrgMembership(TEST_ORG_ID);

        Account retValue = service.createAccount(TEST_APP_ID, account);
        
        assertEquals(retValue.getOrgMembership(), TEST_ORG_ID);
    }
    
    @Test
    public void createAccount_orgAdminCanSetOrgMembership() {
        RequestContext.set(new RequestContext.Builder()
                .withCallerOrgMembership(TEST_ORG_ID)
                .withCallerRoles(ImmutableSet.of(ORG_ADMIN)).build());
        
        App app = App.create();
        app.setPasswordPolicy(PasswordPolicy.DEFAULT_PASSWORD_POLICY);
        app.setUserProfileAttributes(ImmutableSet.of());
        when(mockAppService.getApp(TEST_APP_ID)).thenReturn(app);
        
        Account account = Account.create();
        account.setAppId(TEST_APP_ID);
        account.setEmail(EMAIL);
        account.setOrgMembership(TEST_ORG_ID);

        Account retValue = service.createAccount(TEST_APP_ID, account);
        
        assertEquals(retValue.getOrgMembership(), TEST_ORG_ID);
    }
    
    @Test(expectedExceptions = EntityNotFoundException.class, 
            expectedExceptionsMessageRegExp = "App not found.")
    public void createAccount_appNotFound() {
        Account account = Account.create();
        
        service.createAccount(TEST_APP_ID, account);
    }
    
    @Test(expectedExceptions = LimitExceededException.class)
    public void createAccount_limitExceeded() {
        App app = App.create();
        app.setIdentifier(TEST_APP_ID);
        app.setAccountLimit(10);
        when(mockAppService.getApp(TEST_APP_ID)).thenReturn(app);
        
        PagedResourceList<AccountSummary> page = new PagedResourceList<>(ImmutableList.of(), 10);
        when(mockAccountDao.getPagedAccountSummaries(eq(TEST_APP_ID), any())).thenReturn(page);

        Account account = Account.create();
        
        service.createAccount(TEST_APP_ID, account);
    }
    
    @Test
    public void createAccount_limitNotExceeded() {
        App app = App.create();
        app.setIdentifier(TEST_APP_ID);
        app.setAccountLimit(20);
        when(mockAppService.getApp(TEST_APP_ID)).thenReturn(app);
        
        PagedResourceList<AccountSummary> page = new PagedResourceList<>(ImmutableList.of(), 10);
        when(mockAccountDao.getPagedAccountSummaries(eq(TEST_APP_ID), any())).thenReturn(page);

        Account account = Account.create();
        account.setEmail(EMAIL);
        
        service.createAccount(TEST_APP_ID, account);
        
        verify(mockAccountDao).createAccount(any());
    }
    
    @Test
    public void createAccount_emailUnverified() {
        App app = App.create();
        app.setIdentifier(TEST_APP_ID);
        when(mockAppService.getApp(TEST_APP_ID)).thenReturn(app);
        
        Account account = Account.create();
        account.setEmail(EMAIL);
        
        Account retValue = service.createAccount(TEST_APP_ID, account);
        assertEquals(retValue.getStatus(), UNVERIFIED);
    }
    
    @Test
    public void createAccount_phoneUnverified() {
        App app = App.create();
        when(mockAppService.getApp(TEST_APP_ID)).thenReturn(app);
        
        Account account = Account.create();
        account.setPhone(PHONE);
        
        Account retValue = service.createAccount(TEST_APP_ID, account);
        assertEquals(retValue.getStatus(), UNVERIFIED);
    }
    
    @Test
    public void createAccount_synapseUserIdVerified() {
        App app = App.create();
        when(mockAppService.getApp(TEST_APP_ID)).thenReturn(app);
        
        Account account = Account.create();
        account.setSynapseUserId(SYNAPSE_USER_ID);
        
        Account retValue = service.createAccount(TEST_APP_ID, account);
        assertEquals(retValue.getStatus(), ENABLED);
    }
    
    @Test(expectedExceptions = InvalidEntityException.class)
    public void createAccount_validates() {
        App app = App.create();
        when(mockAppService.getApp(TEST_APP_ID)).thenReturn(app);
        
        Account account = Account.create();
        
        service.createAccount(TEST_APP_ID, account);
    }
    
    @Test
    public void updateRoles_workerCannotCreateWorker() {
        Set<Roles> retValue = service.updateRoles(NULL_INSTANCE, ImmutableSet.of(WORKER), ImmutableSet.of());
        assertEquals(retValue, ImmutableSet.of());
    }
    
    @Test
    public void updateRoles_developerCannotCreateDeveloper() {
        Set<Roles> retValue = service.updateRoles(NULL_INSTANCE, ImmutableSet.of(DEVELOPER), ImmutableSet.of());
        assertEquals(retValue, ImmutableSet.of());
    }
    
    @Test
    public void updateRoles_researcherCanCreateDevelopers() {
        RequestContext context = new RequestContext.Builder().withCallerRoles(ImmutableSet.of(RESEARCHER)).build();
        
        Set<Roles> retValue = service.updateRoles(context, ImmutableSet.of(DEVELOPER), ImmutableSet.of());
        assertEquals(retValue, ImmutableSet.of(DEVELOPER));
    }
    
    @Test
    public void updateRoles_adminCanCreateDeveloperAndResearcher() {
        RequestContext context = new RequestContext.Builder().withCallerRoles(ImmutableSet.of(ADMIN)).build();
        
        Set<Roles> retValue = service.updateRoles(context, ImmutableSet.of(DEVELOPER, RESEARCHER), ImmutableSet.of());
        assertEquals(retValue, ImmutableSet.of(DEVELOPER, RESEARCHER));
    }
    
    @Test
    public void updateRoles_superadminCanCreateEverybody() {
        RequestContext context = new RequestContext.Builder().withCallerRoles(ImmutableSet.of(SUPERADMIN)).build();
        
        Set<Roles> retValue = service.updateRoles(context,
                ImmutableSet.of(SUPERADMIN, ADMIN, DEVELOPER, RESEARCHER, WORKER), ImmutableSet.of());
        assertEquals(retValue, ImmutableSet.of(SUPERADMIN, ADMIN, DEVELOPER, RESEARCHER, WORKER));
    }
    
    public void updateRoles_workerCannotUpdateAnybody() {
        RequestContext context = new RequestContext.Builder().withCallerRoles(ImmutableSet.of(WORKER)).build();
        
        Set<Roles> retValue = service.updateRoles(context,
                ImmutableSet.of(SUPERADMIN, ADMIN, DEVELOPER, RESEARCHER, WORKER), ImmutableSet.of());
        assertEquals(retValue, ImmutableSet.of());
    }
    
    @Test
    public void updateRoles_developerCannotUpdateAnybody() {
        RequestContext context = new RequestContext.Builder().withCallerRoles(ImmutableSet.of(DEVELOPER)).build();
        
        Set<Roles> retValue = service.updateRoles(context,
                ImmutableSet.of(SUPERADMIN, ADMIN, DEVELOPER, RESEARCHER, WORKER), ImmutableSet.of());
        assertEquals(retValue, ImmutableSet.of());
    }
    
    @Test
    public void updateRoles_researcherCanUpdateDevelopers() {
        RequestContext context = new RequestContext.Builder().withCallerRoles(ImmutableSet.of(RESEARCHER)).build();
        
        Set<Roles> retValue = service.updateRoles(context, ImmutableSet.of(DEVELOPER), ImmutableSet.of());
        assertEquals(retValue, ImmutableSet.of(DEVELOPER));
    }
    
    @Test
    public void updateRoles_superadminCanRemoveSuperadmin() {
        RequestContext context = new RequestContext.Builder().withCallerRoles(ImmutableSet.of(SUPERADMIN)).build();
        
        Set<Roles> retValue = service.updateRoles(context, ImmutableSet.of(), ImmutableSet.of(SUPERADMIN));
        assertEquals(retValue, ImmutableSet.of());
    }
    
    @Test
    public void updateRoles_superadminCanRemoveAdmin() {
        RequestContext context = new RequestContext.Builder().withCallerRoles(ImmutableSet.of(SUPERADMIN)).build();
        
        Set<Roles> retValue = service.updateRoles(context, ImmutableSet.of(), ImmutableSet.of(ADMIN));
        assertEquals(retValue, ImmutableSet.of());
    }

    @Test
    public void updateRoles_superadminCanRemoveResearcher() {
        RequestContext context = new RequestContext.Builder().withCallerRoles(ImmutableSet.of(SUPERADMIN)).build();
        
        Set<Roles> retValue = service.updateRoles(context, ImmutableSet.of(), ImmutableSet.of(RESEARCHER));
        assertEquals(retValue, ImmutableSet.of());
    }

    @Test
    public void updateRoles_superadminCanRemoveDeveloper() {
        RequestContext context = new RequestContext.Builder().withCallerRoles(ImmutableSet.of(SUPERADMIN)).build();
        
        Set<Roles> retValue = service.updateRoles(context, ImmutableSet.of(), ImmutableSet.of(DEVELOPER));
        assertEquals(retValue, ImmutableSet.of());
    }
    
    @Test
    public void updateRoles_superadminCanRemoveWorker() {
        RequestContext context = new RequestContext.Builder().withCallerRoles(ImmutableSet.of(SUPERADMIN)).build();
        
        Set<Roles> retValue = service.updateRoles(context, ImmutableSet.of(), ImmutableSet.of(WORKER));
        assertEquals(retValue, ImmutableSet.of());
    }
    
    @Test
    public void updateRoles_adminCannotRemoveSuperadmin() {
        RequestContext context = new RequestContext.Builder().withCallerRoles(ImmutableSet.of(ADMIN)).build();
        
        Set<Roles> retValue = service.updateRoles(context, ImmutableSet.of(), ImmutableSet.of(SUPERADMIN));
        assertEquals(retValue, ImmutableSet.of(SUPERADMIN));
    }
    
    @Test
    public void updateRoles_adminCannotRemoveAdmin() {
        RequestContext context = new RequestContext.Builder().withCallerRoles(ImmutableSet.of(ADMIN)).build();
        
        Set<Roles> retValue = service.updateRoles(context, ImmutableSet.of(), ImmutableSet.of(ADMIN));
        assertEquals(retValue, ImmutableSet.of(ADMIN));
    }

    @Test
    public void updateRoles_adminCanRemoveResearcher() {
        RequestContext context = new RequestContext.Builder().withCallerRoles(ImmutableSet.of(ADMIN)).build();
        
        Set<Roles> retValue = service.updateRoles(context, ImmutableSet.of(), ImmutableSet.of(RESEARCHER));
        assertEquals(retValue, ImmutableSet.of());
    }

    @Test
    public void updateRoles_adminCanRemoveDeveloper() {
        RequestContext context = new RequestContext.Builder().withCallerRoles(ImmutableSet.of(ADMIN)).build();
        
        Set<Roles> retValue = service.updateRoles(context, ImmutableSet.of(), ImmutableSet.of(DEVELOPER));
        assertEquals(retValue, ImmutableSet.of());
    }    
    
    @Test
    public void updateRoles_adminCannotRemoveWorker() {
        RequestContext context = new RequestContext.Builder().withCallerRoles(ImmutableSet.of(ADMIN)).build();
        
        Set<Roles> retValue = service.updateRoles(context, ImmutableSet.of(), ImmutableSet.of(WORKER));
        assertEquals(retValue, ImmutableSet.of(WORKER));
    }
    
    @Test
    public void updateRoles_researcherCannotRemoveSuperadmin() {
        RequestContext context = new RequestContext.Builder().withCallerRoles(ImmutableSet.of(RESEARCHER)).build();
        
        Set<Roles> retValue = service.updateRoles(context, ImmutableSet.of(), ImmutableSet.of(SUPERADMIN));
        assertEquals(retValue, ImmutableSet.of(SUPERADMIN));
    }
    
    @Test
    public void updateRoles_researcherCannotRemoveAdmin() {
        RequestContext context = new RequestContext.Builder().withCallerRoles(ImmutableSet.of(RESEARCHER)).build();
        
        Set<Roles> retValue = service.updateRoles(context, ImmutableSet.of(), ImmutableSet.of(ADMIN));
        assertEquals(retValue, ImmutableSet.of(ADMIN));
    }

    @Test
    public void updateRoles_researcherCanRemoveResearcher() {
        RequestContext context = new RequestContext.Builder().withCallerRoles(ImmutableSet.of(RESEARCHER)).build();
        
        Set<Roles> retValue = service.updateRoles(context, ImmutableSet.of(), ImmutableSet.of(RESEARCHER));
        assertEquals(retValue, ImmutableSet.of(RESEARCHER));
    }

    @Test
    public void updateRoles_researcherCanRemoveDeveloper() {
        RequestContext context = new RequestContext.Builder().withCallerRoles(ImmutableSet.of(RESEARCHER)).build();
        
        Set<Roles> retValue = service.updateRoles(context, ImmutableSet.of(), ImmutableSet.of(DEVELOPER));
        assertEquals(retValue, ImmutableSet.of());
    }
    
    @Test
    public void updateRoles_researcherCannotRemoveWorker() {
        RequestContext context = new RequestContext.Builder().withCallerRoles(ImmutableSet.of(RESEARCHER)).build();
        
        Set<Roles> retValue = service.updateRoles(context, ImmutableSet.of(), ImmutableSet.of(WORKER));
        assertEquals(retValue, ImmutableSet.of(WORKER));
    }
    
    @Test
    public void updateRoles_developerCannotRemoveAnybody() {
        RequestContext context = new RequestContext.Builder().withCallerRoles(ImmutableSet.of(DEVELOPER)).build();
        
        Set<Roles> roles = ImmutableSet.of(DEVELOPER, RESEARCHER, ADMIN, SUPERADMIN, WORKER);
        Set<Roles> retValue = service.updateRoles(context, ImmutableSet.of(), roles);
        assertEquals(retValue, roles);
    }
    
    @Test
    public void updateAccount() {
        App app = App.create();
        app.setUserProfileAttributes(ImmutableSet.of("a"));
        when(mockAppService.getApp(TEST_APP_ID)).thenReturn(app);
        
        Account persistedAccount = Account.create();
        persistedAccount.setAdmin(TRUE);
        persistedAccount.setId(TEST_USER_ID);
        persistedAccount.setAppId(TEST_APP_ID);
        persistedAccount.setEmail(EMAIL);
        persistedAccount.setPhone(PHONE);
        persistedAccount.setEmailVerified(TRUE);
        persistedAccount.setPhoneVerified(TRUE);
        persistedAccount.setHealthCode(GUID);
        persistedAccount.setFirstName("firstName");
        persistedAccount.setLastName("lastName");
        persistedAccount.setSynapseUserId(SYNAPSE_USER_ID);
        persistedAccount.setClientData(TestUtils.getClientData());
        persistedAccount.setLanguages(TestConstants.LANGUAGES);
        persistedAccount.setClientTimeZone("America/Los_Angeles");
        persistedAccount.setOrgMembership(TEST_ORG_ID);
        persistedAccount.getAttributes().put("a", "test");
        persistedAccount.setPassword("P@ssword!1");
        persistedAccount.setCreatedOn(CREATED_ON);
        persistedAccount.setPasswordModifiedOn(MODIFIED_ON);
        persistedAccount.setMigrationVersion(MIGRATION_VERSION);
        persistedAccount.setDataGroups(ImmutableSet.of(TEST_USER_GROUP));
        persistedAccount.setNotifyByEmail(FALSE);
        persistedAccount.setPasswordAlgorithm(DEFAULT_PASSWORD_ALGORITHM);
        persistedAccount.setPasswordHash("something-here");
        persistedAccount.setStatus(AccountStatus.DISABLED);

        when(mockAccountDao.getAccount(any())).thenReturn(Optional.of(persistedAccount));

        Phone changedPhone = new Phone("2022739988", "US");
        
        Account account = Account.create();
        account.setId(TEST_USER_ID);
        account.setAppId("changed-app-id");
        account.setEmail("changed-email@email.com");
        account.setPhone(changedPhone);
        account.setEmailVerified(TRUE); // ignored
        account.setPhoneVerified(TRUE); // ignored
        account.setFirstName("changed-firstName");
        account.setLastName("changed-lastName");
        account.setSynapseUserId("67890");
        account.setClientData(null);
        account.setLanguages(ImmutableList.of("ch"));
        account.setClientTimeZone("America/Chicago");
        account.setOrgMembership("changed-org");
        account.getAttributes().put("a", "changed-value");
        account.setPassword("F00boo!1");
        account.setNotifyByEmail(TRUE);
        account.setStatus(AccountStatus.ENABLED);
        
        Account retValue = service.updateAccount(TEST_APP_ID, account);
        
        assertEquals(retValue.getId(), TEST_USER_ID);
        assertEquals(retValue.getAppId(), TEST_APP_ID);
        assertEquals(retValue.getEmailVerified(), FALSE);
        assertEquals(retValue.getPhoneVerified(), FALSE);
        assertEquals(retValue.getHealthCode(), GUID);
        assertEquals(retValue.getCreatedOn(), CREATED_ON);
        assertEquals(retValue.getModifiedOn(), MODIFIED_ON);
        assertEquals(retValue.getPasswordModifiedOn(), MODIFIED_ON);
        assertEquals(retValue.getMigrationVersion(), MIGRATION_VERSION);
        assertEquals(retValue.getDataGroups(), ImmutableSet.of(TEST_USER_GROUP));
        assertEquals(retValue.getSharingScope(), NO_SHARING);
        assertEquals(retValue.getNotifyByEmail(), TRUE);
        assertEquals(retValue.getAppId(), TEST_APP_ID);
        assertEquals(retValue.getFirstName(), "changed-firstName");
        assertEquals(retValue.getLastName(), "changed-lastName");
        assertEquals(retValue.getSynapseUserId(), "67890");
        assertNull(retValue.getClientData());
        assertEquals(retValue.getLanguages(), ImmutableList.of("ch"));
        assertEquals(retValue.getClientTimeZone(), "America/Chicago");
        assertEquals(retValue.getOrgMembership(), TEST_ORG_ID);
        assertEquals(retValue.getAttributes().size(), 1);
        assertEquals(retValue.getAttributes().get("a"), "changed-value");
        assertEquals(retValue.getPasswordAlgorithm(), DEFAULT_PASSWORD_ALGORITHM);
        assertNotNull(retValue.getPasswordHash());
        assertNull(retValue.getPassword());
        assertEquals(retValue.getStatus(), DISABLED);
        
        verify(mockAccountDao).updateAccount(account);
        verify(mockAccountWorkflowService).sendEmailVerificationToken(app, TEST_USER_ID, "changed-email@email.com");
        verify(mockSmsService).optInPhoneNumber(TEST_USER_ID, changedPhone);
        verify(mockAccountWorkflowService).sendPhoneVerificationToken(app, TEST_USER_ID, changedPhone);
    }
    
    @Test
    public void updateAccount_changeRoleCausesPermissionUpdate() {
        RequestContext.set(new RequestContext.Builder().withCallerRoles(ImmutableSet.of(ADMIN)).build());
        App app = App.create();
        app.setUserProfileAttributes(ImmutableSet.of("a"));
        when(mockAppService.getApp(TEST_APP_ID)).thenReturn(app);
        
        Account persistedAccount = Account.create();
        persistedAccount.setAdmin(TRUE);
        persistedAccount.setEmail(EMAIL);
        persistedAccount.setEmailVerified(TRUE);
        persistedAccount.setRoles(ImmutableSet.of(RESEARCHER));
        
        when(mockAccountDao.getAccount(any())).thenReturn(Optional.of(persistedAccount));
        
        Account account = Account.create();
        account.setId(TEST_USER_ID);
        account.setEmail(EMAIL);
        account.setEmailVerified(TRUE);
        account.setRoles(ImmutableSet.of(DEVELOPER));
        
        service.updateAccount(TEST_APP_ID, account);
        
        verify(mockPermissionService).updatePermissionsFromRoles(eq(account), eq(persistedAccount));
    }
    
    @Test
    public void updateAccount_roleAdditionCausesPermissionUpdate() {
        RequestContext.set(new RequestContext.Builder().withCallerRoles(ImmutableSet.of(ADMIN)).build());
        App app = App.create();
        app.setUserProfileAttributes(ImmutableSet.of("a"));
        when(mockAppService.getApp(TEST_APP_ID)).thenReturn(app);
        
        Account persistedAccount = Account.create();
        persistedAccount.setAdmin(TRUE);
        persistedAccount.setEmail(EMAIL);
        persistedAccount.setEmailVerified(TRUE);
        persistedAccount.setRoles(ImmutableSet.of(RESEARCHER));
        
        when(mockAccountDao.getAccount(any())).thenReturn(Optional.of(persistedAccount));
        
        Account account = Account.create();
        account.setId(TEST_USER_ID);
        account.setEmail(EMAIL);
        account.setEmailVerified(TRUE);
        account.setRoles(ImmutableSet.of(DEVELOPER, RESEARCHER));
        
        service.updateAccount(TEST_APP_ID, account);
        
        verify(mockPermissionService).updatePermissionsFromRoles(eq(account), eq(persistedAccount));
    }
    
    @Test
    public void updateAccount_roleRemovalCausesPermissionUpdate() {
        RequestContext.set(new RequestContext.Builder().withCallerRoles(ImmutableSet.of(ADMIN)).build());
        App app = App.create();
        app.setUserProfileAttributes(ImmutableSet.of("a"));
        when(mockAppService.getApp(TEST_APP_ID)).thenReturn(app);
        
        Account persistedAccount = Account.create();
        persistedAccount.setAdmin(TRUE);
        persistedAccount.setEmail(EMAIL);
        persistedAccount.setEmailVerified(TRUE);
        persistedAccount.setRoles(ImmutableSet.of(RESEARCHER, DEVELOPER));
        
        when(mockAccountDao.getAccount(any())).thenReturn(Optional.of(persistedAccount));
        
        Account account = Account.create();
        account.setId(TEST_USER_ID);
        account.setEmail(EMAIL);
        account.setEmailVerified(TRUE);
        account.setRoles(ImmutableSet.of(DEVELOPER));
        
        service.updateAccount(TEST_APP_ID, account);
        
        verify(mockPermissionService).updatePermissionsFromRoles(eq(account), eq(persistedAccount));
    }
    
    @Test
    public void updateAccount_noRoleUpdateDoesNotCausePermissionUpdate() {
        RequestContext.set(new RequestContext.Builder().withCallerRoles(ImmutableSet.of(ADMIN)).build());
        App app = App.create();
        app.setUserProfileAttributes(ImmutableSet.of("a"));
        when(mockAppService.getApp(TEST_APP_ID)).thenReturn(app);
        
        Account persistedAccount = Account.create();
        persistedAccount.setAdmin(TRUE);
        persistedAccount.setEmail(EMAIL);
        persistedAccount.setEmailVerified(TRUE);
        persistedAccount.setRoles(ImmutableSet.of(RESEARCHER, DEVELOPER));
        
        when(mockAccountDao.getAccount(any())).thenReturn(Optional.of(persistedAccount));
        
        Account account = Account.create();
        account.setId(TEST_USER_ID);
        account.setEmail(EMAIL);
        account.setEmailVerified(TRUE);
        account.setRoles(ImmutableSet.of(DEVELOPER, RESEARCHER));
        
        service.updateAccount(TEST_APP_ID, account);
        
        verifyZeroInteractions(mockPermissionService);
    }
    
    @Test
    public void updateAccount_doesNotChangeEmailOrPhone() {
        App app = App.create();
        when(mockAppService.getApp(TEST_APP_ID)).thenReturn(app);
        
        Account persistedAccount = Account.create();
        persistedAccount.setAdmin(TRUE);
        persistedAccount.setEmail(EMAIL);
        persistedAccount.setPhone(PHONE);
        persistedAccount.setEmailVerified(TRUE);
        persistedAccount.setPhoneVerified(TRUE);

        when(mockAccountDao.getAccount(any())).thenReturn(Optional.of(persistedAccount));

        Account account = Account.create();
        account.setEmail(EMAIL);
        account.setPhone(PHONE);
        account.setId(TEST_USER_ID);
        account.setEmailVerified(FALSE); // ignored
        account.setPhoneVerified(FALSE); // ignored
        
        Account retValue = service.updateAccount(TEST_APP_ID, account);
        
        assertEquals(retValue.getEmailVerified(), TRUE);
        assertEquals(retValue.getPhoneVerified(), TRUE);
        assertEquals(retValue.getEmail(), EMAIL);
        assertEquals(retValue.getPhone(), PHONE);
        
        verify(mockAccountDao).updateAccount(account);
        verify(mockAccountWorkflowService, never()).sendEmailVerificationToken(any(), any(), any());
        verify(mockSmsService, never()).optInPhoneNumber(any(), any());
        verify(mockAccountWorkflowService, never()).sendPhoneVerificationToken(any(), any(), any());
    }
    
    @Test
    public void updateAccount_removesEmailAndPhone() {
        App app = App.create();
        when(mockAppService.getApp(TEST_APP_ID)).thenReturn(app);
        
        Account persistedAccount = Account.create();
        persistedAccount.setAdmin(TRUE);
        persistedAccount.setEmail(EMAIL);
        persistedAccount.setPhone(PHONE);
        persistedAccount.setEmailVerified(TRUE);
        persistedAccount.setPhoneVerified(TRUE);

        when(mockAccountDao.getAccount(any())).thenReturn(Optional.of(persistedAccount));

        Account account = Account.create();
        account.setId(TEST_USER_ID);
        account.setSynapseUserId("12345");
        
        Account retValue = service.updateAccount(TEST_APP_ID, account);
        
        assertEquals(retValue.getEmailVerified(), FALSE);
        assertEquals(retValue.getPhoneVerified(), FALSE);
        assertNull(retValue.getEmail());
        assertNull(retValue.getPhone());
        
        verify(mockAccountDao).updateAccount(account);
        verify(mockAccountWorkflowService, never()).sendEmailVerificationToken(any(), any(), any());
        verify(mockSmsService, never()).optInPhoneNumber(any(), any());
        verify(mockAccountWorkflowService, never()).sendPhoneVerificationToken(any(), any(), any());
    }
    
    @Test(expectedExceptions = EntityNotFoundException.class)
    public void updateAccount_cannotEditNonAdminUser() {
        App app = App.create();
        app.setUserProfileAttributes(ImmutableSet.of("a"));
        when(mockAppService.getApp(TEST_APP_ID)).thenReturn(app);
        
        Account persistedAccount = Account.create();
        persistedAccount.setAdmin(FALSE);

        when(mockAccountDao.getAccount(any())).thenReturn(Optional.of(persistedAccount));

        Account account = Account.create();
        account.setId(TEST_USER_ID);
        account.setAdmin(TRUE); // a lie
        
        service.updateAccount(TEST_APP_ID, account);
    }
    
    @Test(expectedExceptions = EntityNotFoundException.class, 
            expectedExceptionsMessageRegExp = "App not found.")
    public void updateAccount_appNotFound() {
        service.updateAccount(TEST_APP_ID, Account.create());
    }
    
    @Test(expectedExceptions = EntityNotFoundException.class, 
            expectedExceptionsMessageRegExp = "Account not found.")
    public void updateAccount_accountIdNull() {
        App app = App.create();
        when(mockAppService.getApp(TEST_APP_ID)).thenReturn(app);

        service.updateAccount(TEST_APP_ID, Account.create());
    }
    
    @Test(expectedExceptions = EntityNotFoundException.class, 
            expectedExceptionsMessageRegExp = "Account not found.")
    public void updateAccount_accountNotFound() {
        App app = App.create();
        when(mockAppService.getApp(TEST_APP_ID)).thenReturn(app);
        
        Account account = Account.create();
        account.setEmail(EMAIL);
        account.setId(TEST_USER_ID);
        
        when(mockAccountDao.getAccount(any())).thenReturn(Optional.empty());

        service.updateAccount(TEST_APP_ID, account);
    }

    @Test
    public void updateAccount_doNotSendVerificationMessages() {
        // This update does not change identifiers, so no updates are sent.
        
        App app = App.create();
        app.setUserProfileAttributes(ImmutableSet.of());
        when(mockAppService.getApp(TEST_APP_ID)).thenReturn(app);
        
        Account persistedAccount = Account.create();
        persistedAccount.setAdmin(TRUE);
        persistedAccount.setAppId(TEST_APP_ID);
        persistedAccount.setId(TEST_USER_ID);
        persistedAccount.setEmail(EMAIL);
        persistedAccount.setPhone(PHONE);
        persistedAccount.setEmailVerified(TRUE);
        persistedAccount.setPhoneVerified(TRUE);

        when(mockAccountDao.getAccount(any())).thenReturn(Optional.of(persistedAccount));

        Account account = Account.create();
        account.setId(TEST_USER_ID);
        account.setAppId(TEST_APP_ID);
        account.setEmail(EMAIL);
        account.setPhone(PHONE);
        account.setEmailVerified(FALSE); // ignored
        account.setPhoneVerified(FALSE); // ignored
        
        Account retValue = service.updateAccount(TEST_APP_ID, account);
        assertEquals(retValue.getEmailVerified(), TRUE);
        assertEquals(retValue.getPhoneVerified(), TRUE);
        
        verify(mockAccountDao).updateAccount(account);
        verify(mockAccountWorkflowService, never()).sendEmailVerificationToken(any(), any(), any());
        verify(mockSmsService, never()).optInPhoneNumber(any(), any());
        verify(mockAccountWorkflowService, never()).sendPhoneVerificationToken(any(), any(), any());
    }
    
    
    @Test
    public void updateAccount_nonAdminCannotChangeStatus() {
        App app = App.create();
        app.setUserProfileAttributes(ImmutableSet.of("a"));
        when(mockAppService.getApp(TEST_APP_ID)).thenReturn(app);
        
        Account persistedAccount = Account.create();
        persistedAccount.setAdmin(TRUE);
        persistedAccount.setStatus(AccountStatus.DISABLED);

        when(mockAccountDao.getAccount(any())).thenReturn(Optional.of(persistedAccount));

        Account account = Account.create();
        account.setId(TEST_USER_ID);
        account.setEmail(EMAIL);
        account.setStatus(AccountStatus.ENABLED);
        
        Account retValue = service.updateAccount(TEST_APP_ID, account);
        
        assertEquals(retValue.getStatus(), DISABLED);        
    }

    @Test
    public void updateAccount_adminCanChangeStatus() {
        RequestContext.set(new RequestContext.Builder().withCallerRoles(ImmutableSet.of(ADMIN)).build());
        App app = App.create();
        app.setUserProfileAttributes(ImmutableSet.of("a"));
        when(mockAppService.getApp(TEST_APP_ID)).thenReturn(app);
        
        Account persistedAccount = Account.create();
        persistedAccount.setAdmin(TRUE);
        persistedAccount.setEmail(EMAIL);
        persistedAccount.setEmailVerified(TRUE);
        persistedAccount.setStatus(AccountStatus.DISABLED);

        when(mockAccountDao.getAccount(any())).thenReturn(Optional.of(persistedAccount));

        Account account = Account.create();
        account.setId(TEST_USER_ID);
        account.setEmail(EMAIL);
        account.setEmailVerified(TRUE);
        account.setStatus(AccountStatus.ENABLED);
        
        Account retValue = service.updateAccount(TEST_APP_ID, account);
        
        assertEquals(retValue.getStatus(), ENABLED);
    }

    @Test
    public void updateAccount_workerCanChangeStatus() {
        RequestContext.set(new RequestContext.Builder().withCallerRoles(ImmutableSet.of(WORKER)).build());
        App app = App.create();
        app.setUserProfileAttributes(ImmutableSet.of("a"));
        when(mockAppService.getApp(TEST_APP_ID)).thenReturn(app);
        
        Account persistedAccount = Account.create();
        persistedAccount.setAdmin(TRUE);
        persistedAccount.setEmail(EMAIL);
        persistedAccount.setEmailVerified(TRUE);
        persistedAccount.setStatus(AccountStatus.DISABLED);

        when(mockAccountDao.getAccount(any())).thenReturn(Optional.of(persistedAccount));

        Account account = Account.create();
        account.setId(TEST_USER_ID);
        account.setEmail(EMAIL);
        account.setEmailVerified(TRUE);
        account.setStatus(AccountStatus.ENABLED);
        
        Account retValue = service.updateAccount(TEST_APP_ID, account);
        
        assertEquals(retValue.getStatus(), ENABLED);
    }
    
    @Test
    public void updateAccount_changeToSynapseUserIdSignsOutUser() {
        App app = App.create();
        when(mockAppService.getApp(TEST_APP_ID)).thenReturn(app);
        
        Account persistedAccount = Account.create();
        persistedAccount.setAdmin(TRUE);
        persistedAccount.setId(TEST_USER_ID);
        persistedAccount.setAppId(TEST_APP_ID);
        persistedAccount.setSynapseUserId(SYNAPSE_USER_ID);

        when(mockAccountDao.getAccount(any())).thenReturn(Optional.of(persistedAccount));

        Account account = Account.create();
        account.setId(TEST_USER_ID);
        account.setSynapseUserId("67890");
        
        Account retValue = service.updateAccount(TEST_APP_ID, account);
        assertEquals(retValue.getSynapseUserId(), "67890");
        
        verify(mockCacheProvider).removeSessionByUserId(TEST_USER_ID);
    }
    
    @Test
    public void updateAccount_nullSynapseUserId() {
        App app = App.create();
        when(mockAppService.getApp(TEST_APP_ID)).thenReturn(app);
        
        Account persistedAccount = Account.create();
        persistedAccount.setEmail(EMAIL);
        persistedAccount.setAdmin(TRUE);
        persistedAccount.setId(TEST_USER_ID);
        persistedAccount.setAppId(TEST_APP_ID);

        when(mockAccountDao.getAccount(any())).thenReturn(Optional.of(persistedAccount));

        Account account = Account.create();
        account.setId(TEST_USER_ID);
        account.setEmail(EMAIL);
        
        Account retValue = service.updateAccount(TEST_APP_ID, account);
        assertNull(retValue.getSynapseUserId());
        
        verify(mockCacheProvider, never()).removeSessionByUserId(TEST_USER_ID);
    }
    
    @Test
    public void updateAccount_noChangeToSynapseUserId() {
        App app = App.create();
        when(mockAppService.getApp(TEST_APP_ID)).thenReturn(app);
        
        Account persistedAccount = Account.create();
        persistedAccount.setAdmin(TRUE);
        persistedAccount.setId(TEST_USER_ID);
        persistedAccount.setAppId(TEST_APP_ID);
        persistedAccount.setSynapseUserId(SYNAPSE_USER_ID);

        when(mockAccountDao.getAccount(any())).thenReturn(Optional.of(persistedAccount));

        Account account = Account.create();
        account.setId(TEST_USER_ID);
        account.setSynapseUserId(SYNAPSE_USER_ID);
        
        Account retValue = service.updateAccount(TEST_APP_ID, account);
        assertEquals(retValue.getSynapseUserId(), SYNAPSE_USER_ID);
        
        verify(mockCacheProvider, never()).removeSessionByUserId(TEST_USER_ID);
    }
}
