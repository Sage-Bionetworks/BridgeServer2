package org.sagebionetworks.bridge;

import static org.sagebionetworks.bridge.BridgeConstants.API_APP_ID;
import static org.sagebionetworks.bridge.RequestContext.NULL_INSTANCE;
import static org.sagebionetworks.bridge.Roles.ADMIN;
import static org.sagebionetworks.bridge.Roles.DEVELOPER;
import static org.sagebionetworks.bridge.Roles.RESEARCHER;
import static org.sagebionetworks.bridge.Roles.SUPERADMIN;
import static org.sagebionetworks.bridge.Roles.WORKER;
import static org.sagebionetworks.bridge.TestConstants.LANGUAGES;
import static org.sagebionetworks.bridge.TestConstants.TEST_STUDY;
import static org.sagebionetworks.bridge.TestConstants.USER_ID;
import static org.sagebionetworks.bridge.models.ClientInfo.UNKNOWN_CLIENT;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertNotNull;
import static org.testng.Assert.assertNull;
import static org.testng.Assert.assertTrue;

import java.util.Set;

import org.testng.annotations.Test;

import org.sagebionetworks.bridge.models.ClientInfo;
import org.sagebionetworks.bridge.models.Metrics;

import com.fasterxml.jackson.databind.node.ObjectNode;
import com.google.common.collect.ImmutableSet;

public class RequestContextTest {

    private static final String REQUEST_ID = "requestId";
    private static final Set<String> SUBSTUDIES = ImmutableSet.of("testA", "testB");
    private static final Set<Roles> ROLES = ImmutableSet.of(DEVELOPER, WORKER);

    @Test
    public void builderIsNullSafe() { 
        // Verify that minimal construction of a context works and returns a valid, if 
        // empty and useless, request context. It's expected this will be augmented by other
        // code that executes.
        RequestContext nullContext = new RequestContext.Builder().withRequestId(null).withCallerStudyId(null)
                .withCallerSubstudies(null).withCallerRoles(null).withCallerUserId(null)
                .withCallerLanguages(null).withCallerClientInfo(null).build();
        
        assertNotNull(nullContext.getId());
        assertTrue(nullContext.getCallerSubstudies().isEmpty());
        assertTrue(nullContext.getCallerRoles().isEmpty());
        assertNull(nullContext.getCallerStudyId());
        assertNull(nullContext.getCallerStudyIdentifier());
        assertNull(nullContext.getCallerUserId());
        assertNotNull(nullContext.getMetrics());
        assertTrue(nullContext.getCallerLanguages().isEmpty());
        assertEquals(nullContext.getCallerClientInfo(), UNKNOWN_CLIENT);
        
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
        assertTrue(NULL_INSTANCE.getCallerSubstudies().isEmpty());
        assertTrue(NULL_INSTANCE.getCallerRoles().isEmpty());
        assertNull(NULL_INSTANCE.getCallerStudyId());
        assertNull(NULL_INSTANCE.getCallerStudyIdentifier());
        assertNull(NULL_INSTANCE.getCallerUserId());
        assertNull(NULL_INSTANCE.getMetrics());
        assertTrue(NULL_INSTANCE.getCallerLanguages().isEmpty());
        assertEquals(NULL_INSTANCE.getCallerClientInfo(), UNKNOWN_CLIENT);
    }

    @Test
    public void test() {
        // An existing metrics object
        Metrics metrics = new Metrics(REQUEST_ID);
        
        ClientInfo clientInfo = ClientInfo.fromUserAgentCache("Asthma/26 (Unknown iPhone; iPhone OS/9.1) BridgeSDK/4");
        
        RequestContext context = new RequestContext.Builder().withRequestId(REQUEST_ID).withCallerStudyId(TEST_STUDY)
                .withCallerSubstudies(SUBSTUDIES).withMetrics(metrics).withCallerRoles(ROLES).withCallerUserId(USER_ID)
                .withCallerLanguages(LANGUAGES).withCallerClientInfo(clientInfo).build();

        assertEquals(context.getId(), REQUEST_ID);
        assertEquals(context.getCallerStudyId(), API_APP_ID);
        assertEquals(context.getCallerStudyIdentifier(), TEST_STUDY);
        assertEquals(context.getCallerSubstudies(), SUBSTUDIES);
        assertEquals(context.getCallerRoles(), ROLES);
        assertEquals(context.getCallerUserId(), USER_ID);
        assertEquals(context.getCallerLanguages(), LANGUAGES);
        assertEquals(context.getCallerClientInfo(), clientInfo);
        assertEquals(context.getMetrics(), metrics);
    }
    
    @Test
    public void toBuilder() { 
        // An existing metrics object
        Metrics metrics = new Metrics(REQUEST_ID);
        
        ClientInfo clientInfo = ClientInfo.fromUserAgentCache("Asthma/26 (Unknown iPhone; iPhone OS/9.1) BridgeSDK/4");
        
        RequestContext context = new RequestContext.Builder().withRequestId(REQUEST_ID).withCallerStudyId(TEST_STUDY)
                .withCallerSubstudies(SUBSTUDIES).withMetrics(metrics).withCallerRoles(ROLES).withCallerUserId(USER_ID)
                .withCallerLanguages(LANGUAGES).withCallerClientInfo(clientInfo).build();        
        
        RequestContext copy = context.toBuilder().withRequestId("did-change-this").build();
        
        assertEquals(copy.getId(), "did-change-this");
        assertEquals(copy.getCallerStudyId(), API_APP_ID);
        assertEquals(copy.getCallerStudyIdentifier(), TEST_STUDY);
        assertEquals(copy.getCallerSubstudies(), SUBSTUDIES);
        assertEquals(copy.getCallerRoles(), ROLES);
        assertEquals(copy.getCallerUserId(), USER_ID);
        assertEquals(copy.getCallerLanguages(), LANGUAGES);
        assertEquals(copy.getCallerClientInfo(), clientInfo);
        assertEquals(copy.getMetrics(), metrics);
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
        assertTrue(context.isInRole(ImmutableSet.of(WORKER)));
        assertTrue(context.isInRole(ImmutableSet.of(DEVELOPER, ADMIN)));
    }
    
    @Test
    public void isInRole() {
        RequestContext context = new RequestContext.Builder().withCallerRoles(ImmutableSet.of(ADMIN)).build();

        assertFalse(context.isInRole(DEVELOPER));
        assertFalse(context.isInRole(RESEARCHER));
        assertTrue(context.isInRole(ADMIN));
        assertFalse(context.isInRole(WORKER));
        assertFalse(context.isInRole(ImmutableSet.of(DEVELOPER)));
        assertFalse(context.isInRole(ImmutableSet.of(RESEARCHER)));
        assertTrue(context.isInRole(ImmutableSet.of(ADMIN)));
        assertFalse(context.isInRole(ImmutableSet.of(WORKER)));
        assertTrue(context.isInRole(ImmutableSet.of(DEVELOPER, ADMIN)));
    }
}
