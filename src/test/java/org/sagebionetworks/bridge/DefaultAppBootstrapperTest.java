package org.sagebionetworks.bridge;

import static org.sagebionetworks.bridge.BridgeConstants.API_2_APP_ID;
import static org.sagebionetworks.bridge.BridgeConstants.API_APP_ID;
import static org.sagebionetworks.bridge.BridgeConstants.SHARED_APP_ID;
import static org.sagebionetworks.bridge.BridgeConstants.TEST_USER_GROUP;
import static org.sagebionetworks.bridge.DefaultAppBootstrapper.TEST_DATA_GROUPS;
import static org.sagebionetworks.bridge.DefaultAppBootstrapper.TEST_TASK_IDENTIFIERS;
import static org.sagebionetworks.bridge.Roles.ADMIN;
import static org.sagebionetworks.bridge.Roles.SUPERADMIN;
import static org.sagebionetworks.bridge.TestConstants.EMAIL;
import static org.sagebionetworks.bridge.TestConstants.PASSWORD;
import static org.sagebionetworks.bridge.TestConstants.SYNAPSE_USER_ID;
import static org.sagebionetworks.bridge.models.accounts.SharingScope.NO_SHARING;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertNull;
import static org.testng.Assert.assertTrue;

import java.util.List;
import java.util.Optional;

import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;
import org.sagebionetworks.bridge.config.BridgeConfig;
import org.sagebionetworks.bridge.config.Environment;
import org.sagebionetworks.bridge.dynamodb.AnnotationBasedTableCreator;
import org.sagebionetworks.bridge.dynamodb.DynamoInitializer;
import org.sagebionetworks.bridge.exceptions.EntityNotFoundException;
import org.sagebionetworks.bridge.models.accounts.Account;
import org.sagebionetworks.bridge.models.apps.App;
import org.sagebionetworks.bridge.services.AccountService;
import org.sagebionetworks.bridge.services.AdminAccountService;
import org.sagebionetworks.bridge.services.AppService;
import org.sagebionetworks.bridge.services.OrganizationService;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import com.amazonaws.services.dynamodbv2.model.TableDescription;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;

public class DefaultAppBootstrapperTest extends Mockito {
    
    @Mock
    BridgeConfig mockConfig;
    
    @Mock
    AdminAccountService mockAdminAccountService;
    
    @Mock
    AccountService mockAccountService;
    
    @Mock
    AppService mockAppService;
    
    @Mock
    DynamoInitializer mockDynamoInitializer;
    
    @Mock
    AnnotationBasedTableCreator mockAnnotationBasedTableCreator;
    
    @Mock
    S3Initializer mockS3Initializer;
    
    @Mock
    OrganizationService mockOrgService;

    @Captor
    ArgumentCaptor<App> appCaptor;

    @Captor
    ArgumentCaptor<Account> accountCaptor;
    
    @InjectMocks
    DefaultAppBootstrapper bootstrapper;
    
    @BeforeMethod
    public void before() {
        bootstrapper = null; // it keeps this between tests which breaks one test
        MockitoAnnotations.initMocks(this);
    }
    
    @Test
    public void fullBootstrap_withPassword() {
        when(mockConfig.get("admin.email")).thenReturn(EMAIL);
        when(mockConfig.get("admin.password")).thenReturn(PASSWORD);
        when(mockConfig.get("admin.synapse.user.id")).thenReturn(SYNAPSE_USER_ID);
        when(mockConfig.getEnvironment()).thenReturn(Environment.DEV);
        
        List<TableDescription> tables = ImmutableList.of(); 
        when(mockAnnotationBasedTableCreator.getTables("org.sagebionetworks.bridge.dynamodb")).thenReturn(tables);
        
        when(mockAppService.getApp(any())).thenThrow(new EntityNotFoundException(App.class));
        when(mockAppService.createApp(any())).thenAnswer((args) -> args.getArgument(0));
        
        when(mockAccountService.getAccount(any())).thenReturn(Optional.empty());
        
        // We don't care about the context
        bootstrapper.onApplicationEvent(null);
        
        verify(mockDynamoInitializer).init(tables);
        verify(mockS3Initializer).initBuckets();
        
        verify(mockAppService, times(3)).createApp(appCaptor.capture());
        
        App retApi = appCaptor.getAllValues().get(0);
        assertEquals(retApi.getIdentifier(), API_APP_ID);
        assertFalse(retApi.isReauthenticationEnabled());
        assertEquals(retApi.getMinAgeOfConsent(), 18);
        assertEquals(retApi.getDataGroups(), TEST_DATA_GROUPS);
        assertEquals(retApi.getTaskIdentifiers(), TEST_TASK_IDENTIFIERS);
        assertEquals(retApi.getUserProfileAttributes(), ImmutableSet.of("can_be_recontacted"));
        assertTrue(retApi.isEmailVerificationEnabled());
        assertTrue(retApi.isVerifyChannelOnSignInEnabled());
        
        App retApi2 = appCaptor.getAllValues().get(1);
        assertEquals(retApi2.getIdentifier(), API_2_APP_ID);
        assertFalse(retApi2.isReauthenticationEnabled());
        assertEquals(retApi2.getMinAgeOfConsent(), 0);
        assertTrue(retApi2.getDataGroups().isEmpty());
        assertTrue(retApi2.getTaskIdentifiers().isEmpty());
        assertTrue(retApi2.getUserProfileAttributes().isEmpty());
        assertTrue(retApi2.isEmailVerificationEnabled());
        assertTrue(retApi2.isVerifyChannelOnSignInEnabled());
        
        App retShared = appCaptor.getAllValues().get(2);
        assertEquals(retShared.getIdentifier(), SHARED_APP_ID);
        assertFalse(retShared.isReauthenticationEnabled());
        assertEquals(retShared.getMinAgeOfConsent(), 0);
        assertTrue(retShared.isEmailVerificationEnabled());
        assertTrue(retShared.isVerifyChannelOnSignInEnabled());
        
        verify(mockAdminAccountService).createAccount(eq(API_APP_ID), accountCaptor.capture());
        verify(mockAdminAccountService).createAccount(eq(API_2_APP_ID), accountCaptor.capture());
        verify(mockAdminAccountService).createAccount(eq(SHARED_APP_ID), accountCaptor.capture());
        
        assertEquals(accountCaptor.getAllValues().size(), 3);
        for (Account admin : accountCaptor.getAllValues()) {
            assertEquals(admin.getEmail(), EMAIL);
            assertEquals(admin.getPassword(), PASSWORD);
            assertEquals(admin.getSynapseUserId(), SYNAPSE_USER_ID);
            assertEquals(admin.getDataGroups(), ImmutableSet.of(TEST_USER_GROUP));
            assertEquals(admin.getSharingScope(), NO_SHARING);
            assertEquals(admin.getRoles(), ImmutableSet.of(SUPERADMIN));
        }
    }
    
    // This is still ok and won't break anything, but 
    @Test
    public void fullBootstrap_noPassword() {
        when(mockConfig.get("admin.email")).thenReturn(EMAIL);
        when(mockConfig.get("admin.synapse.user.id")).thenReturn(SYNAPSE_USER_ID);
        when(mockConfig.getEnvironment()).thenReturn(Environment.DEV);
        
        List<TableDescription> tables = ImmutableList.of(); 
        when(mockAnnotationBasedTableCreator.getTables("org.sagebionetworks.bridge.dynamodb")).thenReturn(tables);
        
        when(mockAppService.getApp(any())).thenThrow(new EntityNotFoundException(App.class));
        when(mockAppService.createApp(any())).thenAnswer((args) -> args.getArgument(0));
        
        when(mockAccountService.getAccount(any())).thenReturn(Optional.empty());
        
        // We don't care about the context
        bootstrapper.onApplicationEvent(null);
        
        verify(mockAdminAccountService).createAccount(eq(API_APP_ID), accountCaptor.capture());
        verify(mockAdminAccountService).createAccount(eq(API_2_APP_ID), accountCaptor.capture());
        verify(mockAdminAccountService).createAccount(eq(SHARED_APP_ID), accountCaptor.capture());
        
        assertEquals(accountCaptor.getAllValues().size(), 3);
        for (Account admin : accountCaptor.getAllValues()) {
            assertEquals(admin.getEmail(), EMAIL);
            assertNull(admin.getPassword());
            assertEquals(admin.getSynapseUserId(), SYNAPSE_USER_ID);
            assertEquals(admin.getDataGroups(), ImmutableSet.of(TEST_USER_GROUP));
            assertEquals(admin.getSharingScope(), NO_SHARING);
            assertEquals(admin.getRoles(), ImmutableSet.of(SUPERADMIN));
        }
    }
    
    @Test
    public void bootstrapInProd() {
        when(mockConfig.get("admin.email")).thenReturn(EMAIL);
        when(mockConfig.get("admin.synapse.user.id")).thenReturn(SYNAPSE_USER_ID);
        when(mockConfig.getEnvironment()).thenReturn(Environment.PROD);
        
        List<TableDescription> tables = ImmutableList.of(); 
        when(mockAnnotationBasedTableCreator.getTables("org.sagebionetworks.bridge.dynamodb")).thenReturn(tables);
        
        when(mockAppService.getApp(any())).thenThrow(new EntityNotFoundException(App.class));
        when(mockAppService.createApp(any())).thenAnswer((args) -> args.getArgument(0));
        
        when(mockAccountService.getAccount(any())).thenReturn(Optional.empty());
        
        // We don't care about the context
        bootstrapper.onApplicationEvent(null);
        
        verify(mockAdminAccountService).createAccount(eq(API_APP_ID), accountCaptor.capture());
        verify(mockAdminAccountService).createAccount(eq(API_2_APP_ID), accountCaptor.capture());

        Account apiAdmin = accountCaptor.getAllValues().get(0);
        assertEquals(apiAdmin.getRoles(), ImmutableSet.of(ADMIN));

        Account sharedAdmin = accountCaptor.getAllValues().get(1);
        assertEquals(sharedAdmin.getRoles(), ImmutableSet.of(ADMIN));
    }
    
    @Test
    public void skipExistingItems() {
        when(mockConfig.get("admin.synapse.user.id")).thenReturn(SYNAPSE_USER_ID);
        
        List<TableDescription> tables = ImmutableList.of(); 
        when(mockAnnotationBasedTableCreator.getTables("org.sagebionetworks.bridge.dynamodb")).thenReturn(tables);
        
        when(mockAppService.getApp(any())).thenReturn(App.create());
        when(mockAccountService.getAccount(any())).thenReturn(Optional.of(Account.create()));
        
        // We don't care about the context
        bootstrapper.onApplicationEvent(null);

        verify(mockAppService, never()).createApp(any());
        verify(mockAdminAccountService, never()).createAccount(any(), any());
    }
    
    @Test
    public void skipBootstrapAccountIfNotConfigured() {
        when(mockConfig.getEnvironment()).thenReturn(Environment.DEV);
        
        List<TableDescription> tables = ImmutableList.of(); 
        when(mockAnnotationBasedTableCreator.getTables("org.sagebionetworks.bridge.dynamodb")).thenReturn(tables);
        
        when(mockAppService.getApp(any())).thenThrow(new EntityNotFoundException(App.class));
        when(mockAppService.createApp(any())).thenAnswer((args) -> args.getArgument(0));
        
        // We don't care about the context
        bootstrapper.onApplicationEvent(null);
        
        verify(mockDynamoInitializer).init(tables);
        verify(mockS3Initializer).initBuckets();
        verify(mockAppService, times(3)).createApp(appCaptor.capture());
        verify(mockAdminAccountService, never()).createAccount(any(), any());
    }
}
