package org.sagebionetworks.bridge;

import static java.util.stream.Collectors.toSet;
import static org.sagebionetworks.bridge.AuthEvaluatorField.ORG_ID;
import static org.sagebionetworks.bridge.AuthEvaluatorField.OWNER_ID;
import static org.sagebionetworks.bridge.AuthEvaluatorField.STUDY_ID;
import static org.sagebionetworks.bridge.AuthEvaluatorField.USER_ID;
import static org.sagebionetworks.bridge.AuthUtils.CAN_EDIT_MEMBERS;
import static org.sagebionetworks.bridge.AuthUtils.CAN_EDIT_ASSESSMENTS;
import static org.sagebionetworks.bridge.AuthUtils.CAN_EDIT_SHARED_ASSESSMENTS;
import static org.sagebionetworks.bridge.AuthUtils.CAN_EDIT_ENROLLMENTS;
import static org.sagebionetworks.bridge.AuthUtils.CAN_EDIT_OTHER_ENROLLMENTS;
import static org.sagebionetworks.bridge.AuthUtils.CAN_READ_STUDY_ASSOCIATIONS;
import static org.sagebionetworks.bridge.AuthUtils.CAN_TRANSITION_STUDY;
import static org.sagebionetworks.bridge.BridgeConstants.TEST_USER_GROUP;
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

import java.util.Arrays;
import java.util.Set;

import com.google.common.collect.ImmutableSet;

import org.mockito.Mockito;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.Test;

import org.sagebionetworks.bridge.exceptions.UnauthorizedException;
import org.sagebionetworks.bridge.models.accounts.Account;
import org.sagebionetworks.bridge.models.studies.Enrollment;

public class AuthUtilsTest extends Mockito {
    private static final String SHARED_OWNER_ID = TEST_APP_ID + ":" + TEST_OWNER_ID;
    private static final String OTHER_USER_ID = "otherUserId";
    
    @AfterMethod
    public void afterMethod() {
        RequestContext.set(NULL_INSTANCE);
    }
    
    @Test
    public void canEditEnrollments_succeedsForSelf() {
        RequestContext.set(new RequestContext.Builder().withCallerUserId(TEST_USER_ID).build());
        
        CAN_EDIT_ENROLLMENTS.checkAndThrow(STUDY_ID, TEST_STUDY_ID, USER_ID, TEST_USER_ID);
    }
    
    @Test
    public void canEditEnrollments_succeedsForStudyResearcher() {
        RequestContext.set(new RequestContext.Builder()
                .withCallerRoles(ImmutableSet.of(RESEARCHER))
                .withOrgSponsoredStudies(ImmutableSet.of(TEST_STUDY_ID))
                .build());
        
        CAN_EDIT_ENROLLMENTS.checkAndThrow(STUDY_ID, TEST_STUDY_ID, USER_ID, TEST_USER_ID);
    }

    @Test
    public void canEditEnrollments_succeedsForAdmin() {
        RequestContext.set(new RequestContext.Builder()
                .withCallerRoles(ImmutableSet.of(ADMIN))
                .build());
        
        CAN_EDIT_ENROLLMENTS.checkAndThrow(STUDY_ID, TEST_STUDY_ID, USER_ID, TEST_USER_ID);
    }
    
    @Test(expectedExceptions = UnauthorizedException.class)
    public void canEditEnrollments_failsForNonStudyCoordinator() {
        RequestContext.set(new RequestContext.Builder()
                .withCallerUserId(OTHER_USER_ID)
                .withCallerRoles(ImmutableSet.of(STUDY_COORDINATOR))
                .withOrgSponsoredStudies(ImmutableSet.of("someOtherStudy"))
                .build());
        
        CAN_EDIT_ENROLLMENTS.checkAndThrow(STUDY_ID, TEST_STUDY_ID, USER_ID, TEST_USER_ID);
    }

    // A test used to verify that developers could not change enrollments. They can now change
    // enrollments, but they can only do this on accounts they can “see,” and that only includes
    // test accounts. So the test has been removed.
    // canEditEnrollmentsFailsForDev

    @Test
    public void canEditEnrollments_succeedsForMatchingOrgId() {
        RequestContext.set(new RequestContext.Builder()
                .withCallerOrgMembership(TEST_ORG_ID).build());
        
        CAN_EDIT_ENROLLMENTS.checkAndThrow(ORG_ID, TEST_ORG_ID);
    }
    
    @Test(expectedExceptions = UnauthorizedException.class)
    public void canEditEnrollments_failsWrongOrganization() {
        RequestContext.set(new RequestContext.Builder()
                .withCallerUserId(TEST_USER_ID)
                .withCallerOrgMembership("another-organization").build());
        
        CAN_EDIT_ENROLLMENTS.checkAndThrow(ORG_ID, TEST_ORG_ID);
    }
    
    @Test(expectedExceptions = UnauthorizedException.class)
    public void canEditEnrollments_failsOnNullOrg() {
        RequestContext.set(new RequestContext.Builder()
                .withCallerUserId(TEST_USER_ID)
                .build());
        
        CAN_EDIT_ENROLLMENTS.checkAndThrow(ORG_ID, TEST_ORG_ID);
    }
    
    @Test
    public void canEditEnrollments_succeeds() {
        RequestContext.set(new RequestContext.Builder()
                .withCallerOrgMembership(TEST_ORG_ID).build());
        
        CAN_EDIT_ENROLLMENTS.checkAndThrow(ORG_ID, TEST_ORG_ID);
    }

    @Test(expectedExceptions = UnauthorizedException.class)
    public void canEditEnrollments_fails() {
        RequestContext.set(new RequestContext.Builder()
                .withCallerUserId(TEST_USER_ID)
                .build());
        
        CAN_EDIT_ENROLLMENTS.checkAndThrow(ORG_ID, TEST_ORG_ID);
    }
    
    @Test
    public void canEditSharedAssessments_succeedsForAdmin() {
        RequestContext.set(new RequestContext.Builder()
                .withCallerRoles(ImmutableSet.of(ADMIN)).build());
        CAN_EDIT_SHARED_ASSESSMENTS.checkAndThrow(OWNER_ID, SHARED_OWNER_ID);
    }
    
    @Test(expectedExceptions = UnauthorizedException.class)
    public void canEditSharedAssessments_failsForGlobalOwnerId() {
        RequestContext.set(NULL_INSTANCE);
        CAN_EDIT_SHARED_ASSESSMENTS.checkAndThrow(OWNER_ID, TEST_OWNER_ID);
    }
    
    @Test
    public void canEditSharedAssessments_succeedsForStudyDesignerOwner() {
        RequestContext.set(new RequestContext.Builder()
                .withCallerAppId(TEST_APP_ID)
                .withCallerOrgMembership(TEST_OWNER_ID)
                .withCallerRoles(ImmutableSet.of(STUDY_DESIGNER)).build());
        CAN_EDIT_SHARED_ASSESSMENTS.checkAndThrow(OWNER_ID, SHARED_OWNER_ID);
    }

    @Test
    public void canEditSharedAssessments_succeedsForDeveloperOwner() {
        RequestContext.set(new RequestContext.Builder()
                .withCallerAppId(TEST_APP_ID)
                .withCallerOrgMembership(TEST_OWNER_ID)
                .withCallerRoles(ImmutableSet.of(DEVELOPER)).build());
        CAN_EDIT_SHARED_ASSESSMENTS.checkAndThrow(OWNER_ID, SHARED_OWNER_ID);
    }
    
    @Test(expectedExceptions = UnauthorizedException.class)
    public void canEditSharedAssessments_failsForDeveloper() {
        RequestContext.set(new RequestContext.Builder()
                .withCallerAppId("some-other-app")
                .withCallerOrgMembership("some-other-org")
                .withCallerRoles(ImmutableSet.of(STUDY_DESIGNER)).build());
        CAN_EDIT_SHARED_ASSESSMENTS.checkAndThrow(OWNER_ID, SHARED_OWNER_ID);
    }
    
    @Test(expectedExceptions = UnauthorizedException.class)
    public void canEditSharedAssessments_failsWrongOrgId() {
        RequestContext.set(new RequestContext.Builder()
                .withCallerOrgMembership("notValidOwner").build());
        CAN_EDIT_SHARED_ASSESSMENTS.checkAndThrow(OWNER_ID, SHARED_OWNER_ID);
    }
    
    @Test(expectedExceptions = UnauthorizedException.class)
    public void canEditSharedAssessments_failsWrongAppId() { 
        RequestContext.set(new RequestContext.Builder()
                .withCallerOrgMembership(TEST_APP_ID).build());
        CAN_EDIT_SHARED_ASSESSMENTS.checkAndThrow(OWNER_ID, SHARED_OWNER_ID);        
    }
    
    @Test(expectedExceptions = UnauthorizedException.class)
    public void canEditSharedAssessments_failsUserWrongAppId() { 
        RequestContext.set(NULL_INSTANCE);
        // still doesn't pass because the appId must always match (global users must call 
        // this API after associating to the right app context):
        CAN_EDIT_SHARED_ASSESSMENTS.checkAndThrow(OWNER_ID, "other:"+TEST_OWNER_ID);
    }
    
    @Test(expectedExceptions = UnauthorizedException.class)
    public void canEditParticipants_failsOnStudyAccess() { 
        RequestContext.set(new RequestContext.Builder()
                .withCallerUserId("callerUserId")
                .withOrgSponsoredStudies(ImmutableSet.of("studyA", "studyB")).build());
        
        CAN_EDIT_PARTICIPANTS.checkAndThrow(STUDY_ID, TEST_STUDY_ID);
    }
    
    @Test
    public void canEditParticipants_fails() {
        RequestContext.set(new RequestContext.Builder()
                .withCallerUserId("callerUserId")
                .withOrgSponsoredStudies(ImmutableSet.of("study1", "study2")).build());
        
        assertFalse( CAN_EDIT_PARTICIPANTS.check(STUDY_ID, TEST_STUDY_ID) );
    }
    
    @Test
    public void canEditParticipants_failsOnNullStudy() {
        RequestContext.set(new RequestContext.Builder()
                .withCallerUserId("callerUserId")
                .withOrgSponsoredStudies(ImmutableSet.of("study1", "study2")).build());
        
        assertFalse( CAN_EDIT_PARTICIPANTS.check(STUDY_ID, null) );
    }
    
    @Test
    public void canEditParticipants_succeedsForWorker() {
        RequestContext.set(new RequestContext.Builder()
                .withCallerRoles(ImmutableSet.of(WORKER))
                .withOrgSponsoredStudies(ImmutableSet.of("study1", "study2")).build());
        
        assertTrue( CAN_EDIT_PARTICIPANTS.check(STUDY_ID, TEST_STUDY_ID) );
    }

    @Test
    public void canEditParticipants_succeedsForAdmin() {
        RequestContext.set(new RequestContext.Builder()
                .withCallerRoles(ImmutableSet.of(ADMIN))
                .withOrgSponsoredStudies(ImmutableSet.of("study1", "study2")).build());
        
        assertTrue( CAN_EDIT_PARTICIPANTS.check(STUDY_ID, TEST_STUDY_ID) );
    }
    
    @Test
    public void canEditStudyParticipants_succeedsForOrgSponsoredStudy() {
        RequestContext.set(new RequestContext.Builder()
                .withCallerRoles(ImmutableSet.of(STUDY_COORDINATOR))
                .withOrgSponsoredStudies(ImmutableSet.of("study1", "study2")).build());
        
        assertTrue( CAN_EDIT_STUDY_PARTICIPANTS.check(STUDY_ID, "study2") );
    }

    @Test
    public void canEditStudyParticipants_succeedsForEnrolledParticipant() {
        RequestContext.set(new RequestContext.Builder()
                .withCallerEnrolledStudies(ImmutableSet.of("study1", "study2")).build());
        
        assertTrue( CAN_EDIT_STUDY_PARTICIPANTS.check(STUDY_ID, "study2") );

        RequestContext.set(new RequestContext.Builder()
                .withCallerEnrolledStudies(ImmutableSet.of("study3", "study3")).build());
        
        assertFalse( CAN_EDIT_STUDY_PARTICIPANTS.check(STUDY_ID, "study2") );
    }
    
    @Test
    public void isInRoleMethodsAreNullSafe() {
        assertFalse(AuthUtils.isInRole(ImmutableSet.of(DEVELOPER), (Roles)null));
        assertFalse(AuthUtils.isInRole(null, DEVELOPER));
        assertFalse(AuthUtils.isInRole(ImmutableSet.of(DEVELOPER), (Set<Roles>)null));
        assertFalse(AuthUtils.isInRole(null, ImmutableSet.of(DEVELOPER)));        
        assertFalse(AuthUtils.isInRole(null, ImmutableSet.of()));
        assertFalse(AuthUtils.isInRole(ImmutableSet.of(DEVELOPER), ImmutableSet.of()));
    }
    
    @Test
    public void isInRole_superadmin() {
        assertTrue(AuthUtils.isInRole(ImmutableSet.of(SUPERADMIN), DEVELOPER));
        assertTrue(AuthUtils.isInRole(ImmutableSet.of(SUPERADMIN), RESEARCHER));
        assertTrue(AuthUtils.isInRole(ImmutableSet.of(SUPERADMIN), ADMIN));
        assertTrue(AuthUtils.isInRole(ImmutableSet.of(SUPERADMIN), WORKER));
        assertTrue(AuthUtils.isInRole(ImmutableSet.of(SUPERADMIN), ADMIN));
        assertTrue(AuthUtils.isInRole(ImmutableSet.of(SUPERADMIN), SUPERADMIN));
        
        assertTrue(AuthUtils.isInRole(ImmutableSet.of(SUPERADMIN), ImmutableSet.of(DEVELOPER)));
        assertTrue(AuthUtils.isInRole(ImmutableSet.of(SUPERADMIN), ImmutableSet.of(RESEARCHER)));
        assertTrue(AuthUtils.isInRole(ImmutableSet.of(SUPERADMIN), ImmutableSet.of(ADMIN)));
        assertTrue(AuthUtils.isInRole(ImmutableSet.of(SUPERADMIN), ImmutableSet.of(WORKER)));
        assertTrue(AuthUtils.isInRole(ImmutableSet.of(SUPERADMIN), ImmutableSet.of(DEVELOPER, ADMIN)));
        assertTrue(AuthUtils.isInRole(ImmutableSet.of(SUPERADMIN), ImmutableSet.of(SUPERADMIN)));
    }
    
    @Test
    public void isInRole_admin() {
        assertTrue(AuthUtils.isInRole(ImmutableSet.of(ADMIN), DEVELOPER));
        assertTrue(AuthUtils.isInRole(ImmutableSet.of(ADMIN), RESEARCHER));
        assertTrue(AuthUtils.isInRole(ImmutableSet.of(ADMIN), ADMIN));
        assertTrue(AuthUtils.isInRole(ImmutableSet.of(ADMIN), WORKER));
        assertFalse(AuthUtils.isInRole(ImmutableSet.of(ADMIN), SUPERADMIN));
        assertTrue(AuthUtils.isInRole(ImmutableSet.of(ADMIN), ImmutableSet.of(DEVELOPER)));
        assertTrue(AuthUtils.isInRole(ImmutableSet.of(ADMIN), ImmutableSet.of(RESEARCHER)));
        assertTrue(AuthUtils.isInRole(ImmutableSet.of(ADMIN), ImmutableSet.of(ADMIN)));
        assertTrue(AuthUtils.isInRole(ImmutableSet.of(ADMIN), ImmutableSet.of(WORKER)));
        assertTrue(AuthUtils.isInRole(ImmutableSet.of(ADMIN), ImmutableSet.of(DEVELOPER, ADMIN)));
        assertFalse(AuthUtils.isInRole(ImmutableSet.of(ADMIN), ImmutableSet.of(SUPERADMIN)));
        assertFalse(AuthUtils.isInRole(ImmutableSet.of(ADMIN), ImmutableSet.of(DEVELOPER, SUPERADMIN)));
    }
 
    @Test
    public void isInRole_developer() {
        assertTrue(AuthUtils.isInRole(ImmutableSet.of(DEVELOPER), DEVELOPER));
        assertFalse(AuthUtils.isInRole(ImmutableSet.of(DEVELOPER), RESEARCHER));
        assertFalse(AuthUtils.isInRole(ImmutableSet.of(DEVELOPER), ADMIN));
        assertFalse(AuthUtils.isInRole(ImmutableSet.of(DEVELOPER), WORKER));
        assertFalse(AuthUtils.isInRole(ImmutableSet.of(DEVELOPER), SUPERADMIN));
        assertTrue(AuthUtils.isInRole(ImmutableSet.of(DEVELOPER), ImmutableSet.of(DEVELOPER)));
        assertFalse(AuthUtils.isInRole(ImmutableSet.of(DEVELOPER), ImmutableSet.of(RESEARCHER)));
        assertFalse(AuthUtils.isInRole(ImmutableSet.of(DEVELOPER), ImmutableSet.of(ADMIN)));
        assertFalse(AuthUtils.isInRole(ImmutableSet.of(DEVELOPER), ImmutableSet.of(WORKER)));
        assertFalse(AuthUtils.isInRole(ImmutableSet.of(DEVELOPER), ImmutableSet.of(SUPERADMIN)));
        assertTrue(AuthUtils.isInRole(ImmutableSet.of(DEVELOPER), ImmutableSet.of(DEVELOPER, ADMIN)));
        assertTrue(AuthUtils.isInRole(ImmutableSet.of(DEVELOPER), ImmutableSet.of(DEVELOPER, SUPERADMIN)));
    }
    
    @Test
    public void canEditAssessments_succeedsForAdmin() {
        RequestContext.set(new RequestContext.Builder()
                .withCallerRoles(ImmutableSet.of(ADMIN)).build());

        CAN_EDIT_ASSESSMENTS.checkAndThrow(ORG_ID, TEST_ORG_ID);
    }
    
    @Test
    public void canEditParticipants_succeedsForResearcher() {
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
    public void canEditMembers_succeedsForAdmin() {
        RequestContext.set(new RequestContext.Builder()
                .withCallerRoles(ImmutableSet.of(ADMIN))
                .build());
        
        CAN_EDIT_MEMBERS.checkAndThrow(ORG_ID, TEST_ORG_ID);
    }
    
    @Test(expectedExceptions = UnauthorizedException.class)
    public void canEditMembers_wrongOrgId() {
        RequestContext.set(new RequestContext.Builder()
                .withCallerOrgMembership("wrong-org-id")
                .withCallerRoles(ImmutableSet.of(ORG_ADMIN))
                .build());
        
        CAN_EDIT_MEMBERS.checkAndThrow(ORG_ID, TEST_ORG_ID);
    }
    
    @Test(expectedExceptions = UnauthorizedException.class)
    public void canEditMembers_wrongRole() {
        RequestContext.set(new RequestContext.Builder()
                .withCallerOrgMembership(TEST_ORG_ID)
                .withCallerRoles(ImmutableSet.of(DEVELOPER))
                .build());
        
        CAN_EDIT_MEMBERS.checkAndThrow(ORG_ID, TEST_ORG_ID);
    }
    
    @Test
    public void canEditAssessments_succeedsForStudyDesignerOrgMember() {
        RequestContext.set(new RequestContext.Builder()
                .withCallerRoles(ImmutableSet.of(STUDY_DESIGNER))
                .withCallerOrgMembership(TEST_ORG_ID).build());
        
        assertTrue( CAN_EDIT_ASSESSMENTS.check(ORG_ID, TEST_ORG_ID) );
    }
    
    @Test
    public void canEditAssessments_succeedsForDeveloper() {
        RequestContext.set(new RequestContext.Builder()
                .withCallerRoles(ImmutableSet.of(DEVELOPER)).build());
        
        assertTrue( CAN_EDIT_ASSESSMENTS.check(ORG_ID, TEST_ORG_ID) );
    }
    
    @Test
    public void canEditAssessments_failsForStudyCoordinatorOrgMember() {
        RequestContext.set(new RequestContext.Builder()
                .withCallerRoles(ImmutableSet.of(STUDY_COORDINATOR))
                .withCallerOrgMembership(TEST_ORG_ID).build());
        
        assertFalse( CAN_EDIT_ASSESSMENTS.check(ORG_ID, TEST_ORG_ID) );
    }
    
    @Test
    public void canEditAssessments_wrongOrg() {
        RequestContext.set(new RequestContext.Builder()
                .withCallerAppId(TEST_APP_ID)
                .withCallerOrgMembership("wrong-org")
                .build());
        
        assertFalse( CAN_EDIT_ASSESSMENTS.check(ORG_ID, TEST_ORG_ID) );
    }
    
    @Test
    public void canEditMembers_succeedsForOrgAdmin() {
        RequestContext.set(new RequestContext.Builder()
                .withCallerOrgMembership(TEST_ORG_ID)
                .withCallerRoles(ImmutableSet.of(ORG_ADMIN))
                .build());
        
        assertTrue( CAN_EDIT_MEMBERS.check(ORG_ID, TEST_ORG_ID) );
    }

    @Test
    public void canEditMembers_failsOnWrongOrgMembership() {
        RequestContext.set(new RequestContext.Builder()
                .withCallerRoles(ImmutableSet.of(ORG_ADMIN))
                .build());
        
        assertFalse( CAN_EDIT_MEMBERS.check(ORG_ID, TEST_ORG_ID) );
    }

    @Test
    public void canEditMembers_failsOnNotOrgAdmin() {
        RequestContext.set(new RequestContext.Builder()
                .withCallerOrgMembership(TEST_ORG_ID)
                .build());
        
        assertFalse( CAN_EDIT_MEMBERS.check(ORG_ID, TEST_ORG_ID) );
    }
    
    @Test
    public void canReadStudyAssociations_succeedsForSelf() {
        RequestContext.set(new RequestContext.Builder()
                .withCallerUserId(TEST_USER_ID).build());
        
        assertTrue( CAN_READ_STUDY_ASSOCIATIONS.check(STUDY_ID, TEST_STUDY_ID, USER_ID, TEST_USER_ID) );
    }

    @Test
    public void canReadStudyAssociations_succeedsForStudyTeamMember() {
        RequestContext.set(new RequestContext.Builder()
                .withCallerRoles(ImmutableSet.of(STUDY_COORDINATOR))
                .withOrgSponsoredStudies(ImmutableSet.of(TEST_STUDY_ID)).build());
        
        assertTrue( CAN_READ_STUDY_ASSOCIATIONS.check(STUDY_ID, TEST_STUDY_ID, USER_ID, TEST_USER_ID) );
    }

    @Test
    public void canReadStudyAssociations_succeedsForWorker() {
        RequestContext.set(new RequestContext.Builder()
                .withCallerRoles(ImmutableSet.of(WORKER)).build());
        
        assertTrue( CAN_READ_STUDY_ASSOCIATIONS.check(STUDY_ID, TEST_STUDY_ID, USER_ID, TEST_USER_ID) );
    }

    @Test
    public void canReadStudyAssociations_fails() {
        RequestContext.set(new RequestContext.Builder()
                .withCallerUserId(OTHER_USER_ID)
                .build());
        
        assertFalse( CAN_READ_STUDY_ASSOCIATIONS.check(STUDY_ID, TEST_STUDY_ID, USER_ID, TEST_USER_ID) );
    }
    
    @Test
    public void canReadParticipants_fails() {
        RequestContext.set(new RequestContext.Builder()
                .withCallerUserId(OTHER_USER_ID)
                .build());
        
        assertFalse( CAN_READ_PARTICIPANTS.check(ORG_ID, TEST_ORG_ID, USER_ID, TEST_USER_ID) );
    }

    @Test
    public void canReadParticipants_succeedsForSelf() {
        RequestContext.set(new RequestContext.Builder()
                .withCallerUserId(TEST_USER_ID)
                .build());
        
        assertTrue( CAN_READ_PARTICIPANTS.check(ORG_ID, TEST_ORG_ID, USER_ID, TEST_USER_ID) );
    }

    @Test
    public void canReadParticipants_succeedsForWorker() {
        RequestContext.set(new RequestContext.Builder()
                .withCallerRoles(ImmutableSet.of(WORKER))
                .build());
        
        assertTrue( CAN_READ_PARTICIPANTS.check(ORG_ID, TEST_ORG_ID, USER_ID, TEST_USER_ID) );
    }

    @Test
    public void canReadParticipants_succeedsForOrgAdmin() {
        RequestContext.set(new RequestContext.Builder()
                .withCallerOrgMembership(TEST_ORG_ID)
                .withCallerRoles(ImmutableSet.of(ORG_ADMIN))
                .build());
        
        assertTrue( CAN_READ_PARTICIPANTS.check(ORG_ID, TEST_ORG_ID, USER_ID, TEST_USER_ID) );
    }
    
    @Test
    public void canReadExternalIds_succeedsForStudyCoordinator() {
        RequestContext.set(new RequestContext.Builder()
                .withOrgSponsoredStudies(ImmutableSet.of(TEST_STUDY_ID))
                .withCallerRoles(ImmutableSet.of(STUDY_COORDINATOR))
                .build());
        
        CAN_READ_EXTERNAL_IDS.checkAndThrow(STUDY_ID, TEST_STUDY_ID);
    }

    @Test
    public void canReadExternalIds_succeedsForDeveloper() {
        RequestContext.set(new RequestContext.Builder()
                .withCallerRoles(ImmutableSet.of(DEVELOPER))
                .build());
        
        CAN_READ_EXTERNAL_IDS.checkAndThrow(STUDY_ID, TEST_STUDY_ID);
    }

    @Test
    public void canReadExternalIds_succeedsForResearcher() {
        RequestContext.set(new RequestContext.Builder()
                .withCallerRoles(ImmutableSet.of(RESEARCHER))
                .build());
        
        CAN_READ_EXTERNAL_IDS.checkAndThrow(STUDY_ID, TEST_STUDY_ID);
    }
    
    @Test(expectedExceptions = UnauthorizedException.class)
    public void canReadExternalIds_fails() {
        RequestContext.set(new RequestContext.Builder()
                .withOrgSponsoredStudies(ImmutableSet.of("some-other-study"))
                .withCallerRoles(ImmutableSet.of(STUDY_COORDINATOR))
                .build());
        
        CAN_READ_EXTERNAL_IDS.checkAndThrow(STUDY_ID, TEST_STUDY_ID);
    }
    
    @Test
    public void canEditStudyParticipants_succeedsForResearcher() {
        RequestContext.set(new RequestContext.Builder()
                .withCallerRoles(ImmutableSet.of(RESEARCHER))
                .build());
        
        CAN_EDIT_STUDY_PARTICIPANTS.checkAndThrow(STUDY_ID, TEST_STUDY_ID);
    }

    @Test(expectedExceptions = UnauthorizedException.class)
    public void canEditStudyParticipants_failsForWrongStudyCoordinator() {
        RequestContext.set(new RequestContext.Builder()
                .withOrgSponsoredStudies(ImmutableSet.of("some-study-id"))
                .withCallerRoles(ImmutableSet.of(STUDY_COORDINATOR))
                .build());
        
        CAN_EDIT_STUDY_PARTICIPANTS.checkAndThrow(STUDY_ID, TEST_STUDY_ID);
    }
    
    @Test
    public void canReadSchedules_failsForStudyCoordinator() { // for example
        RequestContext.set(new RequestContext.Builder()
                .withCallerRoles(ImmutableSet.of(STUDY_COORDINATOR)).build());
        
        assertFalse( AuthUtils.CAN_READ_SCHEDULES.check(ORG_ID, TEST_ORG_ID) );
    }
    
    @Test
    public void canReadSchedules_succeedsForDeveloper() {
        RequestContext.set(new RequestContext.Builder()
                .withCallerRoles(ImmutableSet.of(DEVELOPER)).build());
        
        assertTrue( AuthUtils.CAN_READ_SCHEDULES.check(ORG_ID, TEST_ORG_ID) );
    }

    @Test
    public void canReadSchedules_succeedsForStudyDesigner() {
        RequestContext.set(new RequestContext.Builder()
                .withCallerOrgMembership(TEST_ORG_ID)
                .withCallerRoles(ImmutableSet.of(STUDY_DESIGNER)).build());
        
        assertTrue( AuthUtils.CAN_READ_SCHEDULES.check(ORG_ID, TEST_ORG_ID) );
    }

    @Test
    public void canReadSchedules_succeedsForEnrollee() {
        RequestContext.set(new RequestContext.Builder()
                .withCallerEnrolledStudies(ImmutableSet.of(TEST_STUDY_ID)).build());
        
        assertTrue( AuthUtils.CAN_READ_SCHEDULES.check(STUDY_ID, TEST_STUDY_ID) );
    }

    @Test
    public void canReadSchedules_failsForNonEnrollee() {
        RequestContext.set(new RequestContext.Builder()
                .withCallerEnrolledStudies(ImmutableSet.of("someOtherStudy")).build());
        
        assertFalse( AuthUtils.CAN_READ_SCHEDULES.check(STUDY_ID, TEST_STUDY_ID) );
    }
    
    @Test
    public void canReadSchedules_failsForStudyDesignerInOtherOrg() {
        RequestContext.set(new RequestContext.Builder()
                .withCallerOrgMembership("other-organization")
                .withCallerRoles(ImmutableSet.of(STUDY_DESIGNER)).build());
        
        assertFalse( AuthUtils.CAN_READ_SCHEDULES.check(ORG_ID, TEST_ORG_ID) );
    }
    
    @Test
    public void canEditSchedules_succeedsForStudyDesigner() {
        RequestContext.set(new RequestContext.Builder()
                .withCallerOrgMembership(TEST_ORG_ID)
                .withCallerRoles(ImmutableSet.of(STUDY_DESIGNER)).build());
                
        CAN_EDIT_SCHEDULES.checkAndThrow(ORG_ID, TEST_ORG_ID);
    }

    @Test
    public void canEditSchedules_succeedsForDeveloper() {
        RequestContext.set(new RequestContext.Builder()
                .withCallerRoles(ImmutableSet.of(DEVELOPER)).build());
                
        CAN_EDIT_SCHEDULES.checkAndThrow(ORG_ID, TEST_ORG_ID);
    }
    
    @Test(expectedExceptions = UnauthorizedException.class)
    public void canEditSchedules_failsForAnon() {
        RequestContext.set(NULL_INSTANCE);
                
        CAN_EDIT_SCHEDULES.checkAndThrow(ORG_ID, TEST_ORG_ID);
    }

    @Test(expectedExceptions = UnauthorizedException.class)
    public void canEditSchedules_failsForStudyCoordinatorNotInOrg() {
        RequestContext.set(new RequestContext.Builder()
                .withCallerOrgMembership("wrongOrganization")
                .withCallerRoles(ImmutableSet.of(STUDY_COORDINATOR)).build());
                
        CAN_EDIT_SCHEDULES.checkAndThrow(ORG_ID, TEST_ORG_ID);
    }

    @Test(expectedExceptions = UnauthorizedException.class)
    public void canEditSchedules_failsForNonDeveloper() {
        RequestContext.set(new RequestContext.Builder()
                .withCallerRoles(ImmutableSet.of(RESEARCHER)).build());
                
        CAN_EDIT_SCHEDULES.checkAndThrow(ORG_ID, TEST_ORG_ID);
    }
    
    @Test
    public void canTransitionStudies_succeedsForResearcher() { 
        RequestContext.set(new RequestContext.Builder()
                .withCallerRoles(ImmutableSet.of(RESEARCHER)).build());
                
        CAN_TRANSITION_STUDY.checkAndThrow(STUDY_ID, TEST_STUDY_ID);
    }
    
    @Test
    public void canTransitionStudies_succeedsForStudyCoordinator() { 
        RequestContext.set(new RequestContext.Builder()
                .withOrgSponsoredStudies(ImmutableSet.of(TEST_STUDY_ID))
                .withCallerRoles(ImmutableSet.of(STUDY_COORDINATOR)).build());
                
        CAN_TRANSITION_STUDY.checkAndThrow(STUDY_ID, TEST_STUDY_ID);
    }
    
    @Test(expectedExceptions = UnauthorizedException.class)
    public void canEditSchedules_fails() {
        RequestContext.set(new RequestContext.Builder()
                .withOrgSponsoredStudies(ImmutableSet.of("some-other-study"))
                .withCallerRoles(ImmutableSet.of(STUDY_COORDINATOR)).build());
                
        CAN_TRANSITION_STUDY.checkAndThrow(STUDY_ID, TEST_STUDY_ID);
    }

    public void canReadOrg() {
        RequestContext.set(new RequestContext.Builder()
                .withCallerOrgMembership(TEST_ORG_ID).build());
        AuthUtils.CAN_READ_ORG.checkAndThrow(ORG_ID, TEST_ORG_ID);
    }
    
    @Test(expectedExceptions = UnauthorizedException.class)
    public void canReadOrg_fails() { 
        RequestContext.set(new RequestContext.Builder()
                .withCallerOrgMembership("some-other-org").build());
        AuthUtils.CAN_READ_ORG.checkAndThrow(ORG_ID, TEST_ORG_ID);
    }
    
    @Test
    public void canEditOrg() {
        RequestContext.set(new RequestContext.Builder()
                .withCallerRoles(ImmutableSet.of(ORG_ADMIN))
                .withCallerOrgMembership(TEST_ORG_ID).build());
        AuthUtils.CAN_EDIT_ORG.checkAndThrow(ORG_ID, TEST_ORG_ID);
    }
    
    @Test(expectedExceptions = UnauthorizedException.class)
    public void canEditOrg_fails() {
        RequestContext.set(new RequestContext.Builder()
                .withCallerRoles(ImmutableSet.of(STUDY_DESIGNER))
                .withCallerOrgMembership(TEST_ORG_ID).build());
        AuthUtils.CAN_EDIT_ORG.checkAndThrow(ORG_ID, TEST_ORG_ID);
    }
    
    
    @Test
    public void canAccessAccount_nullFails() {
        assertFalse(AuthUtils.canAccessAccount(null));
    }

    @Test
    public void canAccessAccount_succeedsForSelf() {
        Account account = Account.create();
        account.setId(TEST_USER_ID);
        
        RequestContext.set(new RequestContext.Builder().withCallerUserId(TEST_USER_ID).build());

        assertTrue(AuthUtils.canAccessAccount(account));
    }
    
    @Test
    public void canAccessAccount_succeedsForAdmin() {
        Account account = Account.create();
        
        RequestContext.set(new RequestContext.Builder().withCallerRoles(ImmutableSet.of(ADMIN)).build());

        assertTrue(AuthUtils.canAccessAccount(account));
    }
    
    @Test
    public void canAccessAccount_succeedsForWorker() {
        Account account = Account.create();
        
        RequestContext.set(new RequestContext.Builder().withCallerRoles(ImmutableSet.of(WORKER)).build());

        assertTrue(AuthUtils.canAccessAccount(account));
    }
    
    @Test
    public void canAccessAccount_orgAdminSucceedsOnProductionAccountInOrg() {
        Account account = Account.create();
        account.setOrgMembership(TEST_ORG_ID);
        
        RequestContext.set(new RequestContext.Builder()
                .withCallerRoles(ImmutableSet.of(ORG_ADMIN))
                .withCallerOrgMembership(TEST_ORG_ID).build());

        assertTrue(AuthUtils.canAccessAccount(account));
    }
    
    @Test
    public void canAccessAccount_orgAdminFailsOnProductionAccountOtherOrg() {
        Account account = Account.create();
        account.setOrgMembership("other-organization");
        
        RequestContext.set(new RequestContext.Builder()
                .withCallerUserId("id")
                .withCallerRoles(ImmutableSet.of(ORG_ADMIN))
                .withCallerOrgMembership(TEST_ORG_ID).build());

        assertFalse(AuthUtils.canAccessAccount(account));
    }
    
    @Test
    public void canAccessAccount_orgAdminSucceedsOnTextAccountInOrg() {
        Account account = Account.create();
        account.setDataGroups(ImmutableSet.of(TEST_USER_GROUP));
        account.setOrgMembership(TEST_ORG_ID);
        
        RequestContext.set(new RequestContext.Builder()
                .withCallerRoles(ImmutableSet.of(ORG_ADMIN))
                .withCallerOrgMembership(TEST_ORG_ID).build());

        assertTrue(AuthUtils.canAccessAccount(account));
    }
    
    @Test
    public void canAccessAccount_orgAdminFailsOnTestAccountOtherOrg() {
        Account account = Account.create();
        account.setDataGroups(ImmutableSet.of(TEST_USER_GROUP));
        account.setOrgMembership("other-organization");
        
        RequestContext.set(new RequestContext.Builder()
                .withCallerUserId("id")
                .withCallerRoles(ImmutableSet.of(ORG_ADMIN))
                .withCallerOrgMembership(TEST_ORG_ID).build());

        assertFalse(AuthUtils.canAccessAccount(account));
    }
    
    @Test
    public void canAccessAccount_orgAdminFailsOnProdParticipant() {
        Account account = Account.create();
        account.setEnrollments(ImmutableSet.of(Enrollment.create(TEST_APP_ID, TEST_STUDY_ID, TEST_USER_ID)));
        
        RequestContext.set(new RequestContext.Builder()
                .withCallerUserId("id")
                .withOrgSponsoredStudies(ImmutableSet.of(TEST_STUDY_ID))
                .withCallerRoles(ImmutableSet.of(ORG_ADMIN)).build());

        assertFalse(AuthUtils.canAccessAccount(account));
    }
    
    @Test
    public void canAccessAccount_orgAdminFailsOnTestParticipant() {
        Account account = Account.create();
        account.setDataGroups(ImmutableSet.of(TEST_USER_GROUP));
        account.setEnrollments(ImmutableSet.of(Enrollment.create(TEST_APP_ID, TEST_STUDY_ID, TEST_USER_ID)));
        
        RequestContext.set(new RequestContext.Builder()
                .withCallerUserId("id")
                .withOrgSponsoredStudies(ImmutableSet.of(TEST_STUDY_ID))
                .withCallerRoles(ImmutableSet.of(ORG_ADMIN)).build());

        assertFalse(AuthUtils.canAccessAccount(account));
    }
    
    @Test
    public void canAccessAccount_devSucceedsWithTestAccount() {
        Account account = Account.create();
        account.setDataGroups(ImmutableSet.of(TEST_USER_GROUP));
        
        RequestContext.set(new RequestContext.Builder()
                .withCallerUserId("id")
                .withCallerRoles(ImmutableSet.of(DEVELOPER)).build());

        assertTrue(AuthUtils.canAccessAccount(account));
    }
    
    @Test
    public void canAccessAccount_studyDesignerSucceedsWithTestAccount() {
        Account account = getAccountEnrolledIn(TEST_STUDY_ID);
        account.setDataGroups(ImmutableSet.of(TEST_USER_GROUP));
        
        RequestContext.set(new RequestContext.Builder()
                .withCallerUserId("id")
                .withOrgSponsoredStudies(ImmutableSet.of(TEST_STUDY_ID))
                .withCallerRoles(ImmutableSet.of(STUDY_DESIGNER)).build());

        assertTrue(AuthUtils.canAccessAccount(account));
    }
    
    @Test
    public void canAccessAccount_devFailsWithProdAccount() {
        Account account = Account.create();
        
        RequestContext.set(new RequestContext.Builder()
                .withCallerUserId("id")
                .withCallerRoles(ImmutableSet.of(DEVELOPER)).build());

        assertFalse(AuthUtils.canAccessAccount(account));
    }
    
    @Test
    public void canAccessAccount_studyDesignerFailsWithProdAccount() {
        Account account = getAccountEnrolledIn(TEST_STUDY_ID);
        
        RequestContext.set(new RequestContext.Builder()
                .withCallerUserId("id")
                .withOrgSponsoredStudies(ImmutableSet.of(TEST_STUDY_ID))
                .withCallerRoles(ImmutableSet.of(STUDY_DESIGNER)).build());

        assertFalse(AuthUtils.canAccessAccount(account));
    }
    
    @Test
    public void canAccessAccount_researcherSucceedsWithEnrollee() {
        Account account = getAccountEnrolledIn(TEST_STUDY_ID);
        
        RequestContext.set(new RequestContext.Builder()
                .withCallerUserId("id")
                .withCallerRoles(ImmutableSet.of(RESEARCHER)).build());

        assertTrue(AuthUtils.canAccessAccount(account));
    }
    
    @Test
    public void canAccessAccount_studyCoordinatorSucceedsWithEnrollee() {
        Account account = getAccountEnrolledIn(TEST_STUDY_ID);
        
        RequestContext.set(new RequestContext.Builder()
                .withCallerUserId("id")
                .withOrgSponsoredStudies(ImmutableSet.of(TEST_STUDY_ID))
                .withCallerRoles(ImmutableSet.of(STUDY_COORDINATOR)).build());

        assertTrue(AuthUtils.canAccessAccount(account));
    }
    
    @Test
    public void canAccessAccount_researcherSucceedsWithNonEnrollee() {
        Account account = getAccountEnrolledIn("other-study");
        
        RequestContext.set(new RequestContext.Builder()
                .withCallerUserId("id")
                .withCallerRoles(ImmutableSet.of(RESEARCHER)).build());

        assertTrue(AuthUtils.canAccessAccount(account));
    }
    
    @Test
    public void canAccessAccount_studyCoordinatorFailsWithNonEnrollee() {
        Account account = getAccountEnrolledIn("other-study");
        
        RequestContext.set(new RequestContext.Builder()
                .withCallerUserId("id")
                .withCallerRoles(ImmutableSet.of(STUDY_COORDINATOR)).build());

        assertFalse(AuthUtils.canAccessAccount(account));
    }
    
    @Test
    public void canReadStudies_enrolleeSucceeds() {
        RequestContext.set(new RequestContext.Builder()
                .withCallerUserId("id")
                .withCallerEnrolledStudies(ImmutableSet.of("studyA")).build());
        
        assertTrue(AuthUtils.CAN_READ_STUDIES.check(STUDY_ID, "studyA"));
    }
    
    @Test
    public void canReadStudies_studyDesignerSucceeds() {
        RequestContext.set(new RequestContext.Builder()
                .withCallerUserId("id")
                .withCallerRoles(ImmutableSet.of(STUDY_DESIGNER))
                .withOrgSponsoredStudies(ImmutableSet.of("studyA")).build());
        
        assertTrue(AuthUtils.CAN_READ_STUDIES.check(STUDY_ID, "studyA"));
    }
    
    @Test
    public void canReadStudies_studyCoordinatorSucceeds() {
        RequestContext.set(new RequestContext.Builder()
                .withCallerUserId("id")
                .withCallerRoles(ImmutableSet.of(STUDY_COORDINATOR))
                .withOrgSponsoredStudies(ImmutableSet.of("studyA")).build());
        
        assertTrue(AuthUtils.CAN_READ_STUDIES.check(STUDY_ID, "studyA"));
    }
    
    @Test
    public void canReadStudies_studyOrgAdminSucceeds() {
        RequestContext.set(new RequestContext.Builder()
                .withCallerUserId("id")
                .withCallerRoles(ImmutableSet.of(ORG_ADMIN))
                .withOrgSponsoredStudies(ImmutableSet.of("studyA")).build());
        
        assertTrue(AuthUtils.CAN_READ_STUDIES.check(STUDY_ID, "studyA"));
    }

    @Test
    public void canReadStudies_nonMemberAdminFails() {
        RequestContext.set(new RequestContext.Builder()
                .withCallerUserId("id")
                .withCallerRoles(ImmutableSet.of(ORG_ADMIN))
                .withOrgSponsoredStudies(ImmutableSet.of("studyB")).build());
        
        assertFalse(AuthUtils.CAN_READ_STUDIES.check(STUDY_ID, "studyA"));
    }

    @Test
    public void canReadStudies_developerSucceeds() {
        RequestContext.set(new RequestContext.Builder()
                .withCallerUserId("id")
                .withCallerRoles(ImmutableSet.of(DEVELOPER)).build());
        
        assertTrue(AuthUtils.CAN_READ_STUDIES.check(STUDY_ID, "studyA"));
    }
    
    @Test
    public void canReadStudies_researcherSucceeds() {
        RequestContext.set(new RequestContext.Builder()
                .withCallerUserId("id")
                .withCallerRoles(ImmutableSet.of(RESEARCHER)).build());
        
        assertTrue(AuthUtils.CAN_READ_STUDIES.check(STUDY_ID, "studyA"));
    }

    @Test
    public void canReadStudies_fails() {
        RequestContext.set(new RequestContext.Builder()
                .withCallerUserId("id").build());
        
        assertFalse(AuthUtils.CAN_READ_STUDIES.check(STUDY_ID, "studyA"));
    }
    
    private Account getAccountEnrolledIn(String... studyIds) {
        Account account = Account.create();
        account.setId(TEST_USER_ID);
        Set<Enrollment> enrollments = Arrays.asList(studyIds)
                .stream()
                .map(id -> Enrollment.create(TEST_APP_ID, id, TEST_USER_ID))
                .collect(toSet());
        account.setEnrollments(enrollments);
        return account;
    }

    @Test
    public void canEditOtherEnrollments_baseCaseFails() {
        RequestContext.set(new RequestContext.Builder().build());

        assertFalse(CAN_EDIT_OTHER_ENROLLMENTS.check(STUDY_ID, TEST_STUDY_ID, USER_ID, TEST_USER_ID));
    }

    @Test
    public void canEditOtherEnrollments_selfFails() {
        RequestContext.set(new RequestContext.Builder()
                .withCallerUserId(TEST_USER_ID).build());

        assertFalse(CAN_EDIT_OTHER_ENROLLMENTS.check(STUDY_ID, TEST_STUDY_ID, USER_ID, TEST_USER_ID));
    }

    @Test
    public void canEditOtherEnrollments_studyDesignerWithStudyAccessSucceeds() {
        RequestContext.set(new RequestContext.Builder()
                .withCallerRoles(ImmutableSet.of(STUDY_DESIGNER))
                .withOrgSponsoredStudies(ImmutableSet.of(TEST_STUDY_ID))
                .build());

        assertTrue(CAN_EDIT_OTHER_ENROLLMENTS.check(STUDY_ID, TEST_STUDY_ID, USER_ID, TEST_USER_ID));
    }

    @Test
    public void canEditOtherEnrollments_studyDesignerWithoutStudyAccessFails() {
        RequestContext.set(new RequestContext.Builder()
                .withCallerRoles(ImmutableSet.of(STUDY_DESIGNER))
                .build());

        assertFalse(CAN_EDIT_OTHER_ENROLLMENTS.check(STUDY_ID, TEST_STUDY_ID, USER_ID, TEST_USER_ID));
    }

    @Test
    public void canEditOtherEnrollments_studyCoordinatorWithStudyAccessSucceeds() {
        RequestContext.set(new RequestContext.Builder()
                .withCallerRoles(ImmutableSet.of(STUDY_COORDINATOR))
                .withOrgSponsoredStudies(ImmutableSet.of(TEST_STUDY_ID))
                .build());

        assertTrue(CAN_EDIT_OTHER_ENROLLMENTS.check(STUDY_ID, TEST_STUDY_ID, USER_ID, TEST_USER_ID));
    }

    @Test
    public void canEditOtherEnrollments_studyCoordinatorWithoutStudyAccessFails() {
        RequestContext.set(new RequestContext.Builder()
                .withCallerRoles(ImmutableSet.of(STUDY_COORDINATOR))
                .build());

        assertFalse(CAN_EDIT_OTHER_ENROLLMENTS.check(STUDY_ID, TEST_STUDY_ID, USER_ID, TEST_USER_ID));
    }

    @Test
    public void canEditOtherEnrollments_adminSucceeds() {
        RequestContext.set(new RequestContext.Builder()
                .withCallerRoles(ImmutableSet.of(ADMIN))
                .build());

        assertTrue(CAN_EDIT_OTHER_ENROLLMENTS.check(STUDY_ID, TEST_STUDY_ID, USER_ID, TEST_USER_ID));
    }

    @Test
    public void canEditOtherEnrollments_researcherSucceeds() {
        RequestContext.set(new RequestContext.Builder()
                .withCallerRoles(ImmutableSet.of(RESEARCHER))
                .build());

        assertTrue(CAN_EDIT_OTHER_ENROLLMENTS.check(STUDY_ID, TEST_STUDY_ID, USER_ID, TEST_USER_ID));
    }

    @Test
    public void canEditOtherEnrollments_developerSucceeds() {
        RequestContext.set(new RequestContext.Builder()
                .withCallerRoles(ImmutableSet.of(DEVELOPER))
                .build());

        assertTrue(CAN_EDIT_OTHER_ENROLLMENTS.check(STUDY_ID, TEST_STUDY_ID, USER_ID, TEST_USER_ID));
    }
}