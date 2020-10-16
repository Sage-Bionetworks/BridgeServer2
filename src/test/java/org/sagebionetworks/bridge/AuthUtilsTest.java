package org.sagebionetworks.bridge;

import static org.sagebionetworks.bridge.BridgeConstants.CALLER_NOT_MEMBER_ERROR;
import static org.sagebionetworks.bridge.RequestContext.NULL_INSTANCE;
import static org.sagebionetworks.bridge.Roles.ADMIN;
import static org.sagebionetworks.bridge.Roles.DEVELOPER;
import static org.sagebionetworks.bridge.Roles.RESEARCHER;
import static org.sagebionetworks.bridge.Roles.WORKER;
import static org.sagebionetworks.bridge.TestConstants.GUID;
import static org.sagebionetworks.bridge.TestConstants.OWNER_ID;
import static org.sagebionetworks.bridge.TestConstants.TEST_APP_ID;
import static org.sagebionetworks.bridge.TestConstants.TEST_ORG_ID;
import static org.sagebionetworks.bridge.TestConstants.TEST_STUDY_ID;
import static org.sagebionetworks.bridge.TestConstants.USER_ID;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertTrue;

import com.google.common.collect.ImmutableSet;

import org.mockito.Mockito;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.Test;

import org.sagebionetworks.bridge.exceptions.UnauthorizedException;

public class AuthUtilsTest extends Mockito {
    private static final String SHARED_OWNER_ID = TEST_APP_ID + ":" + OWNER_ID;
    
    @AfterMethod
    public void afterMethod() {
        RequestContext.set(RequestContext.NULL_INSTANCE);
    }
    
    @Test
    public void checkSelfStudyResearcherOrAdminSucceedsForSelf() {
        RequestContext.set(new RequestContext.Builder().withCallerUserId(USER_ID).build());
        
        AuthUtils.checkSelfStudyResearcherOrAdmin(USER_ID, TEST_STUDY_ID);
    }
    
    @Test
    public void checkSelfStudyResearcherOrAdminSucceedsForStudyResearcher() {
        RequestContext.set(new RequestContext.Builder()
                .withCallerRoles(ImmutableSet.of(RESEARCHER))
                .withOrgSponsoredStudies(ImmutableSet.of(TEST_STUDY_ID))
                .build());
        
        AuthUtils.checkSelfStudyResearcherOrAdmin(USER_ID, TEST_STUDY_ID);
    }

    @Test
    public void checkSelfStudyResearcherOrAdminSucceedsForAdmin() {
        RequestContext.set(new RequestContext.Builder()
                .withCallerRoles(ImmutableSet.of(ADMIN))
                .build());
        
        AuthUtils.checkSelfStudyResearcherOrAdmin(USER_ID, TEST_STUDY_ID);
    }
    
    @Test(expectedExceptions = UnauthorizedException.class)
    public void checkSelfStudyResearcherOrAdminFailsForNonStudyResearcher() {
        RequestContext.set(new RequestContext.Builder()
                .withCallerRoles(ImmutableSet.of(RESEARCHER))
                .withOrgSponsoredStudies(ImmutableSet.of("someOtherStudy"))
                .build());
        
        AuthUtils.checkSelfStudyResearcherOrAdmin(USER_ID, TEST_STUDY_ID);
    }

    @Test(expectedExceptions = UnauthorizedException.class)
    public void checkSelfStudyResearcherOrAdminFailsForDev() {
        RequestContext.set(new RequestContext.Builder()
                .withCallerRoles(ImmutableSet.of(DEVELOPER))
                .withOrgSponsoredStudies(ImmutableSet.of(TEST_STUDY_ID))
                .build());
        
        AuthUtils.checkSelfStudyResearcherOrAdmin(USER_ID, TEST_STUDY_ID);
    }

    @Test
    public void checkOrgMembershipSucceedsForAdmin() {
        RequestContext.set(new RequestContext.Builder()
                .withCallerRoles(ImmutableSet.of(ADMIN)).build());
        
        AuthUtils.checkOrgMembership(TEST_ORG_ID);
    }
    
    @Test
    public void checkOrgMembershipSucceedsForMatchingOrgId() {
        RequestContext.set(new RequestContext.Builder()
                .withCallerOrgMembership(TEST_ORG_ID).build());
        
        AuthUtils.checkOrgMembership(TEST_ORG_ID);
    }
    
    @Test(expectedExceptions = UnauthorizedException.class)
    public void checkOrgMembershipFailsOnMismatch() {
        RequestContext.set(new RequestContext.Builder()
                .withCallerOrgMembership("another-organization").build());
        
        AuthUtils.checkOrgMembership(TEST_ORG_ID);
    }
    
    @Test(expectedExceptions = UnauthorizedException.class)
    public void checkOrgMembershipFailsOnNullOrg() {
        RequestContext.set(new RequestContext.Builder().build());
        
        AuthUtils.checkOrgMembership(TEST_ORG_ID);
    }
    
    @Test
    public void checkOrgMembershipAndThrowSucceeds() {
        RequestContext.set(new RequestContext.Builder()
                .withCallerOrgMembership(TEST_ORG_ID).build());
        
        AuthUtils.checkOrgMembership(TEST_ORG_ID);
    }

    @Test(expectedExceptions = UnauthorizedException.class)
    public void checkOrgMembershipAndThrowFails() {
        RequestContext.set(new RequestContext.Builder().build());
        
        AuthUtils.checkOrgMembership(TEST_ORG_ID);
    }
    
    @Test
    public void checkSharedOwnershipAdminUser() {
        RequestContext.set(new RequestContext.Builder()
                .withCallerRoles(ImmutableSet.of(ADMIN)).build());
        AuthUtils.checkSharedAssessmentOwnership(TEST_APP_ID, GUID, SHARED_OWNER_ID);
    }
    
    @Test(expectedExceptions = UnauthorizedException.class)
    public void checkSharedOwnershipAgainstNonGlobalOwnerId() {
        RequestContext.set(NULL_INSTANCE);
        AuthUtils.checkSharedAssessmentOwnership(TEST_APP_ID, GUID, OWNER_ID);
    }
    
    @Test
    public void sharedOwnershipUserInOrder() {
        RequestContext.set(new RequestContext.Builder()
                .withCallerOrgMembership(OWNER_ID).build());
        AuthUtils.checkSharedAssessmentOwnership(TEST_APP_ID, GUID, SHARED_OWNER_ID);
    }
    
    @Test(expectedExceptions = UnauthorizedException.class,
            expectedExceptionsMessageRegExp = CALLER_NOT_MEMBER_ERROR)
    public void checkSharedOwnershipScopedUserOrgIdIsMissing() {
        RequestContext.set(new RequestContext.Builder()
                .withCallerOrgMembership("notValidOwner").build());
        AuthUtils.checkSharedAssessmentOwnership(TEST_APP_ID, GUID, SHARED_OWNER_ID);
    }
    
    @Test(expectedExceptions = UnauthorizedException.class,
            expectedExceptionsMessageRegExp = CALLER_NOT_MEMBER_ERROR)
    public void checkSharedOwnershipWrongAppId() { 
        RequestContext.set(new RequestContext.Builder()
                .withCallerOrgMembership(TEST_APP_ID).build());
        AuthUtils.checkSharedAssessmentOwnership(TEST_APP_ID, GUID, "other:"+OWNER_ID);        
    }
    
    @Test(expectedExceptions = UnauthorizedException.class,
            expectedExceptionsMessageRegExp = CALLER_NOT_MEMBER_ERROR)
    public void checkSharedOwnershipGlobalUserWrongAppId() { 
        RequestContext.set(NULL_INSTANCE);
        // still doesn't pass because the appId must always match (global users must call 
        // this API after associating to the right app context):
        AuthUtils.checkSharedAssessmentOwnership(TEST_APP_ID, GUID, "other:"+OWNER_ID);        
    }
    
    @Test
    public void checkSelfOrResearcherSucceedsBecauseSelf() {
        RequestContext.set(new RequestContext.Builder()
                .withCallerUserId(USER_ID).build());
        
        AuthUtils.checkSelfResearcherOrAdmin(USER_ID);
    }
    
    @Test
    public void checkSelfOrResearcherSucceedsBecauseResearcher() {
        RequestContext.set(new RequestContext.Builder()
                .withCallerRoles(ImmutableSet.of(RESEARCHER))
                .withCallerUserId("notUserId").build());
        
        AuthUtils.checkSelfResearcherOrAdmin(USER_ID);
    }
    
    @Test(expectedExceptions = UnauthorizedException.class)
    public void checkSelfOrResearcherFails() {
        RequestContext.set(new RequestContext.Builder()
                .withCallerRoles(ImmutableSet.of(DEVELOPER))
                .withCallerUserId("notUserId").build());
        
        AuthUtils.checkSelfResearcherOrAdmin(USER_ID);
    }
    
    @Test(expectedExceptions = UnauthorizedException.class)
    public void checkSelfOrResearcherAndThrow() { 
        RequestContext.set(new RequestContext.Builder()
                .withCallerRoles(ImmutableSet.of(DEVELOPER))
                .withCallerUserId("notUserId").build());
        
        AuthUtils.checkSelfResearcherOrAdmin(USER_ID);
    }
    
    @Test
    public void isInRoleNullStudyIdAndAdmin() {
        RequestContext.set(new RequestContext.Builder()
                .withCallerRoles(ImmutableSet.of(ADMIN)).build());
        
        assertTrue(AuthUtils.isInRoles(null, ADMIN));
    }
    
    @Test
    public void isInRoleEmptyStudyIds() {
        RequestContext.set(new RequestContext.Builder()
                .withOrgSponsoredStudies(ImmutableSet.of())
                .withCallerRoles(ImmutableSet.of(DEVELOPER)).build());
        
        assertTrue(AuthUtils.isInRoles(TEST_STUDY_ID, DEVELOPER));
    }
    
    @Test
    public void isInRoleMatchingStudyId() {
        RequestContext.set(new RequestContext.Builder()
                .withOrgSponsoredStudies(ImmutableSet.of("A", TEST_STUDY_ID, "B"))
                .withCallerRoles(ImmutableSet.of(DEVELOPER)).build());
        
        assertTrue(AuthUtils.isInRoles(TEST_STUDY_ID, DEVELOPER));
    }
    
    @Test
    public void isInRoleExcludedStudyId() {
        RequestContext.set(new RequestContext.Builder()
                .withOrgSponsoredStudies(ImmutableSet.of("A", "B"))
                .withCallerRoles(ImmutableSet.of(DEVELOPER)).build());
        
        assertFalse(AuthUtils.isInRoles(TEST_STUDY_ID, DEVELOPER));
    }

    @Test
    public void isInRoleExcludedWrongRole() {
        RequestContext.set(new RequestContext.Builder()
                .withCallerRoles(ImmutableSet.of(DEVELOPER)).build());
        
        assertFalse(AuthUtils.isInRoles(null, ADMIN));
    }
    
    @Test
    public void isStudyScopedToCallerFails() {
        RequestContext.set(new RequestContext.Builder()
                .withOrgSponsoredStudies(ImmutableSet.of("study1", "study2")).build());
        
        assertFalse( AuthUtils.isStudyScopedToCaller(TEST_STUDY_ID) );
    }
    
    @Test
    public void isStudyScopedToCallerFailsWithNullStudyId() {
        RequestContext.set(new RequestContext.Builder()
                .withOrgSponsoredStudies(ImmutableSet.of("study1", "study2")).build());
        
        assertFalse( AuthUtils.isStudyScopedToCaller(null) );
    }
    
    @Test
    public void isStudyScopedToCallerSucceedsForWorker() {
        RequestContext.set(new RequestContext.Builder()
                .withCallerRoles(ImmutableSet.of(WORKER))
                .withOrgSponsoredStudies(ImmutableSet.of("study1", "study2")).build());
        
        assertTrue( AuthUtils.isStudyScopedToCaller(TEST_STUDY_ID) );
    }

    @Test
    public void isStudyScopedToCallerSucceedsForAdmin() {
        RequestContext.set(new RequestContext.Builder()
                .withCallerRoles(ImmutableSet.of(ADMIN))
                .withOrgSponsoredStudies(ImmutableSet.of("study1", "study2")).build());
        
        assertTrue( AuthUtils.isStudyScopedToCaller(TEST_STUDY_ID) );
    }
    
    @Test
    public void isStudyScopedToCallerSucceedsForGlobalUser() {
        RequestContext.set(new RequestContext.Builder().build());
        
        assertTrue( AuthUtils.isStudyScopedToCaller(TEST_STUDY_ID) );
    }

    @Test
    public void isStudyScopedToCallerSucceedsForOrgSponsoredStudy() {
        RequestContext.set(new RequestContext.Builder()
                .withCallerRoles(ImmutableSet.of(ADMIN))
                .withOrgSponsoredStudies(ImmutableSet.of("study1", "study2")).build());
        
        assertTrue( AuthUtils.isStudyScopedToCaller("study2") );
    }
}