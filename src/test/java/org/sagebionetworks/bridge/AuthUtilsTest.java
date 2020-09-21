package org.sagebionetworks.bridge;

import static org.sagebionetworks.bridge.BridgeConstants.CALLER_NOT_MEMBER_ERROR;
import static org.sagebionetworks.bridge.RequestContext.NULL_INSTANCE;
import static org.sagebionetworks.bridge.Roles.ADMIN;
import static org.sagebionetworks.bridge.Roles.DEVELOPER;
import static org.sagebionetworks.bridge.Roles.RESEARCHER;
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
import org.sagebionetworks.bridge.services.SponsorService;

public class AuthUtilsTest extends Mockito {
    private static final String SHARED_OWNER_ID = TEST_APP_ID + ":" + OWNER_ID;
    
    @AfterMethod
    public void afterMethod() {
        RequestContext.set(RequestContext.NULL_INSTANCE);
    }
    
    @Test
    public void checkOrgMembershipSucceedsForAdmin() {
        RequestContext.set(new RequestContext.Builder()
                .withCallerRoles(ImmutableSet.of(ADMIN)).build());
        
        assertTrue( AuthUtils.checkOrgMembership(TEST_ORG_ID) );
    }
    
    @Test
    public void checkOrgMembershipSucceedsForMatchingOrgId() {
        RequestContext.set(new RequestContext.Builder()
                .withCallerOrgMembership(TEST_ORG_ID).build());
        
        assertTrue( AuthUtils.checkOrgMembership(TEST_ORG_ID) );
    }
    
    @Test
    public void checkOrgMembershipFailsOnMismatch() {
        RequestContext.set(new RequestContext.Builder()
                .withCallerOrgMembership("another-organization").build());
        
        assertFalse( AuthUtils.checkOrgMembership(TEST_ORG_ID) );
    }
    
    @Test
    public void checkOrgMembershipFailsOnNullOrg() {
        RequestContext.set(new RequestContext.Builder().build());
        
        assertFalse( AuthUtils.checkOrgMembership(TEST_ORG_ID) );
    }
    
    @Test
    public void checkOrgMembershipAndThrowSucceeds() {
        RequestContext.set(new RequestContext.Builder()
                .withCallerOrgMembership(TEST_ORG_ID).build());
        
        AuthUtils.checkOrgMembershipAndThrow(TEST_ORG_ID);
    }

    @Test(expectedExceptions = UnauthorizedException.class)
    public void checkOrgMembershipAndThrowFails() {
        RequestContext.set(new RequestContext.Builder().build());
        
        AuthUtils.checkOrgMembershipAndThrow(TEST_ORG_ID);
    }
    
    @Test
    public void checkOwnershipAdminUser() {
        RequestContext.set(new RequestContext.Builder()
                .withCallerRoles(ImmutableSet.of(Roles.ADMIN)).build());
        AuthUtils.checkAssessmentOwnership(TEST_APP_ID, OWNER_ID);
    }
    
    @Test
    public void checkOwnershipUserInOrg() {
        RequestContext.set(new RequestContext.Builder()
                .withCallerOrgMembership(OWNER_ID).build());
        AuthUtils.checkAssessmentOwnership(TEST_APP_ID, OWNER_ID);
    }
    
    @Test(expectedExceptions = UnauthorizedException.class,
            expectedExceptionsMessageRegExp = CALLER_NOT_MEMBER_ERROR)
    public void checkOwnershipScopedUserOrgIdIsMissing() {
        RequestContext.set(new RequestContext.Builder()
                .withCallerOrgMembership("notValidOwner").build());
        AuthUtils.checkAssessmentOwnership(TEST_APP_ID, OWNER_ID);
    }
    
    @Test
    public void checkSharedOwnershipAdminUser() {
        RequestContext.set(new RequestContext.Builder()
                .withCallerRoles(ImmutableSet.of(ADMIN)).build());
        AuthUtils.checkSharedAssessmentOwnership(TEST_APP_ID, GUID, SHARED_OWNER_ID);
    }
    
    @Test(expectedExceptions = UnauthorizedException.class,
            expectedExceptionsMessageRegExp = CALLER_NOT_MEMBER_ERROR)
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
        
        assertTrue(AuthUtils.checkSelfOrResearcher(USER_ID));
    }
    
    @Test
    public void checkSelfOrResearcherSucceedsBecauseResearcher() {
        RequestContext.set(new RequestContext.Builder()
                .withCallerRoles(ImmutableSet.of(RESEARCHER))
                .withCallerUserId("notUserId").build());
        
        assertTrue(AuthUtils.checkSelfOrResearcher(USER_ID));
    }
    
    @Test
    public void checkSelfOrResearcherFails() {
        RequestContext.set(new RequestContext.Builder()
                .withCallerRoles(ImmutableSet.of(DEVELOPER))
                .withCallerUserId("notUserId").build());
        
        assertFalse(AuthUtils.checkSelfOrResearcher(USER_ID));
    }
    
    @Test(expectedExceptions = UnauthorizedException.class)
    public void checkSelfOrResearcherAndThrow() { 
        RequestContext.set(new RequestContext.Builder()
                .withCallerRoles(ImmutableSet.of(DEVELOPER))
                .withCallerUserId("notUserId").build());
        
        AuthUtils.checkSelfOrResearcherAndThrow(USER_ID);
    }
    
    @Test
    public void checkSelfAdminOrSponsorFails() {
        RequestContext.set(new RequestContext.Builder()
                .withCallerUserId("adminUser")
                .withCallerRoles(ImmutableSet.of(DEVELOPER))
                .withCallerOrgMembership(TEST_ORG_ID).build());
        
        SponsorService mockSponsorService = mock(SponsorService.class);
        when(mockSponsorService.isStudySponsoredBy(TEST_STUDY_ID, TEST_ORG_ID)).thenReturn(Boolean.FALSE);

        assertFalse(AuthUtils.checkSelfAdminOrSponsor(mockSponsorService, TEST_STUDY_ID, null));
    }
    
    @Test
    public void checkSelfAdminOrSponsorForAdminSucceeds() {
        RequestContext.set(new RequestContext.Builder()
                .withCallerRoles(ImmutableSet.of(ADMIN))
                .withCallerOrgMembership(TEST_ORG_ID).build());
        
        SponsorService mockSponsorService = mock(SponsorService.class);
        when(mockSponsorService.isStudySponsoredBy(TEST_STUDY_ID, TEST_ORG_ID)).thenReturn(Boolean.FALSE);

        assertTrue(AuthUtils.checkSelfAdminOrSponsor(mockSponsorService, TEST_STUDY_ID, null));
    }
    
    @Test
    public void checkSelfAdminOrSponsorForSponsorSucceeds() {
        RequestContext.set(new RequestContext.Builder()
                .withCallerRoles(ImmutableSet.of(DEVELOPER))
                .withCallerOrgMembership(TEST_ORG_ID).build());
        
        SponsorService mockSponsorService = mock(SponsorService.class);
        when(mockSponsorService.isStudySponsoredBy(TEST_STUDY_ID, TEST_ORG_ID)).thenReturn(Boolean.TRUE);

        assertTrue(AuthUtils.checkSelfAdminOrSponsor(mockSponsorService, TEST_STUDY_ID, null));
    }
    
    @Test
    public void checkSelfAdminOrSponsorForSponsorFails() {
        RequestContext.set(new RequestContext.Builder()
                .withCallerRoles(ImmutableSet.of(DEVELOPER))
                .withCallerOrgMembership(TEST_ORG_ID).build());
        
        SponsorService mockSponsorService = mock(SponsorService.class);
        when(mockSponsorService.isStudySponsoredBy(TEST_STUDY_ID, TEST_ORG_ID)).thenReturn(Boolean.FALSE);

        assertFalse(AuthUtils.checkSelfAdminOrSponsor(mockSponsorService, TEST_STUDY_ID, null));
    }
    
    
    @Test
    public void checkSelfAdminOrSponsorForSelfSucceeds() {
        RequestContext.set(new RequestContext.Builder()
                .withCallerUserId(USER_ID).build());
        
        SponsorService mockSponsorService = mock(SponsorService.class);
        when(mockSponsorService.isStudySponsoredBy(TEST_STUDY_ID, TEST_ORG_ID)).thenReturn(Boolean.TRUE);

        assertTrue(AuthUtils.checkSelfAdminOrSponsor(mockSponsorService, TEST_STUDY_ID, USER_ID));
    }

    @Test
    public void checkSelfAdminOrSponsorAndThrow() {
        RequestContext.set(new RequestContext.Builder()
                .withCallerUserId("adminUser")
                .withCallerRoles(ImmutableSet.of(DEVELOPER))
                .withCallerOrgMembership(TEST_ORG_ID).build());
        
        SponsorService mockSponsorService = mock(SponsorService.class);
        when(mockSponsorService.isStudySponsoredBy(TEST_STUDY_ID, TEST_ORG_ID)).thenReturn(Boolean.TRUE);

        AuthUtils.checkSelfAdminOrSponsorAndThrow(mockSponsorService, TEST_STUDY_ID, null);
    }

    @Test(expectedExceptions = UnauthorizedException.class)
    public void checkSelfAdminOrSponsorAndThrowFails() {
        RequestContext.set(new RequestContext.Builder()
                .withCallerUserId("adminUser")
                .withCallerRoles(ImmutableSet.of(DEVELOPER))
                .withCallerOrgMembership(TEST_ORG_ID).build());
        
        SponsorService mockSponsorService = mock(SponsorService.class);
        when(mockSponsorService.isStudySponsoredBy(TEST_STUDY_ID, TEST_ORG_ID)).thenReturn(Boolean.FALSE);

        AuthUtils.checkSelfAdminOrSponsorAndThrow(mockSponsorService, TEST_STUDY_ID, null);
    }
}
