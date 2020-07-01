package org.sagebionetworks.bridge.services;

import static org.mockito.Mockito.any;
import static org.mockito.Mockito.anyBoolean;
import static org.mockito.Mockito.anyString;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.sagebionetworks.bridge.TestConstants.TEST_APP_ID;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertNotNull;
import static org.testng.Assert.fail;

import java.util.Map;
import java.util.Set;

import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import org.sagebionetworks.bridge.BridgeUtils;
import org.sagebionetworks.bridge.RequestContext;
import org.sagebionetworks.bridge.Roles;
import org.sagebionetworks.bridge.TestConstants;
import org.sagebionetworks.bridge.TestUtils;
import org.sagebionetworks.bridge.cache.CacheProvider;
import org.sagebionetworks.bridge.exceptions.ConsentRequiredException;
import org.sagebionetworks.bridge.exceptions.InvalidEntityException;
import org.sagebionetworks.bridge.models.CriteriaContext;
import org.sagebionetworks.bridge.models.accounts.Account;
import org.sagebionetworks.bridge.models.accounts.AccountId;
import org.sagebionetworks.bridge.models.accounts.ConsentStatus;
import org.sagebionetworks.bridge.models.accounts.IdentifierHolder;
import org.sagebionetworks.bridge.models.accounts.SharingScope;
import org.sagebionetworks.bridge.models.accounts.SignIn;
import org.sagebionetworks.bridge.models.accounts.StudyParticipant;
import org.sagebionetworks.bridge.models.accounts.UserSession;
import org.sagebionetworks.bridge.models.apps.App;
import org.sagebionetworks.bridge.models.subpopulations.SubpopulationGuid;
import org.sagebionetworks.bridge.models.substudies.Enrollment;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Maps;

public class UserAdminServiceMockTest {
    
    private static final String USER_ID = "ABC";

    @Mock
    private AuthenticationService authenticationService;

    @Mock
    private NotificationsService notificationsService;

    @Mock
    private ParticipantService participantService;
    
    @Mock
    private ConsentService consentService;
    
    @Mock
    private AccountService accountService;
    
    @Mock
    private Account account;
    
    @Mock
    private UploadService uploadService;
    
    @Mock
    private HealthDataService healthDataService;
    
    @Mock
    private CacheProvider cacheProvider;
    
    @Mock
    private ScheduledActivityService scheduledActivityService;
    
    @Mock
    private ActivityEventService activityEventService;
    
    @Mock
    private ExternalIdService externalIdService;
    
    @Mock
    private RequestInfoService requestInfoService;
    
    @Captor
    private ArgumentCaptor<CriteriaContext> contextCaptor;
    
    @Captor
    private ArgumentCaptor<SignIn> signInCaptor;
    
    @Captor
    private ArgumentCaptor<Account> accountCaptor;

    @InjectMocks
    private UserAdminService service;
    
    private Map<SubpopulationGuid,ConsentStatus> statuses;
    
    @BeforeMethod
    public void before() {
        MockitoAnnotations.initMocks(this);
        
        // Make a user with multiple consent statuses, and just verify that we call the 
        // consent service that many times.
        statuses = Maps.newHashMap();
        addConsentStatus(statuses, "subpop1");
        addConsentStatus(statuses, "subpop2");
        addConsentStatus(statuses, "subpop3");
        
        UserSession session = new UserSession();
        session.setConsentStatuses(statuses);
        
        when(authenticationService.signIn(any(), any(), any())).thenReturn(session);
        
        doReturn(new IdentifierHolder(USER_ID)).when(participantService).createParticipant(any(), any(),
                anyBoolean());
        doReturn(new StudyParticipant.Builder().withId(USER_ID).build()).when(participantService).getParticipant(any(),
                anyString(), anyBoolean());
    }
    
    @AfterMethod
    public void after() {
        BridgeUtils.setRequestContext(RequestContext.NULL_INSTANCE);
    }
    
    private void addConsentStatus(Map<SubpopulationGuid,ConsentStatus> statuses, String guid) {
        SubpopulationGuid subpopGuid = SubpopulationGuid.create(guid);
        ConsentStatus status = new ConsentStatus.Builder().withConsented(false).withGuid(subpopGuid).withName(guid)
                .withRequired(true).build();
        statuses.put(subpopGuid, status);
    }
    
    @Test
    public void creatingUserConsentsToAllRequiredConsents() {
        BridgeUtils.setRequestContext(new RequestContext.Builder().withCallerRoles(
                ImmutableSet.of(Roles.ADMIN)).build());
        
        App app = TestUtils.getValidApp(UserAdminServiceMockTest.class);
        StudyParticipant participant = new StudyParticipant.Builder().withEmail("email@email.com").withPassword("password").build();
        
        Map<SubpopulationGuid,ConsentStatus> statuses = Maps.newHashMap();
        statuses.put(SubpopulationGuid.create("foo1"), TestConstants.REQUIRED_SIGNED_CURRENT);
        statuses.put(SubpopulationGuid.create("foo2"), TestConstants.REQUIRED_SIGNED_OBSOLETE);
        when(consentService.getConsentStatuses(any())).thenReturn(statuses);
        
        service.createUser(app, participant, null, true, true);
        
        verify(participantService).createParticipant(app, participant, false);
        verify(authenticationService).signIn(eq(app), contextCaptor.capture(), signInCaptor.capture());
        
        CriteriaContext context = contextCaptor.getValue();
        assertEquals(context.getAppId(), app.getIdentifier());
        
        verify(consentService).consentToResearch(eq(app), eq(SubpopulationGuid.create("foo1")), any(StudyParticipant.class), any(),
                eq(SharingScope.NO_SHARING), eq(false));
        verify(consentService).consentToResearch(eq(app), eq(SubpopulationGuid.create("foo2")), any(StudyParticipant.class), any(),
                eq(SharingScope.NO_SHARING), eq(false));

        SignIn signIn = signInCaptor.getValue();
        assertEquals(signIn.getEmail(), participant.getEmail());
        assertEquals(signIn.getPassword(), participant.getPassword());
        
        verify(consentService).getConsentStatuses(context);
    }
    
    @Test
    public void creatingUserWithPhone() {
        BridgeUtils.setRequestContext(new RequestContext.Builder().withCallerRoles(
                ImmutableSet.of(Roles.ADMIN)).build());
        
        App app = TestUtils.getValidApp(UserAdminServiceMockTest.class);
        StudyParticipant participant = new StudyParticipant.Builder().withPhone(TestConstants.PHONE)
                .withPassword("password").build();

        service.createUser(app, participant, null, true, true);
        
        verify(participantService).createParticipant(app, participant, false);
        verify(authenticationService).signIn(eq(app), contextCaptor.capture(), signInCaptor.capture());
        
        CriteriaContext context = contextCaptor.getValue();
        assertEquals(context.getAppId(), app.getIdentifier());
        
        SignIn signIn = signInCaptor.getValue();
        assertEquals(signIn.getPhone(), participant.getPhone());
        assertEquals(signIn.getPassword(), participant.getPassword());
        
        verify(consentService).getConsentStatuses(context);
    }
    
    @Test(expectedExceptions = InvalidEntityException.class)
    public void creatingUserWithoutEmailOrPhoneProhibited() {
        App app = TestUtils.getValidApp(UserAdminServiceMockTest.class);
        StudyParticipant participant = new StudyParticipant.Builder().withPassword("password").build();

        service.createUser(app, participant, null, true, true);
    }
    
    @Test
    public void creatingUserWithSubpopulationOnlyConsentsToThatSubpopulation() {
        BridgeUtils.setRequestContext(new RequestContext.Builder().withCallerRoles(
                ImmutableSet.of(Roles.ADMIN)).build());
                
        App app = TestUtils.getValidApp(UserAdminServiceMockTest.class);
        StudyParticipant participant = new StudyParticipant.Builder().withEmail("email@email.com").withPassword("password").build();
        SubpopulationGuid consentedGuid = statuses.keySet().iterator().next();
        
        UserSession session = service.createUser(app, participant, consentedGuid, true, true);
        
        verify(participantService).createParticipant(app, participant, false);
        
        // consented to the indicated subpopulation
        verify(consentService).consentToResearch(eq(app), eq(consentedGuid), any(StudyParticipant.class), any(), eq(SharingScope.NO_SHARING), eq(false));
        // but not to the other two
        for (SubpopulationGuid guid : session.getConsentStatuses().keySet()) {
            if (guid != consentedGuid) {
                verify(consentService, never()).consentToResearch(eq(app), eq(guid), eq(participant), any(), eq(SharingScope.NO_SHARING), eq(false));    
            }
        }
    }
    
    @Test
    public void createUserWithoutConsents() {
        BridgeUtils.setRequestContext(new RequestContext.Builder().withCallerRoles(
                ImmutableSet.of(Roles.ADMIN)).build());
                
        App app = TestUtils.getValidApp(UserAdminServiceMockTest.class);
        StudyParticipant participant = new StudyParticipant.Builder().withEmail("email@email.com").withPassword("password").build();
        SubpopulationGuid consentedGuid = statuses.keySet().iterator().next();

        service.createUser(app, participant, consentedGuid, true, false);
        
        verify(consentService, never()).consentToResearch(any(), any(), any(), any(), any(), anyBoolean());
    }
    
    @Test
    public void createUserWithoutSigningIn() {
        BridgeUtils.setRequestContext(new RequestContext.Builder().withCallerRoles(
                ImmutableSet.of(Roles.ADMIN)).build());
        
        App app = TestUtils.getValidApp(UserAdminServiceMockTest.class);
        StudyParticipant participant = new StudyParticipant.Builder().withEmail("email@email.com").withPassword("password").build();
        
        when(consentService.getConsentStatuses(any())).thenReturn(ImmutableMap.of());
        
        UserSession session = new UserSession(participant);
        when(authenticationService.getSession(eq(app), any())).thenReturn(session);
        
        service.createUser(app, participant, null, false, true);
        
        verify(authenticationService, never()).signIn(any(), any(), any());
        verify(authenticationService).getSession(eq(app), contextCaptor.capture());
        
        CriteriaContext context = contextCaptor.getValue();
        assertEquals(context.getAppId(), app.getIdentifier());
        assertEquals(context.getAccountId().getId(), USER_ID);
    }
    
    @Test
    public void createUserNotConsented() {
        BridgeUtils.setRequestContext(new RequestContext.Builder().withCallerRoles(
                ImmutableSet.of(Roles.ADMIN)).build());
        
        App app = TestUtils.getValidApp(UserAdminServiceMockTest.class);
        StudyParticipant participant = new StudyParticipant.Builder().withEmail("email@email.com").withPassword("password").build();
        
        when(authenticationService.signIn(eq(app), any(), any()))
                .thenThrow(new ConsentRequiredException(new UserSession(participant)));
        
        // specifically do not ask to sign these required consents. Should not throw ConsentRequiredException, 
        // but should return the session from that exception.
        UserSession session = service.createUser(app, participant, null, true, false);
        assertNotNull(session);
    }
    
    @Test
    public void createUserOnRuntimeExceptionCleansUpUser() {
        BridgeUtils.setRequestContext(new RequestContext.Builder().withCallerRoles(
                ImmutableSet.of(Roles.ADMIN)).build());
        
        App app = TestUtils.getValidApp(UserAdminServiceMockTest.class);
        StudyParticipant participant = new StudyParticipant.Builder().withEmail("email@email.com").withPassword("password").build();
        
        Map<SubpopulationGuid,ConsentStatus> statuses = Maps.newHashMap();
        statuses.put(SubpopulationGuid.create("foo1"), TestConstants.REQUIRED_SIGNED_CURRENT);
        statuses.put(SubpopulationGuid.create("foo2"), TestConstants.REQUIRED_SIGNED_OBSOLETE);
        when(consentService.getConsentStatuses(any())).thenReturn(statuses);
        
        AccountId accountId = AccountId.forId(app.getIdentifier(), USER_ID);
        when(accountService.getAccount(accountId)).thenReturn(account);
        
        when(participantService.getParticipant(app, USER_ID, false))
                .thenThrow(new IllegalStateException("System is unable to complete call"));        
        
        try {
            service.createUser(app, participant, null, true, true);    
            fail("Should have thrown exception");
        } catch(IllegalStateException e) {
            verify(accountService).deleteAccount(accountId);
        }
    }
    
    @Test
    public void deleteUser() {
        App app = TestUtils.getValidApp(UserAdminServiceMockTest.class);
        
        AccountId accountId = AccountId.forId(app.getIdentifier(),  "userId");

        Enrollment as1 = Enrollment.create(TEST_APP_ID, "substudyA", "userId");
        as1.setExternalId("subAextId");
        Enrollment as2 = Enrollment.create(TEST_APP_ID, "substudyB", "userId");
        as2.setExternalId("subBextId");
        Set<Enrollment> substudies = ImmutableSet.of(as1, as2);
        
        doReturn("userId").when(account).getId();
        doReturn("healthCode").when(account).getHealthCode();
        doReturn(substudies).when(account).getEnrollments();
        doReturn(account).when(accountService).getAccount(accountId);
        
        service.deleteUser(app, "userId");
        
        // Verify a lot of stuff is deleted or removed
        verify(cacheProvider).removeSessionByUserId("userId");
        verify(requestInfoService).removeRequestInfo("userId");
        verify(healthDataService).deleteRecordsForHealthCode("healthCode");
        verify(notificationsService).deleteAllRegistrations(app.getIdentifier(), "healthCode");
        verify(uploadService).deleteUploadsForHealthCode("healthCode");
        verify(scheduledActivityService).deleteActivitiesForUser("healthCode");
        verify(activityEventService).deleteActivityEvents("healthCode");
        verify(externalIdService).unassignExternalId(accountCaptor.capture(), eq("subAextId"));
        verify(externalIdService).unassignExternalId(accountCaptor.capture(), eq("subBextId"));
        verify(accountService).deleteAccount(accountId);
        
        assertEquals(accountCaptor.getValue().getHealthCode(), "healthCode");
    }
    
    @Test
    public void deleteUserNotFound() {
        App app = TestUtils.getValidApp(UserAdminServiceMockTest.class);
        
        service.deleteUser(app, "userId");
        
        // (it very quietly does nothing)
        verify(cacheProvider, never()).removeSessionByUserId(any());
        verify(requestInfoService, never()).removeRequestInfo(any());
        verify(healthDataService, never()).deleteRecordsForHealthCode(any());
        verify(notificationsService, never()).deleteAllRegistrations(any(), any());
        verify(uploadService, never()).deleteUploadsForHealthCode(any());
        verify(scheduledActivityService, never()).deleteActivitiesForUser(any());
        verify(activityEventService, never()).deleteActivityEvents(any());
        verify(externalIdService, never()).unassignExternalId(any(), any());
        verify(accountService, never()).deleteAccount(any());
    }
}
