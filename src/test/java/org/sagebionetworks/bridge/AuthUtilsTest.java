package org.sagebionetworks.bridge;

import static org.sagebionetworks.bridge.BridgeConstants.CALLER_NOT_MEMBER_ERROR;
import static org.sagebionetworks.bridge.RequestContext.NULL_INSTANCE;
import static org.sagebionetworks.bridge.Roles.DEVELOPER;
import static org.sagebionetworks.bridge.Roles.RESEARCHER;
import static org.sagebionetworks.bridge.Roles.SUPERADMIN;
import static org.sagebionetworks.bridge.TestConstants.GUID;
import static org.sagebionetworks.bridge.TestConstants.OWNER_ID;
import static org.sagebionetworks.bridge.TestConstants.TEST_APP_ID;
import static org.sagebionetworks.bridge.TestConstants.TEST_ORG_ID;
import static org.sagebionetworks.bridge.TestConstants.USER_ID;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertTrue;

import com.google.common.collect.ImmutableSet;

import org.testng.annotations.AfterMethod;
import org.testng.annotations.Test;

import org.sagebionetworks.bridge.exceptions.UnauthorizedException;

public class AuthUtilsTest {
    private static final String SHARED_OWNER_ID = TEST_APP_ID + ":" + OWNER_ID;
    
    @AfterMethod
    public void afterMethod() {
        BridgeUtils.setRequestContext(RequestContext.NULL_INSTANCE);
    }
    
    @Test
    public void checkOrgMembershipSucceedsForSuperadmin() {
        BridgeUtils.setRequestContext(new RequestContext.Builder()
                .withCallerRoles(ImmutableSet.of(SUPERADMIN)).build());
        
        assertTrue( AuthUtils.checkOrgMembership(TEST_ORG_ID) );
    }
    
    @Test
    public void checkOrgMembershipSucceedsForMatchingOrgId() {
        BridgeUtils.setRequestContext(new RequestContext.Builder()
                .withCallerOrgMembership(TEST_ORG_ID).build());
        
        assertTrue( AuthUtils.checkOrgMembership(TEST_ORG_ID) );
    }
    
    @Test
    public void checkOrgMembershipFailsOnMismatch() {
        BridgeUtils.setRequestContext(new RequestContext.Builder()
                .withCallerOrgMembership("another-organization").build());
        
        assertFalse( AuthUtils.checkOrgMembership(TEST_ORG_ID) );
    }
    
    @Test
    public void checkOrgMembershipFailsOnNullOrg() {
        BridgeUtils.setRequestContext(new RequestContext.Builder().build());
        
        assertFalse( AuthUtils.checkOrgMembership(TEST_ORG_ID) );
    }
    
    @Test
    public void checkOrgMembershipAndThrowSucceeds() {
        BridgeUtils.setRequestContext(new RequestContext.Builder()
                .withCallerOrgMembership(TEST_ORG_ID).build());
        
        AuthUtils.checkOrgMembershipAndThrow(TEST_ORG_ID);
    }

    @Test(expectedExceptions = UnauthorizedException.class)
    public void checkOrgMembershipAndThrowFails() {
        BridgeUtils.setRequestContext(new RequestContext.Builder().build());
        
        AuthUtils.checkOrgMembershipAndThrow(TEST_ORG_ID);
    }
    
    @Test
    public void checkOwnershipAdminUser() {
        BridgeUtils.setRequestContext(new RequestContext.Builder()
                .withCallerRoles(ImmutableSet.of(Roles.ADMIN)).build());
        AuthUtils.checkAssessmentOwnership(TEST_APP_ID, OWNER_ID);
    }
    
    @Test
    public void checkOwnershipUserInOrg() {
        BridgeUtils.setRequestContext(new RequestContext.Builder()
                .withCallerOrgMembership(OWNER_ID).build());
        AuthUtils.checkAssessmentOwnership(TEST_APP_ID, OWNER_ID);
    }
    
    @Test(expectedExceptions = UnauthorizedException.class,
            expectedExceptionsMessageRegExp = CALLER_NOT_MEMBER_ERROR)
    public void checkOwnershipScopedUserOrgIdIsMissing() {
        BridgeUtils.setRequestContext(new RequestContext.Builder()
                .withCallerOrgMembership("notValidOwner").build());
        AuthUtils.checkAssessmentOwnership(TEST_APP_ID, OWNER_ID);
    }
    
    @Test
    public void checkSharedOwnershipAdminUser() {
        BridgeUtils.setRequestContext(new RequestContext.Builder()
                .withCallerRoles(ImmutableSet.of(SUPERADMIN)).build());
        AuthUtils.checkSharedAssessmentOwnership(TEST_APP_ID, GUID, SHARED_OWNER_ID);
    }
    
    @Test(expectedExceptions = UnauthorizedException.class,
            expectedExceptionsMessageRegExp = CALLER_NOT_MEMBER_ERROR)
    public void checkSharedOwnershipAgainstNonGlobalOwnerId() {
        BridgeUtils.setRequestContext(NULL_INSTANCE);
        AuthUtils.checkSharedAssessmentOwnership(TEST_APP_ID, GUID, OWNER_ID);
    }
    
    @Test
    public void sharedOwnershipUserInOrder() {
        BridgeUtils.setRequestContext(new RequestContext.Builder()
                .withCallerOrgMembership(OWNER_ID).build());
        AuthUtils.checkSharedAssessmentOwnership(TEST_APP_ID, GUID, SHARED_OWNER_ID);
    }
    
    @Test(expectedExceptions = UnauthorizedException.class,
            expectedExceptionsMessageRegExp = CALLER_NOT_MEMBER_ERROR)
    public void checkSharedOwnershipScopedUserOrgIdIsMissing() {
        BridgeUtils.setRequestContext(new RequestContext.Builder()
                .withCallerOrgMembership("notValidOwner").build());
        AuthUtils.checkSharedAssessmentOwnership(TEST_APP_ID, GUID, SHARED_OWNER_ID);
    }
    
    @Test(expectedExceptions = UnauthorizedException.class,
            expectedExceptionsMessageRegExp = CALLER_NOT_MEMBER_ERROR)
    public void checkSharedOwnershipWrongAppId() { 
        BridgeUtils.setRequestContext(new RequestContext.Builder()
                .withCallerOrgMembership(TEST_APP_ID).build());
        AuthUtils.checkSharedAssessmentOwnership(TEST_APP_ID, GUID, "other:"+OWNER_ID);        
    }
    
    @Test(expectedExceptions = UnauthorizedException.class,
            expectedExceptionsMessageRegExp = CALLER_NOT_MEMBER_ERROR)
    public void checkSharedOwnershipGlobalUserWrongAppId() { 
        BridgeUtils.setRequestContext(NULL_INSTANCE);
        // still doesn't pass because the appId must always match (global users must call 
        // this API after associating to the right app context):
        AuthUtils.checkSharedAssessmentOwnership(TEST_APP_ID, GUID, "other:"+OWNER_ID);        
    }
    
    @Test
    public void checkSelfOrResearcherSucceedsBecauseSelf() {
        BridgeUtils.setRequestContext(new RequestContext.Builder()
                .withCallerUserId(USER_ID).build());
        
        assertTrue(AuthUtils.checkSelfOrResearcher(USER_ID));
    }
    
    @Test
    public void checkSelfOrResearcherSucceedsBecauseResearcher() {
        BridgeUtils.setRequestContext(new RequestContext.Builder()
                .withCallerRoles(ImmutableSet.of(RESEARCHER))
                .withCallerUserId("notUserId").build());
        
        assertTrue(AuthUtils.checkSelfOrResearcher(USER_ID));
    }
    
    @Test
    public void checkSelfOrResearcherFails() {
        BridgeUtils.setRequestContext(new RequestContext.Builder()
                .withCallerRoles(ImmutableSet.of(DEVELOPER))
                .withCallerUserId("notUserId").build());
        
        assertFalse(AuthUtils.checkSelfOrResearcher(USER_ID));
    }
    
    @Test(expectedExceptions = UnauthorizedException.class)
    public void checkSelfOrResearcherAndThrow() { 
        BridgeUtils.setRequestContext(new RequestContext.Builder()
                .withCallerRoles(ImmutableSet.of(DEVELOPER))
                .withCallerUserId("notUserId").build());
        
        AuthUtils.checkSelfOrResearcherAndThrow(USER_ID);
    }
}
