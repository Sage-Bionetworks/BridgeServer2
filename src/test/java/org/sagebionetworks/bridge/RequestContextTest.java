package org.sagebionetworks.bridge;

import static org.sagebionetworks.bridge.RequestContext.NULL_INSTANCE;
import static org.sagebionetworks.bridge.Roles.ADMIN;
import static org.sagebionetworks.bridge.Roles.DEVELOPER;
import static org.sagebionetworks.bridge.Roles.RESEARCHER;
import static org.sagebionetworks.bridge.Roles.SUPERADMIN;
import static org.sagebionetworks.bridge.Roles.WORKER;
import static org.sagebionetworks.bridge.TestConstants.LANGUAGES;
import static org.sagebionetworks.bridge.TestConstants.TEST_APP_ID;
import static org.sagebionetworks.bridge.TestConstants.TEST_ORG_ID;
import static org.sagebionetworks.bridge.TestConstants.TEST_USER_ID;
import static org.sagebionetworks.bridge.TestConstants.USER_STUDY_IDS;
import static org.sagebionetworks.bridge.models.ClientInfo.UNKNOWN_CLIENT;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertNotNull;
import static org.testng.Assert.assertNull;
import static org.testng.Assert.assertSame;
import static org.testng.Assert.assertTrue;

import java.util.Set;

import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import org.sagebionetworks.bridge.models.ClientInfo;
import org.sagebionetworks.bridge.models.Metrics;
import org.sagebionetworks.bridge.models.accounts.StudyParticipant;
import org.sagebionetworks.bridge.models.accounts.UserSession;
import org.sagebionetworks.bridge.services.SponsorService;

import com.fasterxml.jackson.databind.node.ObjectNode;
import com.google.common.collect.ImmutableSet;

public class RequestContextTest extends Mockito {

    private static final String REQUEST_ID = "requestId";
    private static final Set<String> STUDIES = ImmutableSet.of("testA", "testB");
    private static final Set<Roles> ROLES = ImmutableSet.of(DEVELOPER, WORKER);
    
    @Mock
    SponsorService mockSponsorService;
    
    @BeforeMethod
    public void beforeMethod() {
        MockitoAnnotations.initMocks(this);
    }
    
    @AfterMethod
    public void afterMethod() {
        RequestContext.set(NULL_INSTANCE);
    }

    @Test
    public void builderIsNullSafe() { 
        // Verify that minimal construction of a context works and returns a valid, if 
        // empty and useless, request context. It's expected this will be augmented by other
        // code that executes.
        RequestContext nullContext = new RequestContext.Builder().withRequestId(null).withCallerAppId(null)
                .withOrgSponsoredStudies(null).withCallerEnrolledStudies(null).withCallerRoles(null).withCallerUserId(null)
                .withCallerLanguages(null).withCallerClientInfo(null).withCallerOrgMembership(null).build();
        
        assertNotNull(nullContext.getId());
        assertTrue(nullContext.getCallerEnrolledStudies().isEmpty());
        assertTrue(nullContext.getCallerRoles().isEmpty());
        assertNull(nullContext.getCallerAppId());
        assertNull(nullContext.getCallerUserId());
        assertNotNull(nullContext.getMetrics());
        assertTrue(nullContext.getCallerLanguages().isEmpty());
        assertEquals(nullContext.getCallerClientInfo(), UNKNOWN_CLIENT);
        assertNull(nullContext.getCallerOrgMembership());
        assertTrue(nullContext.getOrgSponsoredStudies().isEmpty());
        
        ObjectNode node = nullContext.getMetrics().getJson();
        assertTrue(node.has("request_id"));
        assertTrue(node.has("version"));
        assertTrue(node.has("start"));
    }
    
    @Test
    public void nullInstanceContainsNoValues() {
        // This is truly devoid of all values and represents no value (in production it's used to 
        // clear the ThreadLocal variable in a way that prevents making logs of null-pointer checks
        // in the code).
        assertNull(NULL_INSTANCE.getId());
        assertTrue(NULL_INSTANCE.getCallerEnrolledStudies().isEmpty());
        assertTrue(NULL_INSTANCE.getCallerRoles().isEmpty());
        assertNull(NULL_INSTANCE.getCallerAppId());
        assertNull(NULL_INSTANCE.getCallerUserId());
        assertNull(NULL_INSTANCE.getMetrics());
        assertTrue(NULL_INSTANCE.getCallerLanguages().isEmpty());
        assertNull(NULL_INSTANCE.getCallerOrgMembership());
        assertTrue(NULL_INSTANCE.getOrgSponsoredStudies().isEmpty());
        assertEquals(NULL_INSTANCE.getCallerClientInfo(), UNKNOWN_CLIENT);
    }

    @Test
    public void test() {
        // An existing metrics object
        Metrics metrics = new Metrics(REQUEST_ID);
        
        ClientInfo clientInfo = ClientInfo.fromUserAgentCache("Asthma/26 (Unknown iPhone; iPhone OS/9.1) BridgeSDK/4");
        
        RequestContext context = new RequestContext.Builder().withRequestId(REQUEST_ID).withCallerEnrolledStudies(STUDIES)
                .withCallerAppId(TEST_APP_ID).withMetrics(metrics).withCallerRoles(ROLES).withCallerUserId(TEST_USER_ID)
                .withCallerLanguages(LANGUAGES).withCallerClientInfo(clientInfo).withCallerOrgMembership(TEST_ORG_ID)
                .withOrgSponsoredStudies(USER_STUDY_IDS).build();

        assertEquals(context.getId(), REQUEST_ID);
        assertEquals(context.getCallerAppId(), TEST_APP_ID);
        assertEquals(context.getCallerEnrolledStudies(), STUDIES);
        assertEquals(context.getCallerRoles(), ROLES);
        assertEquals(context.getCallerUserId(), TEST_USER_ID);
        assertEquals(context.getCallerLanguages(), LANGUAGES);
        assertEquals(context.getCallerClientInfo(), clientInfo);
        assertEquals(context.getMetrics(), metrics);
        assertEquals(context.getCallerOrgMembership(), TEST_ORG_ID);
        assertEquals(context.getOrgSponsoredStudies(), USER_STUDY_IDS);
    }
    
    @Test
    public void toBuilder() { 
        // An existing metrics object
        Metrics metrics = new Metrics(REQUEST_ID);
        
        ClientInfo clientInfo = ClientInfo.fromUserAgentCache("Asthma/26 (Unknown iPhone; iPhone OS/9.1) BridgeSDK/4");
        
        RequestContext context = new RequestContext.Builder().withRequestId(REQUEST_ID).withCallerAppId(TEST_APP_ID)
                .withCallerEnrolledStudies(STUDIES).withMetrics(metrics).withCallerRoles(ROLES).withCallerUserId(TEST_USER_ID)
                .withCallerLanguages(LANGUAGES).withCallerClientInfo(clientInfo).withCallerOrgMembership(TEST_ORG_ID)
                .withOrgSponsoredStudies(USER_STUDY_IDS).build();
        
        RequestContext copy = context.toBuilder().withRequestId("did-change-this").build();
        
        assertEquals(copy.getId(), "did-change-this");
        assertEquals(copy.getCallerAppId(), TEST_APP_ID);
        assertEquals(copy.getCallerEnrolledStudies(), STUDIES);
        assertEquals(copy.getCallerRoles(), ROLES);
        assertEquals(copy.getCallerUserId(), TEST_USER_ID);
        assertEquals(copy.getCallerLanguages(), LANGUAGES);
        assertEquals(copy.getCallerClientInfo(), clientInfo);
        assertEquals(copy.getMetrics(), metrics);
        assertEquals(copy.getCallerOrgMembership(), TEST_ORG_ID);
        assertEquals(copy.getOrgSponsoredStudies(), USER_STUDY_IDS);
    }
    
    @Test
    public void isInRoleMethodsAreNullSafe() {
        RequestContext context = new RequestContext.Builder().build();
        
        assertFalse(context.isAdministrator());
        assertFalse(context.isInRole((Roles)null));
        assertFalse(context.isInRole((Set<Roles>)null));
    }
    
    @Test
    public void isAdministratorTrue() {
        RequestContext context = new RequestContext.Builder().withCallerRoles(ImmutableSet.of(DEVELOPER)).build();
        assertTrue(context.isAdministrator());
    }
    
    @Test
    public void isAdministratorFalse() {
        RequestContext context = new RequestContext.Builder().withCallerRoles(ImmutableSet.of()).build();
        assertFalse(context.isAdministrator());   
    }
    
    @Test
    public void isInRoleForSuperadminMatchesEverything() {
        RequestContext context = new RequestContext.Builder().withCallerRoles(ImmutableSet.of(SUPERADMIN)).build();
        
        assertTrue(context.isInRole(DEVELOPER));
        assertTrue(context.isInRole(RESEARCHER));
        assertTrue(context.isInRole(ADMIN));
        assertTrue(context.isInRole(WORKER));
        assertTrue(context.isInRole(ImmutableSet.of(DEVELOPER)));
        assertTrue(context.isInRole(ImmutableSet.of(RESEARCHER)));
        assertTrue(context.isInRole(ImmutableSet.of(ADMIN)));
        assertTrue(context.isInRole(ImmutableSet.of(DEVELOPER, ADMIN)));
        assertTrue(context.isInRole(ImmutableSet.of(WORKER)));
    }
    
    @Test
    public void isInRole() {
        RequestContext context = new RequestContext.Builder().withCallerRoles(ImmutableSet.of(DEVELOPER)).build();

        assertTrue(context.isInRole(DEVELOPER));
        assertFalse(context.isInRole(RESEARCHER));
        assertFalse(context.isInRole(ADMIN));
        assertFalse(context.isInRole(WORKER));
        assertTrue(context.isInRole(ImmutableSet.of(DEVELOPER)));
        assertFalse(context.isInRole(ImmutableSet.of(RESEARCHER)));
        assertFalse(context.isInRole(ImmutableSet.of(ADMIN)));
        assertFalse(context.isInRole(ImmutableSet.of(WORKER)));
        assertTrue(context.isInRole(ImmutableSet.of(DEVELOPER, ADMIN)));
    }
    
    @Test
    public void updateFromSession() {
        RequestContext context = new RequestContext.Builder().withRequestId(REQUEST_ID).build();
        assertNotNull(context.getId());
        assertNull(context.getCallerAppId());
        assertEquals(ImmutableSet.of(), context.getCallerEnrolledStudies());
        assertFalse(context.isAdministrator());
        RequestContext.set(context);
        
        when(mockSponsorService.getSponsoredStudyIds(TEST_APP_ID, TEST_ORG_ID)).thenReturn(USER_STUDY_IDS);
        
        UserSession session = new UserSession(new StudyParticipant.Builder().withStudyIds(USER_STUDY_IDS)
                .withRoles(ImmutableSet.of(DEVELOPER)).withId(TEST_USER_ID).withOrgMembership(TEST_ORG_ID)
                .withLanguages(LANGUAGES).build());
        session.setAuthenticated(true);
        session.setAppId(TEST_APP_ID);
        
        RequestContext retValue = RequestContext.updateFromSession(session, mockSponsorService);
        assertEquals(retValue.getId(), REQUEST_ID);
        assertEquals(retValue.getCallerAppId(), TEST_APP_ID);
        assertEquals(retValue.getCallerEnrolledStudies(), USER_STUDY_IDS);
        assertEquals(retValue.getOrgSponsoredStudies(), USER_STUDY_IDS);
        assertTrue(retValue.isAdministrator());
        assertTrue(retValue.isInRole(DEVELOPER));
        assertEquals(retValue.getCallerUserId(), TEST_USER_ID);
        assertEquals(retValue.getCallerOrgMembership(), TEST_ORG_ID);
        assertEquals(retValue.getCallerLanguages(), LANGUAGES);
        
        RequestContext threadValue = RequestContext.get();
        assertSame(retValue, threadValue);
    }
    
    // Non-admins who have an organizational relationship are given a specific set of studies
    // that they will have to match in some security checks. Verify this is skipped for accounts
    // with no organizational membership.
    @Test
    public void updateFromSessionNoOrgMembership() { 
        when(mockSponsorService.getSponsoredStudyIds(TEST_APP_ID, TEST_ORG_ID)).thenReturn(USER_STUDY_IDS);
        
        UserSession session = new UserSession(new StudyParticipant.Builder().withStudyIds(USER_STUDY_IDS)
                .withRoles(ImmutableSet.of(DEVELOPER)).withId(TEST_USER_ID).withLanguages(LANGUAGES).build());
        session.setAuthenticated(true);
        session.setAppId(TEST_APP_ID);
        
        RequestContext retValue = RequestContext.updateFromSession(session, mockSponsorService);
        assertEquals(retValue.getOrgSponsoredStudies(), ImmutableSet.of());
        
        RequestContext threadValue = RequestContext.get();
        assertEquals(threadValue.getOrgSponsoredStudies(), ImmutableSet.of());
        
        verify(mockSponsorService, never()).getSponsoredStudyIds(any(), any());
    }
    
    // The role no longer changes the studies that are stored in RequestContext...instead we look at the 
    // roles to determine access.
    @Test
    public void updateFromSessionForAdmin() { 
        when(mockSponsorService.getSponsoredStudyIds(TEST_APP_ID, TEST_ORG_ID)).thenReturn(USER_STUDY_IDS);
        
        UserSession session = new UserSession(new StudyParticipant.Builder().withStudyIds(USER_STUDY_IDS)
                .withRoles(ImmutableSet.of(ADMIN)).withOrgMembership(TEST_ORG_ID).withId(TEST_USER_ID)
                .withLanguages(LANGUAGES).build());
        session.setAuthenticated(true);
        session.setAppId(TEST_APP_ID);
        
        RequestContext retValue = RequestContext.updateFromSession(session, mockSponsorService);
        assertEquals(retValue.getOrgSponsoredStudies(), USER_STUDY_IDS);
        
        RequestContext threadValue = RequestContext.get();
        assertEquals(threadValue.getOrgSponsoredStudies(), USER_STUDY_IDS);
    }
    
    @Test
    public void updateFromSessionNullSponsorService() {
        RequestContext context = new RequestContext.Builder().withRequestId(REQUEST_ID).build();
        assertNotNull(context.getId());
        assertNull(context.getCallerAppId());
        assertEquals(ImmutableSet.of(), context.getCallerEnrolledStudies());
        assertFalse(context.isAdministrator());
        RequestContext.set(context);
        
        UserSession session = new UserSession(new StudyParticipant.Builder().withStudyIds(USER_STUDY_IDS)
                .withRoles(ImmutableSet.of(DEVELOPER)).withId(TEST_USER_ID).withOrgMembership(TEST_ORG_ID)
                .withLanguages(LANGUAGES).build());
        session.setAuthenticated(true);
        session.setAppId(TEST_APP_ID);
        
        RequestContext retValue = RequestContext.updateFromSession(session, mockSponsorService);
        assertEquals(retValue.getId(), REQUEST_ID);
        assertEquals(retValue.getCallerAppId(), TEST_APP_ID);
        assertEquals(retValue.getCallerEnrolledStudies(), USER_STUDY_IDS);
        assertEquals(retValue.getOrgSponsoredStudies(), ImmutableSet.of());
        assertTrue(retValue.isAdministrator());
        assertTrue(retValue.isInRole(DEVELOPER));
        assertEquals(retValue.getCallerUserId(), TEST_USER_ID);
        assertEquals(retValue.getCallerOrgMembership(), TEST_ORG_ID);
        assertEquals(retValue.getCallerLanguages(), LANGUAGES);
        
        RequestContext threadValue = RequestContext.get();
        assertSame(retValue, threadValue);
    }
}
