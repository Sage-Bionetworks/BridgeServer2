package org.sagebionetworks.bridge;

import static org.sagebionetworks.bridge.RequestContext.NULL_INSTANCE;
import static org.sagebionetworks.bridge.Roles.DEVELOPER;
import static org.sagebionetworks.bridge.Roles.RESEARCHER;
import static org.sagebionetworks.bridge.Roles.SUPERADMIN;
import static org.sagebionetworks.bridge.Roles.WORKER;
import static org.sagebionetworks.bridge.TestConstants.TEST_APP_ID;
import static org.sagebionetworks.bridge.TestConstants.TEST_ORG_ID;
import static org.sagebionetworks.bridge.TestConstants.USER_ID;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertTrue;

import com.google.common.collect.ImmutableSet;

import org.testng.annotations.AfterMethod;
import org.testng.annotations.Test;

import org.sagebionetworks.bridge.exceptions.UnauthorizedException;

public class AuthEvaluatorTest {

    @AfterMethod
    public void afterMethod() {
        RequestContext.set(NULL_INSTANCE);
    }
    
    @Test
    public void canAccessStudy() {
        RequestContext.set(new RequestContext.Builder()
                .withCallerRoles(ImmutableSet.of(DEVELOPER))
                .withOrgSponsoredStudies(ImmutableSet.of("study1", "study2")).build());
        
        AuthEvaluator evaluator = new AuthEvaluator().canAccessStudy();
        
        assertTrue(evaluator.verify("studyId", "study2"));
        assertFalse(evaluator.verify());
        assertFalse(evaluator.verify("studyId", "study3"));
        assertFalse(evaluator.verify("userId", "user"));
    }
    
    // This does need to go away
    @Test
    public void callerConsideredGlobal() {
        AuthEvaluator evaluator = new AuthEvaluator().callerConsideredGlobal();
        assertTrue(evaluator.verify("studyId", "study2"));
    }
    
    @Test
    public void hasAnyRole() {
        RequestContext.set(new RequestContext.Builder()
                .withCallerRoles(ImmutableSet.of(DEVELOPER)).build());
        
        AuthEvaluator evaluator = new AuthEvaluator().hasAnyRole(DEVELOPER, RESEARCHER);
        assertTrue(evaluator.verify());
        assertTrue(evaluator.verify("studyId", "study3")); // params are just ignored
        
        evaluator = new AuthEvaluator().hasAnyRole(WORKER);
        assertFalse(evaluator.verify());
        assertFalse(evaluator.verify("studyId", "study3")); // params are just ignored
    }
    
    @Test
    public void hasAnyRoleIsAnyKindOfAdministrator() {
        RequestContext.set(new RequestContext.Builder()
                .withCallerRoles(ImmutableSet.of(DEVELOPER)).build());
        
        AuthEvaluator evaluator = new AuthEvaluator().hasAnyRole();
        assertTrue(evaluator.verify());
        
        RequestContext.set(new RequestContext.Builder()
                .withCallerRoles(ImmutableSet.of()).build());
        assertFalse(evaluator.verify());
    }
    
    @Test
    public void hasAnyRoleAlwaysPassesForSuperadmin() {
        RequestContext.set(new RequestContext.Builder()
                .withCallerRoles(ImmutableSet.of(SUPERADMIN)).build());
        
        AuthEvaluator evaluator = new AuthEvaluator().hasAnyRole(RESEARCHER);
        assertTrue(evaluator.verify());
    }
    
    @Test
    public void isInApp() {
        RequestContext.set(new RequestContext.Builder()
                .withCallerAppId(TEST_APP_ID).build());
        
        AuthEvaluator evaluator = new AuthEvaluator().isInApp();
        
        assertTrue(evaluator.verify("appId", TEST_APP_ID));
        assertFalse(evaluator.verify());
        assertFalse(evaluator.verify("appId", "some-other-app-id"));
        assertFalse(evaluator.verify("userId", USER_ID));
    }

    @Test
    public void isInOrg() {
        RequestContext.set(new RequestContext.Builder()
                .withCallerOrgMembership(TEST_ORG_ID).build());
        
        AuthEvaluator evaluator = new AuthEvaluator().isInOrg();
        
        assertTrue(evaluator.verify("orgId", TEST_ORG_ID));
        assertFalse(evaluator.verify());
        assertFalse(evaluator.verify("orgId", "some-other-org-id"));
        assertFalse(evaluator.verify("userId", USER_ID));
    }
    
    @Test
    public void isSelf() {
        RequestContext.set(new RequestContext.Builder()
                .withCallerUserId(USER_ID).build());
        
        AuthEvaluator evaluator = new AuthEvaluator().isSelf();
        assertTrue(evaluator.verify("userId", USER_ID));
        assertFalse(evaluator.verify());
        assertFalse(evaluator.verify("userId", "another-user-id"));
        assertFalse(evaluator.verify("studyId", USER_ID));
    }
    
    @Test
    public void andConditionsEnforced() {
        RequestContext.set(new RequestContext.Builder()
                .withCallerOrgMembership(TEST_ORG_ID)
                .withCallerRoles(ImmutableSet.of(RESEARCHER)).build());

        AuthEvaluator evaluator = new AuthEvaluator().isInOrg().hasAnyRole(RESEARCHER);
        assertTrue(evaluator.verify("orgId", TEST_ORG_ID));
        
        RequestContext.set(new RequestContext.Builder()
                .withCallerOrgMembership("another-org")
                .withCallerRoles(ImmutableSet.of(RESEARCHER)).build());
        assertFalse(evaluator.verify("orgId", TEST_ORG_ID));
        
        RequestContext.set(new RequestContext.Builder()
                .withCallerOrgMembership(TEST_ORG_ID)
                .withCallerRoles(ImmutableSet.of(DEVELOPER)).build());
        assertFalse(evaluator.verify("orgId", TEST_ORG_ID));
    }
    
    @Test
    public void orConditionsEnforced() {
        RequestContext.set(new RequestContext.Builder()
                .withCallerOrgMembership(TEST_ORG_ID)
                .withCallerUserId(USER_ID).build());

        AuthEvaluator evaluator = new AuthEvaluator().isInOrg().or().isSelf();
        // left side passes
        assertTrue(evaluator.verify("orgId", TEST_ORG_ID, "userId", "wrong-id"));
        
        // right side passes
        assertTrue(evaluator.verify("orgId", "wrong-organization", "userId", USER_ID));
        
        // both sides pass
        assertTrue(evaluator.verify("orgId", TEST_ORG_ID, "userId", USER_ID));
        
        // both sides fail
        assertFalse(evaluator.verify("orgId", "wrong-organization", "userId", "wrong-id"));
    }

    @Test
    public void checkAndThrowOneArgPasses() {
        RequestContext.set(new RequestContext.Builder()
                .withCallerOrgMembership(TEST_ORG_ID).build());

        AuthEvaluator evaluator = new AuthEvaluator().isInOrg();
        
        evaluator.checkOrgId(TEST_ORG_ID);
    }
    
    @Test
    public void checkAndThrowTwoArgsPasses() {
        RequestContext.set(new RequestContext.Builder()
                .withCallerUserId(USER_ID)
                .withCallerOrgMembership(TEST_ORG_ID).build());

        AuthEvaluator evaluator = new AuthEvaluator().isInOrg().isSelf();
        
        evaluator.checkOrgAndUserIds(TEST_ORG_ID, USER_ID);
    }

    @Test(expectedExceptions = UnauthorizedException.class)
    public void checkAndThrowOneArgThrows() {
        RequestContext.set(new RequestContext.Builder()
                .withCallerOrgMembership(TEST_ORG_ID).build());

        AuthEvaluator evaluator = new AuthEvaluator().isInOrg();
        
        evaluator.checkOrgId("foo");
    }

    @Test(expectedExceptions = UnauthorizedException.class)
    public void checkAndThrowTwoArgsThrows() {
        RequestContext.set(new RequestContext.Builder()
                .withCallerUserId(USER_ID)
                .withCallerOrgMembership(TEST_ORG_ID).build());

        AuthEvaluator evaluator = new AuthEvaluator().isInOrg().isSelf();
        
        evaluator.checkOrgAndUserIds("foo", "foo");
    }
}
