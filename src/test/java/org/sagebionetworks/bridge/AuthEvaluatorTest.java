package org.sagebionetworks.bridge;

import static org.sagebionetworks.bridge.AuthEvaluatorField.APP_ID;
import static org.sagebionetworks.bridge.AuthEvaluatorField.ORG_ID;
import static org.sagebionetworks.bridge.AuthEvaluatorField.OWNER_ID;
import static org.sagebionetworks.bridge.AuthEvaluatorField.STUDY_ID;
import static org.sagebionetworks.bridge.AuthEvaluatorField.USER_ID;
import static org.sagebionetworks.bridge.RequestContext.NULL_INSTANCE;
import static org.sagebionetworks.bridge.Roles.ADMIN;
import static org.sagebionetworks.bridge.Roles.DEVELOPER;
import static org.sagebionetworks.bridge.Roles.RESEARCHER;
import static org.sagebionetworks.bridge.Roles.STUDY_DESIGNER;
import static org.sagebionetworks.bridge.Roles.SUPERADMIN;
import static org.sagebionetworks.bridge.Roles.WORKER;
import static org.sagebionetworks.bridge.TestConstants.TEST_APP_ID;
import static org.sagebionetworks.bridge.TestConstants.TEST_ORG_ID;
import static org.sagebionetworks.bridge.TestConstants.TEST_USER_ID;
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
    public void hasOnlyRoles() {
        // This doesn't match against the null request context
        AuthEvaluator evaluator = new AuthEvaluator().hasOnlyRoles(DEVELOPER);
        assertFalse(evaluator.check());

        // User is developer and must only be a developer
        RequestContext.set(new RequestContext.Builder()
                .withCallerRoles(ImmutableSet.of(DEVELOPER)).build());
        assertTrue(evaluator.check());
        
        // User is required to be only these roles, and developer still passes 
        evaluator = new AuthEvaluator().hasOnlyRoles(DEVELOPER, RESEARCHER);
        assertTrue(evaluator.check());

        // But throw in something else, and they don't
        RequestContext.set(new RequestContext.Builder()
                .withCallerRoles(ImmutableSet.of(DEVELOPER, STUDY_DESIGNER)).build());
        assertFalse(evaluator.check());

        // Now make them more more than the roles listed
        evaluator = new AuthEvaluator().hasOnlyRoles(DEVELOPER);
        RequestContext.set(new RequestContext.Builder()
                .withCallerRoles(ImmutableSet.of(DEVELOPER, RESEARCHER)).build());
        // so this is false
        assertFalse(evaluator.check());
    }
    
    @Test
    public void canAccessStudy() {
        RequestContext.set(new RequestContext.Builder()
                .withCallerRoles(ImmutableSet.of(DEVELOPER))
                .withOrgSponsoredStudies(ImmutableSet.of("study1", "study2")).build());
        
        AuthEvaluator evaluator = new AuthEvaluator().canAccessStudy();
        
        assertTrue(evaluator.check(STUDY_ID, "study2"));
        assertFalse(evaluator.check());
        assertFalse(evaluator.check(STUDY_ID, "study3"));
        assertFalse(evaluator.check(USER_ID, "user"));
    }
    
    @Test
    public void isEnrolledInStudy() {
        RequestContext.set(new RequestContext.Builder()
                .withCallerEnrolledStudies(ImmutableSet.of("study1", "study2")).build());

        AuthEvaluator evaluator = new AuthEvaluator().isEnrolledInStudy();
        
        assertTrue(evaluator.check(STUDY_ID, "study2"));
        assertFalse(evaluator.check());
        assertFalse(evaluator.check(STUDY_ID, "study3"));
        assertFalse(evaluator.check(USER_ID, "user"));
    }
    
    @Test
    public void hasAnyRole() {
        RequestContext.set(new RequestContext.Builder()
                .withCallerRoles(ImmutableSet.of(DEVELOPER)).build());
        
        AuthEvaluator evaluator = new AuthEvaluator().hasAnyRole(DEVELOPER, RESEARCHER);
        assertTrue(evaluator.check());
        assertTrue(evaluator.check(STUDY_ID, "study3")); // params are just ignored
        
        evaluator = new AuthEvaluator().hasAnyRole(WORKER);
        assertFalse(evaluator.check());
        assertFalse(evaluator.check(STUDY_ID, "study3")); // params are just ignored
    }
    
    @Test
    public void hasAnyRoleIsAnyKindOfAdministrator() {
        RequestContext.set(new RequestContext.Builder()
                .withCallerRoles(ImmutableSet.of(DEVELOPER)).build());
        
        AuthEvaluator evaluator = new AuthEvaluator().hasAnyRole();
        assertTrue(evaluator.check());
        
        RequestContext.set(new RequestContext.Builder()
                .withCallerRoles(ImmutableSet.of()).build());
        assertFalse(evaluator.check());
    }
    
    @Test
    public void hasAnyRoleAlwaysPassesForSuperadmin() {
        RequestContext.set(new RequestContext.Builder()
                .withCallerRoles(ImmutableSet.of(SUPERADMIN)).build());
        
        AuthEvaluator evaluator = new AuthEvaluator().hasAnyRole(RESEARCHER);
        assertTrue(evaluator.check());
    }
    
    @Test
    public void isInApp() {
        RequestContext.set(new RequestContext.Builder()
                .withCallerAppId(TEST_APP_ID).build());
        
        AuthEvaluator evaluator = new AuthEvaluator().isInApp();
        
        assertTrue(evaluator.check(APP_ID, TEST_APP_ID));
        assertFalse(evaluator.check());
        assertFalse(evaluator.check(APP_ID, "some-other-app-id"));
        assertFalse(evaluator.check(USER_ID, TEST_USER_ID));
    }

    @Test
    public void isInOrg() {
        RequestContext.set(new RequestContext.Builder()
                .withCallerOrgMembership(TEST_ORG_ID).build());
        
        AuthEvaluator evaluator = new AuthEvaluator().isInOrg();
        
        assertTrue(evaluator.check(ORG_ID, TEST_ORG_ID));
        assertFalse(evaluator.check());
        assertFalse(evaluator.check(ORG_ID, "some-other-org-id"));
        assertFalse(evaluator.check(USER_ID, TEST_USER_ID));
    }
    
    @Test
    public void isSelf() {
        RequestContext.set(new RequestContext.Builder()
                .withCallerUserId(TEST_USER_ID).build());
        
        AuthEvaluator evaluator = new AuthEvaluator().isSelf();
        assertTrue(evaluator.check(USER_ID, TEST_USER_ID));
        assertFalse(evaluator.check());
        assertFalse(evaluator.check(USER_ID, "another-user-id"));
        assertFalse(evaluator.check(STUDY_ID, TEST_USER_ID));
    }
    
    @Test
    public void isSelfUnauthenticatedCall() { 
        // A non-authenticated call to create an account, where neither
        // the caller nor the account has an ID, should be allowed.
        RequestContext.set(NULL_INSTANCE);
        
        AuthEvaluator evaluator = new AuthEvaluator().isSelf();
        assertTrue(evaluator.check(USER_ID, TEST_USER_ID));
        assertTrue(evaluator.check(USER_ID, null));
        assertTrue(evaluator.check());
    }
    
    @Test
    public void andConditionsEnforced() {
        RequestContext.set(new RequestContext.Builder()
                .withCallerOrgMembership(TEST_ORG_ID)
                .withCallerRoles(ImmutableSet.of(RESEARCHER)).build());

        AuthEvaluator evaluator = new AuthEvaluator().isInOrg().hasAnyRole(RESEARCHER);
        assertTrue(evaluator.check(ORG_ID, TEST_ORG_ID));
        
        RequestContext.set(new RequestContext.Builder()
                .withCallerOrgMembership("another-org")
                .withCallerRoles(ImmutableSet.of(RESEARCHER)).build());
        assertFalse(evaluator.check(ORG_ID, TEST_ORG_ID));
        
        RequestContext.set(new RequestContext.Builder()
                .withCallerOrgMembership(TEST_ORG_ID)
                .withCallerRoles(ImmutableSet.of(DEVELOPER)).build());
        assertFalse(evaluator.check(ORG_ID, TEST_ORG_ID));
    }
    
    @Test
    public void orConditionsEnforced() {
        RequestContext.set(new RequestContext.Builder()
                .withCallerOrgMembership(TEST_ORG_ID)
                .withCallerUserId(TEST_USER_ID).build());

        AuthEvaluator evaluator = new AuthEvaluator().isInOrg().or().isSelf();
        // left side passes
        assertTrue(evaluator.check(ORG_ID, TEST_ORG_ID, USER_ID, "wrong-id"));
        
        // right side passes
        assertTrue(evaluator.check(ORG_ID, "wrong-organization", USER_ID, TEST_USER_ID));
        
        // both sides pass
        assertTrue(evaluator.check(ORG_ID, TEST_ORG_ID, USER_ID, TEST_USER_ID));
        
        // both sides fail
        assertFalse(evaluator.check(ORG_ID, "wrong-organization", USER_ID, "wrong-id"));
    }
    
    @Test
    public void checkPasses() {
        RequestContext.set(new RequestContext.Builder()
                .withCallerRoles(ImmutableSet.of(ADMIN)).build());
        AuthEvaluator evaluator = new AuthEvaluator().hasAnyRole(ADMIN);
        
        assertTrue(evaluator.check());
    }

    @Test
    public void checkFails() {
        RequestContext.set(new RequestContext.Builder()
                .withCallerRoles(ImmutableSet.of(DEVELOPER)).build());
        AuthEvaluator evaluator = new AuthEvaluator().hasAnyRole(ADMIN);
        
        assertFalse(evaluator.check());
    }
    
    @Test
    public void checkAndThrowPasses() {
        RequestContext.set(new RequestContext.Builder()
                .withCallerRoles(ImmutableSet.of(ADMIN)).build());
        AuthEvaluator evaluator = new AuthEvaluator().hasAnyRole(ADMIN);
        
        evaluator.checkAndThrow();
    }

    @Test(expectedExceptions = UnauthorizedException.class)
    public void checkAndThrowFails() {
        RequestContext.set(new RequestContext.Builder()
                .withCallerRoles(ImmutableSet.of(DEVELOPER)).build());
        AuthEvaluator evaluator = new AuthEvaluator().hasAnyRole(ADMIN);
        
        evaluator.checkAndThrow();
    }
    
    @Test
    public void checkAndThrowOneArgPasses() {
        RequestContext.set(new RequestContext.Builder()
                .withCallerOrgMembership(TEST_ORG_ID).build());

        AuthEvaluator evaluator = new AuthEvaluator().isInOrg();
        
        evaluator.checkAndThrow(ORG_ID, TEST_ORG_ID);
    }
    
    @Test
    public void checkAndThrowTwoArgsPasses() {
        RequestContext.set(new RequestContext.Builder()
                .withCallerUserId(TEST_USER_ID)
                .withCallerOrgMembership(TEST_ORG_ID).build());

        AuthEvaluator evaluator = new AuthEvaluator().isInOrg().isSelf();
        
        evaluator.checkAndThrow(ORG_ID, TEST_ORG_ID, USER_ID, TEST_USER_ID);
    }

    @Test(expectedExceptions = UnauthorizedException.class)
    public void checkAndThrowOneArgThrows() {
        RequestContext.set(new RequestContext.Builder()
                .withCallerOrgMembership(TEST_ORG_ID).build());

        AuthEvaluator evaluator = new AuthEvaluator().isInOrg();
        
        evaluator.checkAndThrow(ORG_ID, "foo");
    }

    @Test(expectedExceptions = UnauthorizedException.class)
    public void checkAndThrowTwoArgsThrows() {
        RequestContext.set(new RequestContext.Builder()
                .withCallerUserId(TEST_USER_ID)
                .withCallerOrgMembership(TEST_ORG_ID).build());

        AuthEvaluator evaluator = new AuthEvaluator().isInOrg().isSelf();
        
        evaluator.checkAndThrow(ORG_ID, "foo", USER_ID, "foo");
    }
    
    @Test
    public void isSharedOwner() {
        String ownerId = TEST_APP_ID + ":" + TEST_ORG_ID;
        RequestContext.set(new RequestContext.Builder()
                .withCallerAppId(TEST_APP_ID)
                .withCallerOrgMembership(TEST_ORG_ID).build());
        
        AuthEvaluator evaluator = new AuthEvaluator().isSharedOwner();
        
        assertTrue(evaluator.check(OWNER_ID, ownerId));
        assertFalse(evaluator.check(OWNER_ID, null));
        assertFalse(evaluator.check(OWNER_ID, TEST_APP_ID + ":wrong-value"));
        assertFalse(evaluator.check(OWNER_ID, "wrong-value:" + TEST_ORG_ID));
        assertFalse(evaluator.check(OWNER_ID, TEST_APP_ID));
        assertFalse(evaluator.check(OWNER_ID, TEST_ORG_ID));
        assertFalse(evaluator.check(OWNER_ID, TEST_APP_ID + ":too-many:colons"));
    }
}
