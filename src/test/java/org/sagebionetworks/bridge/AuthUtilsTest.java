package org.sagebionetworks.bridge;

import static org.sagebionetworks.bridge.AuthEvaluatorField.ORG_ID;
import static org.sagebionetworks.bridge.AuthEvaluatorField.OWNER_ID;
import static org.sagebionetworks.bridge.AuthEvaluatorField.STUDY_ID;
import static org.sagebionetworks.bridge.AuthEvaluatorField.USER_ID;
import static org.sagebionetworks.bridge.AuthUtils.CAN_EDIT_MEMBERS;
import static org.sagebionetworks.bridge.AuthUtils.CAN_EDIT_ASSESSMENTS;
import static org.sagebionetworks.bridge.AuthUtils.CAN_EDIT_SHARED_ASSESSMENTS;
import static org.sagebionetworks.bridge.AuthUtils.CAN_EDIT_ENROLLMENTS;
import static org.sagebionetworks.bridge.AuthUtils.CAN_READ_STUDY_ASSOCIATIONS;
import static org.sagebionetworks.bridge.AuthUtils.CAN_READ_EXTERNAL_IDS;
import static org.sagebionetworks.bridge.AuthUtils.CAN_EDIT_STUDY_PARTICIPANTS;
import static org.sagebionetworks.bridge.AuthUtils.CAN_READ_PARTICIPANTS;
import static org.sagebionetworks.bridge.AuthUtils.CAN_EDIT_PARTICIPANTS;
import static org.sagebionetworks.bridge.AuthUtils.CAN_EDIT_SCHEDULES;
import static org.sagebionetworks.bridge.RequestContext.NULL_INSTANCE;
import static org.sagebionetworks.bridge.Roles.ADMIN;
import static org.sagebionetworks.bridge.Roles.DEVELOPER;
import static org.sagebionetworks.bridge.Roles.ORG_ADMIN;
import static org.sagebionetworks.bridge.Roles.RESEARCHER;
import static org.sagebionetworks.bridge.Roles.STUDY_COORDINATOR;
import static org.sagebionetworks.bridge.Roles.STUDY_DESIGNER;
import static org.sagebionetworks.bridge.Roles.SUPERADMIN;
import static org.sagebionetworks.bridge.Roles.WORKER;
import static org.sagebionetworks.bridge.TestConstants.TEST_OWNER_ID;
import static org.sagebionetworks.bridge.TestConstants.TEST_APP_ID;
import static org.sagebionetworks.bridge.TestConstants.TEST_ORG_ID;
import static org.sagebionetworks.bridge.TestConstants.TEST_STUDY_ID;
import static org.sagebionetworks.bridge.TestConstants.TEST_USER_ID;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertTrue;

import java.util.Set;

import com.google.common.collect.ImmutableSet;

import org.mockito.Mockito;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.Test;

import org.sagebionetworks.bridge.exceptions.UnauthorizedException;

public class AuthUtilsTest extends Mockito {
    private static final String SHARED_OWNER_ID = TEST_APP_ID + ":" + TEST_OWNER_ID;
    
    @AfterMethod
    public void afterMethod() {
        RequestContext.set(NULL_INSTANCE);
    }
    
    @Test
    public void canEditEnrollmentsSucceedsForSelf() {
        RequestContext.set(new RequestContext.Builder().withCallerUserId(TEST_USER_ID).build());
        
        CAN_EDIT_ENROLLMENTS.checkAndThrow(STUDY_ID, TEST_STUDY_ID, USER_ID, TEST_USER_ID);
    }
    
    @Test
    public void canEditEnrollmentsSucceedsForStudyResearcher() {
        RequestContext.set(new RequestContext.Builder()
                .withCallerRoles(ImmutableSet.of(RESEARCHER))
                .withOrgSponsoredStudies(ImmutableSet.of(TEST_STUDY_ID))
                .build());
        
        CAN_EDIT_ENROLLMENTS.checkAndThrow(STUDY_ID, TEST_STUDY_ID, USER_ID, TEST_USER_ID);
    }

    @Test
    public void canEditEnrollmentsSucceedsForAdmin() {
        RequestContext.set(new RequestContext.Builder()
                .withCallerRoles(ImmutableSet.of(ADMIN))
                .build());
        
        CAN_EDIT_ENROLLMENTS.checkAndThrow(STUDY_ID, TEST_STUDY_ID, USER_ID, TEST_USER_ID);
    }
    
    @Test(expectedExceptions = UnauthorizedException.class)
    public void canEditEnrollmentsFailsForNonStudyCoordinator() {
        RequestContext.set(new RequestContext.Builder()
                .withCallerRoles(ImmutableSet.of(STUDY_COORDINATOR))
                .withOrgSponsoredStudies(ImmutableSet.of("someOtherStudy"))
                .build());
        
        CAN_EDIT_ENROLLMENTS.checkAndThrow(STUDY_ID, TEST_STUDY_ID, USER_ID, TEST_USER_ID);
    }

    @Test(expectedExceptions = UnauthorizedException.class)
    public void canEditEnrollmentsFailsForDev() {
        RequestContext.set(new RequestContext.Builder()
                .withCallerRoles(ImmutableSet.of(DEVELOPER))
                .withOrgSponsoredStudies(ImmutableSet.of(TEST_STUDY_ID))
                .build());
        
        CAN_EDIT_ENROLLMENTS.checkAndThrow(STUDY_ID, TEST_STUDY_ID, USER_ID, TEST_USER_ID);
    }

    @Test
    public void canEditEnrollmentsSucceedsForMatchingOrgId() {
        RequestContext.set(new RequestContext.Builder()
                .withCallerOrgMembership(TEST_ORG_ID).build());
        
        CAN_EDIT_ASSESSMENTS.checkAndThrow(ORG_ID, TEST_ORG_ID);
    }
    
    @Test(expectedExceptions = UnauthorizedException.class)
    public void canEditEnrollmentsFailsWrongOrganization() {
        RequestContext.set(new RequestContext.Builder()
                .withCallerOrgMembership("another-organization").build());
        
        CAN_EDIT_ASSESSMENTS.checkAndThrow(ORG_ID, TEST_ORG_ID);
    }
    
    @Test(expectedExceptions = UnauthorizedException.class)
    public void canEditEnrollmentsFailsOnNullOrg() {
        RequestContext.set(new RequestContext.Builder().build());
        
        CAN_EDIT_ASSESSMENTS.checkAndThrow(ORG_ID, TEST_ORG_ID);
    }
    
    @Test
    public void canEditEnrollmentsSucceeds() {
        RequestContext.set(new RequestContext.Builder()
                .withCallerOrgMembership(TEST_ORG_ID).build());
        
        CAN_EDIT_ASSESSMENTS.checkAndThrow(ORG_ID, TEST_ORG_ID);
    }

    @Test(expectedExceptions = UnauthorizedException.class)
    public void canEditEnrollmentsFails() {
        RequestContext.set(new RequestContext.Builder().build());
        
        CAN_EDIT_ASSESSMENTS.checkAndThrow(ORG_ID, TEST_ORG_ID);
    }
    
    @Test
    public void canEditSharedAssessmentsSucceedsForAdmin() {
        RequestContext.set(new RequestContext.Builder()
                .withCallerRoles(ImmutableSet.of(ADMIN)).build());
        CAN_EDIT_SHARED_ASSESSMENTS.checkAndThrow(OWNER_ID, SHARED_OWNER_ID);
    }
    
    @Test(expectedExceptions = UnauthorizedException.class)
    public void canEditSharedAssessmentsFailsForGlobalOwnerId() {
        RequestContext.set(NULL_INSTANCE);
        CAN_EDIT_SHARED_ASSESSMENTS.checkAndThrow(OWNER_ID, TEST_OWNER_ID);
    }
    
    @Test
    public void canEditSharedAssessmentsSucceedsForOwner() {
        RequestContext.set(new RequestContext.Builder()
                .withCallerAppId(TEST_APP_ID)
                .withCallerOrgMembership(TEST_OWNER_ID).build());
        CAN_EDIT_SHARED_ASSESSMENTS.checkAndThrow(OWNER_ID, SHARED_OWNER_ID);
    }

    @Test(expectedExceptions = UnauthorizedException.class)
    public void canEditSharedAssessmentsFailsWrongOrgId() {
        RequestContext.set(new RequestContext.Builder()
                .withCallerOrgMembership("notValidOwner").build());
        CAN_EDIT_SHARED_ASSESSMENTS.checkAndThrow(OWNER_ID, SHARED_OWNER_ID);
    }
    
    @Test(expectedExceptions = UnauthorizedException.class)
    public void canEditSharedAssessmentsFailsWrongAppId() { 
        RequestContext.set(new RequestContext.Builder()
                .withCallerOrgMembership(TEST_APP_ID).build());
        CAN_EDIT_SHARED_ASSESSMENTS.checkAndThrow(OWNER_ID, SHARED_OWNER_ID);        
    }
    
    @Test(expectedExceptions = UnauthorizedException.class)
    public void canEditSharedAssessmentsFailsUserWrongAppId() { 
        RequestContext.set(NULL_INSTANCE);
        // still doesn't pass because the appId must always match (global users must call 
        // this API after associating to the right app context):
        CAN_EDIT_SHARED_ASSESSMENTS.checkAndThrow(OWNER_ID, "other:"+TEST_OWNER_ID);
    }
    
    @Test(expectedExceptions = UnauthorizedException.class)
    public void canEditParticipantsFailsOnStudyAccess() { 
        RequestContext.set(new RequestContext.Builder()
                .withCallerUserId("callerUserId")
                .withOrgSponsoredStudies(ImmutableSet.of("studyA", "studyB")).build());
        
        CAN_EDIT_PARTICIPANTS.checkAndThrow(STUDY_ID, TEST_STUDY_ID);
    }
    
    @Test
    public void canEditParticipantsFails() {
        RequestContext.set(new RequestContext.Builder()
                .withCallerUserId("callerUserId")
                .withOrgSponsoredStudies(ImmutableSet.of("study1", "study2")).build());
        
        assertFalse( CAN_EDIT_PARTICIPANTS.check(STUDY_ID, TEST_STUDY_ID) );
    }
    
    @Test
    public void canEditParticipantsFailsOnNullStudy() {
        RequestContext.set(new RequestContext.Builder()
                .withCallerUserId("callerUserId")
                .withOrgSponsoredStudies(ImmutableSet.of("study1", "study2")).build());
        
        assertFalse( CAN_EDIT_PARTICIPANTS.check(STUDY_ID, null) );
    }
    
    @Test
    public void canEditParticipantsSucceedsForWorker() {
        RequestContext.set(new RequestContext.Builder()
                .withCallerRoles(ImmutableSet.of(WORKER))
                .withOrgSponsoredStudies(ImmutableSet.of("study1", "study2")).build());
        
        assertTrue( CAN_EDIT_PARTICIPANTS.check(STUDY_ID, TEST_STUDY_ID) );
    }

    @Test
    public void canEditParticipantsSucceedsForAdmin() {
        RequestContext.set(new RequestContext.Builder()
                .withCallerRoles(ImmutableSet.of(ADMIN))
                .withOrgSponsoredStudies(ImmutableSet.of("study1", "study2")).build());
        
        assertTrue( CAN_EDIT_PARTICIPANTS.check(STUDY_ID, TEST_STUDY_ID) );
    }
    
    @Test
    public void canEditStudyParticipantsSucceedsForOrgSponsoredStudy() {
        RequestContext.set(new RequestContext.Builder()
                .withCallerRoles(ImmutableSet.of(STUDY_COORDINATOR))
                .withOrgSponsoredStudies(ImmutableSet.of("study1", "study2")).build());
        
        assertTrue( CAN_EDIT_STUDY_PARTICIPANTS.check(STUDY_ID, "study2") );
    }

    @Test
    public void canEditStudyParticipantsSucceedsForEnrolledParticipant() {
        RequestContext.set(new RequestContext.Builder()
                .withCallerEnrolledStudies(ImmutableSet.of("study1", "study2")).build());
        
        assertTrue( CAN_EDIT_STUDY_PARTICIPANTS.check(STUDY_ID, "study2") );

        RequestContext.set(new RequestContext.Builder()
                .withCallerEnrolledStudies(ImmutableSet.of("study3", "study3")).build());
        
        assertFalse( CAN_EDIT_STUDY_PARTICIPANTS.check(STUDY_ID, "study2") );
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
    public void canEditAssessmentsSucceedsForAdmin() {
        RequestContext.set(new RequestContext.Builder()
                .withCallerRoles(ImmutableSet.of(ADMIN)).build());

        CAN_EDIT_ASSESSMENTS.checkAndThrow(ORG_ID, TEST_ORG_ID);
    }
    
    @Test
    public void canEditParticipantsSucceedsForResearcher() {
        RequestContext.set(new RequestContext.Builder()
                .withCallerRoles(ImmutableSet.of(RESEARCHER))
                .build());
        
        CAN_EDIT_PARTICIPANTS.checkAndThrow(STUDY_ID, TEST_STUDY_ID);
    }
    
    @Test
    public void canEditMembers() {
        RequestContext.set(new RequestContext.Builder()
                .withCallerOrgMembership(TEST_ORG_ID)
                .withCallerRoles(ImmutableSet.of(ORG_ADMIN))
                .build());
        
        CAN_EDIT_MEMBERS.checkAndThrow(ORG_ID, TEST_ORG_ID);
    }

    @Test
    public void canEditMembersSucceedsForAdmin() {
        RequestContext.set(new RequestContext.Builder()
                .withCallerRoles(ImmutableSet.of(ADMIN))
                .build());
        
        CAN_EDIT_MEMBERS.checkAndThrow(ORG_ID, TEST_ORG_ID);
    }
    
    @Test(expectedExceptions = UnauthorizedException.class)
    public void canEditMembersWrongOrgId() {
        RequestContext.set(new RequestContext.Builder()
                .withCallerOrgMembership("wrong-org-id")
                .withCallerRoles(ImmutableSet.of(ORG_ADMIN))
                .build());
        
        CAN_EDIT_MEMBERS.checkAndThrow(ORG_ID, TEST_ORG_ID);
    }
    
    @Test(expectedExceptions = UnauthorizedException.class)
    public void canEditMembersWrongRole() {
        RequestContext.set(new RequestContext.Builder()
                .withCallerOrgMembership(TEST_ORG_ID)
                .withCallerRoles(ImmutableSet.of(DEVELOPER))
                .build());
        
        CAN_EDIT_MEMBERS.checkAndThrow(ORG_ID, TEST_ORG_ID);
    }
    
    @Test
    public void canEditAssessmentsSucceedsForOrgMember() {
        RequestContext.set(new RequestContext.Builder()
                .withCallerOrgMembership(TEST_ORG_ID).build());
        
        assertTrue( CAN_EDIT_ASSESSMENTS.check(ORG_ID, TEST_ORG_ID) );
    }
    
    @Test
    public void canEditAssessmentsWrongOrg() {
        RequestContext.set(new RequestContext.Builder()
                .withCallerAppId(TEST_APP_ID)
                .withCallerOrgMembership("wrong-org")
                .build());
        
        assertFalse( CAN_EDIT_ASSESSMENTS.check(ORG_ID, TEST_ORG_ID) );
    }
    
    @Test
    public void canEditMembersSucceedsForOrgAdmin() {
        RequestContext.set(new RequestContext.Builder()
                .withCallerOrgMembership(TEST_ORG_ID)
                .withCallerRoles(ImmutableSet.of(ORG_ADMIN))
                .build());
        
        assertTrue( CAN_EDIT_MEMBERS.check(ORG_ID, TEST_ORG_ID) );
    }

    @Test
    public void canEditMembersFailsOnWrongOrgMembership() {
        RequestContext.set(new RequestContext.Builder()
                .withCallerRoles(ImmutableSet.of(ORG_ADMIN))
                .build());
        
        assertFalse( CAN_EDIT_MEMBERS.check(ORG_ID, TEST_ORG_ID) );
    }

    @Test
    public void canEditMembersFailsOnNotOrgAdmin() {
        RequestContext.set(new RequestContext.Builder()
                .withCallerOrgMembership(TEST_ORG_ID)
                .build());
        
        assertFalse( CAN_EDIT_MEMBERS.check(ORG_ID, TEST_ORG_ID) );
    }
    
    @Test
    public void canReadStudyAssociationsSucceedsForSelf() {
        RequestContext.set(new RequestContext.Builder()
                .withCallerUserId(TEST_USER_ID).build());
        
        assertTrue( CAN_READ_STUDY_ASSOCIATIONS.check(STUDY_ID, TEST_STUDY_ID, USER_ID, TEST_USER_ID) );
    }

    @Test
    public void canReadStudyAssociationsSucceedsForStudyTeamMember() {
        RequestContext.set(new RequestContext.Builder()
                .withCallerRoles(ImmutableSet.of(STUDY_COORDINATOR))
                .withOrgSponsoredStudies(ImmutableSet.of(TEST_STUDY_ID)).build());
        
        assertTrue( CAN_READ_STUDY_ASSOCIATIONS.check(STUDY_ID, TEST_STUDY_ID, USER_ID, TEST_USER_ID) );
    }

    @Test
    public void canReadStudyAssociationsSucceedsForWorker() {
        RequestContext.set(new RequestContext.Builder()
                .withCallerRoles(ImmutableSet.of(WORKER)).build());
        
        assertTrue( CAN_READ_STUDY_ASSOCIATIONS.check(STUDY_ID, TEST_STUDY_ID, USER_ID, TEST_USER_ID) );
    }

    @Test
    public void canReadStudyAssociationsFails() {
        RequestContext.set(new RequestContext.Builder()
                // we have to set this because we still make an exception for accounts
                // associated to no studies (ie not in an org or in an org that isn't
                // sponsoring any studies).
                .withOrgSponsoredStudies(ImmutableSet.of("study1"))
                .build());
        
        assertFalse( CAN_READ_STUDY_ASSOCIATIONS.check(STUDY_ID, TEST_STUDY_ID, USER_ID, TEST_USER_ID) );
    }
    
    @Test
    public void canReadParticipantsFails() {
        RequestContext.set(new RequestContext.Builder()
                // we have to set this because we still make an exception for accounts
                // associated to no studies (ie not in an org or in an org that isn't
                // sponsoring any studies).
                .withOrgSponsoredStudies(ImmutableSet.of("study1"))
                .build());
        
        assertFalse( CAN_READ_PARTICIPANTS.check(ORG_ID, TEST_ORG_ID, USER_ID, TEST_USER_ID) );
    }

    @Test
    public void canReadParticipantsSucceedsForSelf() {
        RequestContext.set(new RequestContext.Builder()
                .withCallerUserId(TEST_USER_ID)
                .build());
        
        assertTrue( CAN_READ_PARTICIPANTS.check(ORG_ID, TEST_ORG_ID, USER_ID, TEST_USER_ID) );
    }

    @Test
    public void canReadParticipantsSucceedsForWorker() {
        RequestContext.set(new RequestContext.Builder()
                .withCallerRoles(ImmutableSet.of(WORKER))
                .build());
        
        assertTrue( CAN_READ_PARTICIPANTS.check(ORG_ID, TEST_ORG_ID, USER_ID, TEST_USER_ID) );
    }

    @Test
    public void canReadParticipantsSucceedsForOrgAdmin() {
        RequestContext.set(new RequestContext.Builder()
                .withCallerOrgMembership(TEST_ORG_ID)
                .withCallerRoles(ImmutableSet.of(ORG_ADMIN))
                .build());
        
        assertTrue( CAN_READ_PARTICIPANTS.check(ORG_ID, TEST_ORG_ID, USER_ID, TEST_USER_ID) );
    }
    
    @Test
    public void canReadExternalIdsSucceedsForStudyCoordinator() {
        RequestContext.set(new RequestContext.Builder()
                .withOrgSponsoredStudies(ImmutableSet.of(TEST_STUDY_ID))
                .withCallerRoles(ImmutableSet.of(STUDY_COORDINATOR))
                .build());
        
        CAN_READ_EXTERNAL_IDS.checkAndThrow(STUDY_ID, TEST_STUDY_ID);
    }

    @Test
    public void canReadExternalIdsSucceedsForDeveloper() {
        RequestContext.set(new RequestContext.Builder()
                .withCallerRoles(ImmutableSet.of(DEVELOPER))
                .build());
        
        CAN_READ_EXTERNAL_IDS.checkAndThrow(STUDY_ID, TEST_STUDY_ID);
    }

    @Test
    public void canReadExternalIdsSucceedsForResearcher() {
        RequestContext.set(new RequestContext.Builder()
                .withCallerRoles(ImmutableSet.of(RESEARCHER))
                .build());
        
        CAN_READ_EXTERNAL_IDS.checkAndThrow(STUDY_ID, TEST_STUDY_ID);
    }
    
    @Test(expectedExceptions = UnauthorizedException.class)
    public void canReadExternalIdsFails() {
        RequestContext.set(new RequestContext.Builder()
                .withOrgSponsoredStudies(ImmutableSet.of("some-other-study"))
                .withCallerRoles(ImmutableSet.of(STUDY_COORDINATOR))
                .build());
        
        CAN_READ_EXTERNAL_IDS.checkAndThrow(STUDY_ID, TEST_STUDY_ID);
    }
    
    @Test
    public void canEditStudyParticipantsSucceedsForResearcher() {
        RequestContext.set(new RequestContext.Builder()
                .withCallerRoles(ImmutableSet.of(RESEARCHER))
                .build());
        
        CAN_EDIT_STUDY_PARTICIPANTS.checkAndThrow(STUDY_ID, TEST_STUDY_ID);
    }

    @Test(expectedExceptions = UnauthorizedException.class)
    public void canEditStudyParticipantsFailsForWrongStudyCoordinator() {
        RequestContext.set(new RequestContext.Builder()
                .withOrgSponsoredStudies(ImmutableSet.of("some-study-id"))
                .withCallerRoles(ImmutableSet.of(STUDY_COORDINATOR))
                .build());
        
        CAN_EDIT_STUDY_PARTICIPANTS.checkAndThrow(STUDY_ID, TEST_STUDY_ID);
    }
    
    @Test
    public void canReadSchedulesFailsForStudyCoordinator() { // for example
        RequestContext.set(new RequestContext.Builder()
                .withCallerOrgMembership(TEST_ORG_ID)
                .withCallerRoles(ImmutableSet.of(STUDY_COORDINATOR)).build());
        
        assertFalse( AuthUtils.CAN_READ_SCHEDULES.check(ORG_ID, TEST_ORG_ID) );
    }
    
    @Test
    public void canReadSchedulesSucceedsForDeveloper() {
        RequestContext.set(new RequestContext.Builder()
                .withCallerRoles(ImmutableSet.of(DEVELOPER)).build());
        
        assertTrue( AuthUtils.CAN_READ_SCHEDULES.check(ORG_ID, TEST_ORG_ID) );
    }

    @Test
    public void canReadSchedulesSucceedsForStudyDesigner() {
        RequestContext.set(new RequestContext.Builder()
                .withCallerOrgMembership(TEST_ORG_ID)
                .withCallerRoles(ImmutableSet.of(STUDY_DESIGNER)).build());
        
        assertTrue( AuthUtils.CAN_READ_SCHEDULES.check(ORG_ID, TEST_ORG_ID) );
    }

    @Test
    public void canReadSchedulesSucceedsForEnrollee() {
        RequestContext.set(new RequestContext.Builder()
                .withCallerEnrolledStudies(ImmutableSet.of(TEST_STUDY_ID)).build());
        
        assertTrue( AuthUtils.CAN_READ_SCHEDULES.check(STUDY_ID, TEST_STUDY_ID) );
    }

    @Test
    public void canReadSchedulesFailsForNonEnrollee() {
        RequestContext.set(new RequestContext.Builder()
                .withCallerEnrolledStudies(ImmutableSet.of("someOtherStudy")).build());
        
        assertFalse( AuthUtils.CAN_READ_SCHEDULES.check(STUDY_ID, TEST_STUDY_ID) );
    }
    
    @Test
    public void canReadSchedulesFailsForStudyDesignerInOtherOrg() {
        RequestContext.set(new RequestContext.Builder()
                .withCallerOrgMembership("other-organization")
                .withCallerRoles(ImmutableSet.of(STUDY_DESIGNER)).build());
        
        assertFalse( AuthUtils.CAN_READ_SCHEDULES.check(ORG_ID, TEST_ORG_ID) );
    }
    
    @Test
    public void canEditSchedulesSucceedsForStudyDesigner() {
        RequestContext.set(new RequestContext.Builder()
                .withCallerOrgMembership(TEST_ORG_ID)
                .withCallerRoles(ImmutableSet.of(STUDY_DESIGNER)).build());
                
        CAN_EDIT_SCHEDULES.checkAndThrow(ORG_ID, TEST_ORG_ID);
    }

    @Test
    public void canEditSchedulesSucceedsForDeveloper() {
        RequestContext.set(new RequestContext.Builder()
                .withCallerRoles(ImmutableSet.of(DEVELOPER)).build());
                
        CAN_EDIT_SCHEDULES.checkAndThrow(ORG_ID, TEST_ORG_ID);
    }
    
    @Test(expectedExceptions = UnauthorizedException.class)
    public void canEditSchedulesFailsForAnon() {
        RequestContext.set(NULL_INSTANCE);
                
        CAN_EDIT_SCHEDULES.checkAndThrow(ORG_ID, TEST_ORG_ID);
    }

    @Test(expectedExceptions = UnauthorizedException.class)
    public void canEditSchedulesFailsForStudyCoordinatorNotInOrg() {
        RequestContext.set(new RequestContext.Builder()
                .withCallerOrgMembership("wrongOrganization")
                .withCallerRoles(ImmutableSet.of(STUDY_COORDINATOR)).build());
                
        CAN_EDIT_SCHEDULES.checkAndThrow(ORG_ID, TEST_ORG_ID);
    }

    @Test(expectedExceptions = UnauthorizedException.class)
    public void canEditSchedulesFailsForNonDeveloper() {
        RequestContext.set(new RequestContext.Builder()
                .withCallerRoles(ImmutableSet.of(RESEARCHER)).build());
                
        CAN_EDIT_SCHEDULES.checkAndThrow(ORG_ID, TEST_ORG_ID);
    }
 }