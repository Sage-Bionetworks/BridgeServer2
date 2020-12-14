package org.sagebionetworks.bridge;

import static org.sagebionetworks.bridge.AuthUtils.IS_ORGADMIN;
import static org.sagebionetworks.bridge.AuthUtils.IS_ORG_MEMBER;
import static org.sagebionetworks.bridge.AuthUtils.IS_ORG_MEMBER_IN_APP;
import static org.sagebionetworks.bridge.AuthUtils.IS_SELF_OR_RESEARCHER;
import static org.sagebionetworks.bridge.AuthUtils.IS_SELF_OR_STUDY_RESEARCHER;
import static org.sagebionetworks.bridge.AuthUtils.IS_SELF_STUDY_TEAM_OR_WORKER;
import static org.sagebionetworks.bridge.AuthUtils.IS_SELF_ORGADMIN_OR_WORKER;
import static org.sagebionetworks.bridge.AuthUtils.IS_STUDY_RESEARCHER;
import static org.sagebionetworks.bridge.AuthUtils.IS_STUDY_TEAM_OR_WORKER;
import static org.sagebionetworks.bridge.RequestContext.NULL_INSTANCE;
import static org.sagebionetworks.bridge.Roles.ADMIN;
import static org.sagebionetworks.bridge.Roles.DEVELOPER;
import static org.sagebionetworks.bridge.Roles.ORG_ADMIN;
import static org.sagebionetworks.bridge.Roles.RESEARCHER;
import static org.sagebionetworks.bridge.Roles.SUPERADMIN;
import static org.sagebionetworks.bridge.Roles.WORKER;
import static org.sagebionetworks.bridge.TestConstants.OWNER_ID;
import static org.sagebionetworks.bridge.TestConstants.TEST_APP_ID;
import static org.sagebionetworks.bridge.TestConstants.TEST_ORG_ID;
import static org.sagebionetworks.bridge.TestConstants.TEST_STUDY_ID;
import static org.sagebionetworks.bridge.TestConstants.USER_ID;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertTrue;

import java.util.Set;

import com.google.common.collect.ImmutableSet;

import org.mockito.Mockito;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.Test;

import org.sagebionetworks.bridge.exceptions.UnauthorizedException;

public class AuthUtilsTest extends Mockito {
    private static final String SHARED_OWNER_ID = TEST_APP_ID + ":" + OWNER_ID;
    
    @AfterMethod
    public void afterMethod() {
        RequestContext.set(NULL_INSTANCE);
    }
    
    @Test
    public void checkSelfOrStudyResearcherSucceedsForSelf() {
        RequestContext.set(new RequestContext.Builder().withCallerUserId(USER_ID).build());
        
        IS_SELF_OR_STUDY_RESEARCHER.checkStudyAndUserIds(TEST_STUDY_ID, USER_ID);
    }
    
    @Test
    public void checkSelfOrStudyResearcherSucceedsForStudyResearcher() {
        RequestContext.set(new RequestContext.Builder()
                .withCallerRoles(ImmutableSet.of(RESEARCHER))
                .withOrgSponsoredStudies(ImmutableSet.of(TEST_STUDY_ID))
                .build());
        
        IS_SELF_OR_STUDY_RESEARCHER.checkStudyAndUserIds(TEST_STUDY_ID, USER_ID);
    }

    @Test
    public void checkSelfOrStudyResearcherSucceedsForAdmin() {
        RequestContext.set(new RequestContext.Builder()
                .withCallerRoles(ImmutableSet.of(ADMIN))
                .build());
        
        IS_SELF_OR_STUDY_RESEARCHER.checkStudyAndUserIds(TEST_STUDY_ID, USER_ID);
    }
    
    @Test(expectedExceptions = UnauthorizedException.class)
    public void checkSelfOrStudyResearcherFailsForNonStudyResearcher() {
        RequestContext.set(new RequestContext.Builder()
                .withCallerRoles(ImmutableSet.of(RESEARCHER))
                .withOrgSponsoredStudies(ImmutableSet.of("someOtherStudy"))
                .build());
        
        IS_SELF_OR_STUDY_RESEARCHER.checkStudyAndUserIds(TEST_STUDY_ID, USER_ID);
    }

    @Test(expectedExceptions = UnauthorizedException.class)
    public void checkSelfOrStudyResearcherFailsForDev() {
        RequestContext.set(new RequestContext.Builder()
                .withCallerRoles(ImmutableSet.of(DEVELOPER))
                .withOrgSponsoredStudies(ImmutableSet.of(TEST_STUDY_ID))
                .build());
        
        IS_SELF_OR_STUDY_RESEARCHER.checkStudyAndUserIds(TEST_STUDY_ID, USER_ID);
    }

    @Test
    public void checkOrgMemberSucceedsForMatchingOrgId() {
        RequestContext.set(new RequestContext.Builder()
                .withCallerOrgMembership(TEST_ORG_ID).build());
        
        IS_ORG_MEMBER.checkOrgId(TEST_ORG_ID);
    }
    
    @Test(expectedExceptions = UnauthorizedException.class)
    public void checkOrgMemberFailsWrongOrganization() {
        RequestContext.set(new RequestContext.Builder()
                .withCallerOrgMembership("another-organization").build());
        
        IS_ORG_MEMBER.checkOrgId(TEST_ORG_ID);
    }
    
    @Test(expectedExceptions = UnauthorizedException.class)
    public void checkOrgMemberFailsOnNullOrg() {
        RequestContext.set(new RequestContext.Builder().build());
        
        IS_ORG_MEMBER.checkOrgId(TEST_ORG_ID);
    }
    
    @Test
    public void checkOrgMemberSucceeds() {
        RequestContext.set(new RequestContext.Builder()
                .withCallerOrgMembership(TEST_ORG_ID).build());
        
        IS_ORG_MEMBER.checkOrgId(TEST_ORG_ID);
    }

    @Test(expectedExceptions = UnauthorizedException.class)
    public void checkOrgMembershipFails() {
        RequestContext.set(new RequestContext.Builder().build());
        
        IS_ORG_MEMBER.checkOrgId(TEST_ORG_ID);
    }
    
    @Test
    public void checkOrgMemberOfSharedAssessmentOwnerSucceedsForAdmin() {
        RequestContext.set(new RequestContext.Builder()
                .withCallerRoles(ImmutableSet.of(ADMIN)).build());
        IS_ORG_MEMBER_IN_APP.checkOwnerId(SHARED_OWNER_ID);
    }
    
    @Test(expectedExceptions = UnauthorizedException.class)
    public void checkOrgMemberOfSharedAssessmentOwnerFailsForGlobalOwnerId() {
        RequestContext.set(NULL_INSTANCE);
        IS_ORG_MEMBER_IN_APP.checkOwnerId(OWNER_ID);
    }
    
    @Test
    public void checkOrgMemberOfSharedAssessmentOwnerSucceedsForOwner() {
        RequestContext.set(new RequestContext.Builder()
                .withCallerAppId(TEST_APP_ID)
                .withCallerOrgMembership(OWNER_ID).build());
        IS_ORG_MEMBER_IN_APP.checkOwnerId(SHARED_OWNER_ID);
    }

    @Test(expectedExceptions = UnauthorizedException.class)
    public void checkOrgMemberOfSharedAssessmentOwnerFailsWrongOrgId() {
        RequestContext.set(new RequestContext.Builder()
                .withCallerOrgMembership("notValidOwner").build());
        IS_ORG_MEMBER_IN_APP.checkOwnerId(SHARED_OWNER_ID);
    }
    
    @Test(expectedExceptions = UnauthorizedException.class)
    public void checkOrgMemberOfSharedAssessmentOwnerFailsWrongAppId() { 
        RequestContext.set(new RequestContext.Builder()
                .withCallerOrgMembership(TEST_APP_ID).build());
        IS_ORG_MEMBER_IN_APP.checkOwnerId(SHARED_OWNER_ID);        
    }
    
    @Test(expectedExceptions = UnauthorizedException.class)
    public void checkOrgMemberOfSharedAssessmentOwnerFailsUserWrongAppId() { 
        RequestContext.set(NULL_INSTANCE);
        // still doesn't pass because the appId must always match (global users must call 
        // this API after associating to the right app context):
        IS_ORG_MEMBER_IN_APP.checkOwnerId("other:"+OWNER_ID);
    }
    
    @Test
    public void checkSelfOrResearcherSucceedsForSelf() {
        RequestContext.set(new RequestContext.Builder()
                .withCallerUserId(USER_ID).build());
        
        IS_SELF_OR_RESEARCHER.checkUserId(USER_ID);
    }
    
    @Test
    public void checkSelfOrResearcherSucceedsForResearcher() {
        RequestContext.set(new RequestContext.Builder()
                .withCallerRoles(ImmutableSet.of(RESEARCHER))
                .withCallerUserId("notUserId").build());
        
        IS_SELF_OR_RESEARCHER.checkUserId(USER_ID);
    }
    
    @Test(expectedExceptions = UnauthorizedException.class)
    public void checkSelfOrResearcherFails() {
        RequestContext.set(new RequestContext.Builder()
                .withCallerRoles(ImmutableSet.of(DEVELOPER))
                .withCallerUserId("notUserId").build());
        
        IS_SELF_OR_RESEARCHER.checkUserId(USER_ID);
    }
    
    @Test(expectedExceptions = UnauthorizedException.class)
    public void checkStudyTeamMemberOrWorkerFailsOnStudyAccess() { 
        RequestContext.set(new RequestContext.Builder()
                .withOrgSponsoredStudies(ImmutableSet.of("studyA", "studyB")).build());
        
        IS_STUDY_TEAM_OR_WORKER.checkStudyId(TEST_STUDY_ID);
    }
    
    @Test
    public void isStudyTeamMemberOrWorkerFails() {
        RequestContext.set(new RequestContext.Builder()
                .withOrgSponsoredStudies(ImmutableSet.of("study1", "study2")).build());
        
        assertFalse( IS_STUDY_TEAM_OR_WORKER.verify("studyId", TEST_STUDY_ID) );
    }
    
    @Test
    public void isStudyTeamMemberOrWorkerFailsOnNullStudy() {
        RequestContext.set(new RequestContext.Builder()
                .withOrgSponsoredStudies(ImmutableSet.of("study1", "study2")).build());
        
        assertFalse( IS_STUDY_TEAM_OR_WORKER.verify("studyId", null) );
    }
    
    @Test
    public void isStudyTeamMemberOrWorkerSucceedsForWorker() {
        RequestContext.set(new RequestContext.Builder()
                .withCallerRoles(ImmutableSet.of(WORKER))
                .withOrgSponsoredStudies(ImmutableSet.of("study1", "study2")).build());
        
        assertTrue( IS_STUDY_TEAM_OR_WORKER.verify("studyId", TEST_STUDY_ID) );
    }

    @Test
    public void isStudyTeamMemberOrWorkerSucceedsForAdmin() {
        RequestContext.set(new RequestContext.Builder()
                .withCallerRoles(ImmutableSet.of(ADMIN))
                .withOrgSponsoredStudies(ImmutableSet.of("study1", "study2")).build());
        
        assertTrue( IS_STUDY_TEAM_OR_WORKER.verify("studyId", TEST_STUDY_ID) );
    }
    
    @Test
    public void isStudyTeamMemberOrWorkerSucceedsForOrgSponsoredStudy() {
        RequestContext.set(new RequestContext.Builder()
                .withOrgSponsoredStudies(ImmutableSet.of("study1", "study2")).build());
        
        assertTrue( IS_STUDY_TEAM_OR_WORKER.verify("studyId", "study2") );
    }

    @Test
    public void isInRoleMethodsAreNullSafe() {
        assertFalse(AuthUtils.isInRole(null, (Roles)null));
        assertFalse(AuthUtils.isInRole(null, (Set<Roles>)null));
    }
    
    @Test
    public void isInRoleForSuperadminMatchesEverything() {
        assertTrue(AuthUtils.isInRole(ImmutableSet.of(SUPERADMIN), DEVELOPER));
        assertTrue(AuthUtils.isInRole(ImmutableSet.of(SUPERADMIN), RESEARCHER));
        assertTrue(AuthUtils.isInRole(ImmutableSet.of(SUPERADMIN), ADMIN));
        assertTrue(AuthUtils.isInRole(ImmutableSet.of(SUPERADMIN), WORKER));
        assertTrue(AuthUtils.isInRole(ImmutableSet.of(SUPERADMIN), ImmutableSet.of(DEVELOPER)));
        assertTrue(AuthUtils.isInRole(ImmutableSet.of(SUPERADMIN), ImmutableSet.of(RESEARCHER)));
        assertTrue(AuthUtils.isInRole(ImmutableSet.of(SUPERADMIN), ImmutableSet.of(ADMIN)));
        assertTrue(AuthUtils.isInRole(ImmutableSet.of(SUPERADMIN), ImmutableSet.of(WORKER)));
        assertTrue(AuthUtils.isInRole(ImmutableSet.of(SUPERADMIN), ImmutableSet.of(DEVELOPER, ADMIN)));
    }
    
    @Test
    public void isInRole() {
        assertFalse(AuthUtils.isInRole(ImmutableSet.of(ADMIN), DEVELOPER));
        assertFalse(AuthUtils.isInRole(ImmutableSet.of(ADMIN), RESEARCHER));
        assertTrue(AuthUtils.isInRole(ImmutableSet.of(ADMIN), ADMIN));
        assertFalse(AuthUtils.isInRole(ImmutableSet.of(ADMIN), WORKER));
        assertFalse(AuthUtils.isInRole(ImmutableSet.of(ADMIN), ImmutableSet.of(DEVELOPER)));
        assertFalse(AuthUtils.isInRole(ImmutableSet.of(ADMIN), ImmutableSet.of(RESEARCHER)));
        assertTrue(AuthUtils.isInRole(ImmutableSet.of(ADMIN), ImmutableSet.of(ADMIN)));
        assertFalse(AuthUtils.isInRole(ImmutableSet.of(ADMIN), ImmutableSet.of(WORKER)));
        assertTrue(AuthUtils.isInRole(ImmutableSet.of(ADMIN), ImmutableSet.of(DEVELOPER, ADMIN)));
    }
 
    @Test
    public void checkOrgMembershipSucceedsForAdmin() {
        RequestContext.set(new RequestContext.Builder()
                .withCallerRoles(ImmutableSet.of(ADMIN)).build());

        IS_ORG_MEMBER.checkOrgId(TEST_ORG_ID);
    }
    
    @Test
    public void isStudyScopedToCallerSucceedsForGlobalUser() {
        RequestContext.set(new RequestContext.Builder().build());
        
        IS_STUDY_TEAM_OR_WORKER.checkStudyId(TEST_STUDY_ID);
    }
    
    @Test
    public void checkStudyResearcherSuccedsForResearcher() {
        RequestContext.set(new RequestContext.Builder()
                .withOrgSponsoredStudies(ImmutableSet.of(TEST_STUDY_ID))
                .withCallerRoles(ImmutableSet.of(RESEARCHER))
                .build());
        
        IS_STUDY_RESEARCHER.checkStudyId(TEST_STUDY_ID);
    }

    @Test
    public void checkStudyResearcherSuccedsForAdmin() {
        RequestContext.set(new RequestContext.Builder()
                .withCallerRoles(ImmutableSet.of(ADMIN))
                .build());
        
        IS_STUDY_RESEARCHER.checkStudyId(TEST_STUDY_ID);
    }

    @Test(expectedExceptions = UnauthorizedException.class)
    public void checkStudyResearcherFailsWrongStudy() {
        RequestContext.set(new RequestContext.Builder()
                .withOrgSponsoredStudies(ImmutableSet.of("wrong-study"))
                .withCallerRoles(ImmutableSet.of(RESEARCHER))
                .build());
        
        IS_STUDY_RESEARCHER.checkStudyId(TEST_STUDY_ID);
    }
    
    @Test(expectedExceptions = UnauthorizedException.class)
    public void checkStudyResearcherFailsWrongRole() {
        RequestContext.set(new RequestContext.Builder()
                .withOrgSponsoredStudies(ImmutableSet.of(TEST_STUDY_ID))
                .withCallerRoles(ImmutableSet.of(DEVELOPER))
                .build());
        
        IS_STUDY_RESEARCHER.checkStudyId(TEST_STUDY_ID);
    }
    
    @Test
    public void checkOrgAdmin() {
        RequestContext.set(new RequestContext.Builder()
                .withCallerOrgMembership(TEST_ORG_ID)
                .withCallerRoles(ImmutableSet.of(ORG_ADMIN))
                .build());
        
        IS_ORGADMIN.checkOrgId(TEST_ORG_ID);
    }

    @Test
    public void checkOrgAdminSucceedsForAdmin() {
        RequestContext.set(new RequestContext.Builder()
                .withCallerRoles(ImmutableSet.of(ADMIN))
                .build());
        
        IS_ORGADMIN.checkOrgId(TEST_ORG_ID);
    }
    
    @Test(expectedExceptions = UnauthorizedException.class)
    public void checkOrgAdminWrongOrgId() {
        RequestContext.set(new RequestContext.Builder()
                .withCallerOrgMembership("wrong-org-id")
                .withCallerRoles(ImmutableSet.of(ORG_ADMIN))
                .build());
        
        IS_ORGADMIN.checkOrgId(TEST_ORG_ID);
    }
    
    @Test(expectedExceptions = UnauthorizedException.class)
    public void checkOrgAdminWrongRole() {
        RequestContext.set(new RequestContext.Builder()
                .withCallerOrgMembership(TEST_ORG_ID)
                .withCallerRoles(ImmutableSet.of(DEVELOPER))
                .build());
        
        IS_ORGADMIN.checkOrgId(TEST_ORG_ID);
    }
    
    @Test
    public void isOrgMemberSucceedsForOrgMember() {
        RequestContext.set(new RequestContext.Builder()
                .withCallerOrgMembership(TEST_ORG_ID).build());
        
        assertTrue( IS_ORG_MEMBER.verify("orgId", TEST_ORG_ID) );
    }
    
    @Test
    public void isOrgMemberSucceedsForAdmin() {
        RequestContext.set(new RequestContext.Builder()
                .withCallerRoles(ImmutableSet.of(ADMIN)).build());
        
        assertTrue( IS_ORG_MEMBER.verify("orgId", TEST_ORG_ID) );
    }
    
    @Test
    public void isOrgMemberWrongOrg() {
        RequestContext.set(new RequestContext.Builder()
                .withCallerAppId(TEST_APP_ID)
                .withCallerOrgMembership("wrong-org")
                .build());
        
        assertFalse( IS_ORG_MEMBER.verify("orgId", TEST_ORG_ID) );
    }
    
    @Test
    public void isOrgAdmin() {
        RequestContext.set(new RequestContext.Builder()
                .withCallerOrgMembership(TEST_ORG_ID)
                .withCallerRoles(ImmutableSet.of(ORG_ADMIN))
                .build());
        
        assertTrue( IS_ORGADMIN.verify("orgId", TEST_ORG_ID) );
    }

    @Test
    public void isOrgAdminWrongOrgMembership() {
        RequestContext.set(new RequestContext.Builder()
                .withCallerRoles(ImmutableSet.of(ORG_ADMIN))
                .build());
        
        assertFalse( IS_ORGADMIN.verify("orgId", TEST_ORG_ID) );
    }

    @Test
    public void isOrgAdminWrongRole() {
        RequestContext.set(new RequestContext.Builder()
                .withCallerOrgMembership(TEST_ORG_ID)
                .build());
        
        assertFalse( IS_ORGADMIN.verify("orgId", TEST_ORG_ID) );
    }
    
    @Test
    public void isSelfOrStudyTeamMemberOrWorkerSucceesForSelf() {
        RequestContext.set(new RequestContext.Builder()
                .withCallerUserId(USER_ID).build());
        
        assertTrue( IS_SELF_STUDY_TEAM_OR_WORKER.verify("studyId", TEST_STUDY_ID, "userId", USER_ID) );
    }

    @Test
    public void isSelfOrStudyTeamMemberOrWorkerSucceesForStudyTeamMember() {
        RequestContext.set(new RequestContext.Builder()
                .withOrgSponsoredStudies(ImmutableSet.of(TEST_STUDY_ID)).build());
        
        assertTrue( IS_SELF_STUDY_TEAM_OR_WORKER.verify("studyId", TEST_STUDY_ID, "userId", USER_ID) );
    }

    @Test
    public void isSelfOrStudyTeamMemberOrWorkerSucceesForWorker() {
        RequestContext.set(new RequestContext.Builder()
                .withCallerRoles(ImmutableSet.of(WORKER)).build());
        
        assertTrue( IS_SELF_STUDY_TEAM_OR_WORKER.verify("studyId", TEST_STUDY_ID, "userId", USER_ID) );
    }

    @Test
    public void isSelfOrStudyTeamMemberOrWorkerFails() {
        RequestContext.set(new RequestContext.Builder()
                // we have to set this because we still make an exception for accounts
                // associated to no studies (ie not in an org or in an org that isn't
                // sponsoring any studies).
                .withOrgSponsoredStudies(ImmutableSet.of("study1"))
                .build());
        
        assertFalse( IS_SELF_STUDY_TEAM_OR_WORKER.verify("studyId", TEST_STUDY_ID, "userId", USER_ID) );
    }
    
    @Test
    public void isSelfOrStudyTeamMemberOrWorkerForSelf() {
        RequestContext.set(new RequestContext.Builder()
                .withCallerUserId(USER_ID)
                .build());
        
        assertTrue( IS_SELF_STUDY_TEAM_OR_WORKER.verify("studyId", TEST_STUDY_ID, "userId", USER_ID) );
    }

    @Test
    public void isSelfOrStudyTeamMemberOrWorkerForStudyTeamMember() {
        RequestContext.set(new RequestContext.Builder()
                .withCallerUserId(USER_ID)
                .build());
        
        assertTrue( IS_SELF_STUDY_TEAM_OR_WORKER.verify("studyId", TEST_STUDY_ID, "userId", USER_ID) );
    }

    @Test
    public void isSelfOrStudyTeamMemberOrWorkerForWorker() {
        RequestContext.set(new RequestContext.Builder()
                .withCallerRoles(ImmutableSet.of(WORKER))
                .build());
        
        assertTrue( IS_SELF_STUDY_TEAM_OR_WORKER.verify("studyId", TEST_STUDY_ID, "userId", USER_ID) );
    }

    @Test
    public void isSelfWorkerOrOrgAdminFails() {
        RequestContext.set(new RequestContext.Builder()
                // we have to set this because we still make an exception for accounts
                // associated to no studies (ie not in an org or in an org that isn't
                // sponsoring any studies).
                .withOrgSponsoredStudies(ImmutableSet.of("study1"))
                .build());
        
        assertFalse( IS_SELF_ORGADMIN_OR_WORKER.verify("orgId", TEST_ORG_ID, "userId", USER_ID) );
    }

    @Test
    public void isSelfWorkerOrOrgAdminForSelf() {
        RequestContext.set(new RequestContext.Builder()
                .withCallerUserId(USER_ID)
                .build());
        
        assertTrue( IS_SELF_ORGADMIN_OR_WORKER.verify("orgId", TEST_ORG_ID, "userId", USER_ID) );
    }

    @Test
    public void isSelfWorkerOrOrgAdminForWorker() {
        RequestContext.set(new RequestContext.Builder()
                .withCallerRoles(ImmutableSet.of(WORKER))
                .build());
        
        assertTrue( IS_SELF_ORGADMIN_OR_WORKER.verify("orgId", TEST_ORG_ID, "userId", USER_ID) );
    }

    @Test
    public void isSelfWorkerOrOrgAdminForOrgAdmin() {
        RequestContext.set(new RequestContext.Builder()
                .withCallerOrgMembership(TEST_ORG_ID)
                .withCallerRoles(ImmutableSet.of(ORG_ADMIN))
                .build());
        
        assertTrue( IS_SELF_ORGADMIN_OR_WORKER.verify("orgId", TEST_ORG_ID, "userId", USER_ID) );
    }

    @Test
    public void checkStudyResearcher() {
        RequestContext.set(new RequestContext.Builder()
                .withOrgSponsoredStudies(ImmutableSet.of(TEST_STUDY_ID))
                .withCallerRoles(ImmutableSet.of(RESEARCHER)).build());
        
        IS_STUDY_RESEARCHER.checkStudyId(TEST_STUDY_ID);
    }

    @Test(expectedExceptions = UnauthorizedException.class)
    public void checkStudyResearcherFails() {
        RequestContext.set(new RequestContext.Builder()
                .withOrgSponsoredStudies(ImmutableSet.of("studyA"))
                .withCallerRoles(ImmutableSet.of(RESEARCHER)).build());
        
        IS_STUDY_RESEARCHER.checkStudyId(TEST_STUDY_ID);
    }
}