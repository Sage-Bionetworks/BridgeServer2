package org.sagebionetworks.bridge;

import static org.sagebionetworks.bridge.Roles.ADMIN;
import static org.sagebionetworks.bridge.Roles.DEVELOPER;
import static org.sagebionetworks.bridge.Roles.ORG_ADMIN;
import static org.sagebionetworks.bridge.Roles.RESEARCHER;
import static org.sagebionetworks.bridge.Roles.STUDY_COORDINATOR;
import static org.sagebionetworks.bridge.Roles.STUDY_DESIGNER;
import static org.sagebionetworks.bridge.Roles.SUPERADMIN;
import static org.sagebionetworks.bridge.Roles.WORKER;

import java.util.Set;

/**
 * Utility methods to check caller authorization in service methods. Given the way the code and the 
 * authorization rules are written, ADMIN and SUPERADMIN roles will currently always pass. They
 * are tested differently because there are some cases where APIs can only be used by superadmins, 
 * so event app-scoped admins have one or two API limitations that must be declared in rules and 
 * not special-cased in the `inInRole` methods.
 * 
 * All methods throw UnauthorizedException if they fail.
 */
public class AuthUtils {
    
    /**
     * Is this scoped to specific studies? It should have one of the study-scoped
     * roles, and no roles that are app scoped that we would allow wider latitude
     * when using the APIs.
     */
    public static final AuthEvaluator CAN_READ_ORG_SPONSORED_STUDIES = new AuthEvaluator()
            .hasAnyRole(ORG_ADMIN, STUDY_DESIGNER, STUDY_COORDINATOR).hasNoRole(DEVELOPER, RESEARCHER, ADMIN, WORKER);

    /**
     * Can the caller edit assessments? Must be a study designer in the organization that 
     * owns the assessment, or a developer.
     */
    public static final AuthEvaluator CAN_EDIT_ASSESSMENTS = new AuthEvaluator()
            .isInOrg().hasAnyRole(STUDY_DESIGNER).or()
            .hasAnyRole(DEVELOPER, ADMIN);

    /**
     * Can the caller and/remove organization members? Must be the organizations's admin. Note 
     * that this check is currently also used for sponsors...which are not members.
     */
    public static final AuthEvaluator CAN_READ_MEMBERS = new AuthEvaluator()
            .isInOrg().or().hasAnyRole(ADMIN);

    /**
     * Can the caller and/remove organization members? Must be the organizations's admin. Note 
     * that this check is currently also used for sponsors...which are not members.
     */
    public static final AuthEvaluator CAN_EDIT_MEMBERS = new AuthEvaluator()
            .isInOrg().hasAnyRole(ORG_ADMIN).or()
            .hasAnyRole(ADMIN);

    /**
     * Can the caller and/remove organization members? Must be the organizations's admin. Note 
     * that this check is currently also used for sponsors...which are not members.
     */
    public static final AuthEvaluator CAN_READ_ORG = new AuthEvaluator()
            .isInOrg().or()
            .hasAnyRole(ADMIN);

    /**
     * Can the caller and/remove organization members? Must be the organizations's admin. Note 
     * that this check is currently also used for sponsors...which are not members.
     */
    public static final AuthEvaluator CAN_EDIT_ORG = new AuthEvaluator()
            .isInOrg().hasAnyRole(ORG_ADMIN).or()
            .hasAnyRole(ADMIN);
    
    /**
     * Can the caller edit accounts? For the APIs to work with administrative accounts, 
     * the caller must be operating on self (and in the correct organization), or an 
     * administrator of the organization.
     */
    public static final AuthEvaluator CAN_EDIT_ACCOUNTS = new AuthEvaluator()
            .isInOrg().isSelf().or()
            .isInOrg().hasAnyRole(ORG_ADMIN).or()
            .hasAnyRole(ADMIN);
    
    /**
     * Can the caller see that the account is enrolled in a study? Must be reading one's
     * own account, must have access to the study, or be a worker. 
     */
    public static final AuthEvaluator CAN_READ_STUDY_ASSOCIATIONS = new AuthEvaluator().isSelf().or()
            .canAccessStudy().hasAnyRole(STUDY_COORDINATOR).or()
            .hasAnyRole(RESEARCHER, WORKER, ADMIN);
    
    /**
     * Can the caller view participants (through the original Participants API)? Must be reading self,
     * be an organization admin, or be a worker.
     */
    public static final AuthEvaluator CAN_READ_PARTICIPANTS = new AuthEvaluator().isSelf().or()
            .isInOrg().hasAnyRole(ORG_ADMIN).or()
            .canAccessStudy().hasAnyRole(STUDY_COORDINATOR).or()
            .hasAnyRole(RESEARCHER, WORKER, ADMIN);
    
    /**
     * Can the caller edit participants? Must be editing one’s own account, or be a study coordinator
     * with access to the participant’s study, or be a researcher, or a worker.
     */
    public static final AuthEvaluator CAN_EDIT_PARTICIPANTS = new AuthEvaluator().isSelf().or()
            .canAccessStudy().hasAnyRole(STUDY_COORDINATOR).or()
            .hasAnyRole(RESEARCHER, WORKER, ADMIN);
    
    /**
     * Can the caller read participant reports? 
     */
    public static final AuthEvaluator CAN_READ_PARTICIPANT_REPORTS = new AuthEvaluator().isSelf().or()
            .canAccessStudy().hasAnyRole(STUDY_COORDINATOR).or()
            .hasAnyRole(RESEARCHER, WORKER, ADMIN);
    
    /**
     * Can the caller read study reports?
     */
    public static final AuthEvaluator CAN_READ_STUDY_REPORTS = new AuthEvaluator()
            .canAccessStudy().or()
            .hasAnyRole(DEVELOPER, RESEARCHER, WORKER, ADMIN);
    
    /**
     * Can the caller enroll or withdraw participants from a study? Must be enrolling self, or 
     * be a study coordinator with access to the study involved, or be a researcher. 
     */
    public static final AuthEvaluator CAN_EDIT_ENROLLMENTS = new AuthEvaluator().isSelf().or()
            .canAccessStudy().hasAnyRole(STUDY_COORDINATOR).or()
            .hasAnyRole(RESEARCHER, ADMIN);

    /**
     * Can the caller view external IDs? Must be a study coordinator, developer, or researcher
     * (external IDs are pretty lax because in theory, they are not identifying).
     */
    public static final AuthEvaluator CAN_READ_EXTERNAL_IDS = new AuthEvaluator()
            .canAccessStudy().hasAnyRole(STUDY_COORDINATOR, STUDY_DESIGNER).or()
            .hasAnyRole(DEVELOPER, RESEARCHER, ADMIN);

    /**
     * Can the caller view Studies? Must be associated to the organization that owns the 
     * study.
     */
    public static final AuthEvaluator CAN_READ_STUDIES = new AuthEvaluator()
            .isEnrolledInStudy().or()
            .canAccessStudy().hasAnyRole(STUDY_COORDINATOR, STUDY_DESIGNER, ORG_ADMIN).or()
            .hasAnyRole(DEVELOPER, ADMIN);
    
    /**
     * Can the caller edit studies? Caller must be a study coordinator, or a developer.
     */
    public static final AuthEvaluator CAN_UPDATE_STUDIES = new AuthEvaluator()
            .canAccessStudy().hasAnyRole(STUDY_DESIGNER).or()
            .hasAnyRole(DEVELOPER, ADMIN);
    
    /**
     * Can the caller edit study participants (these are participants via the newer, study-scoped
     * participant APIs). Must be a study coordinator with access to the study, or a researcher. 
     */
    public static final AuthEvaluator CAN_EDIT_STUDY_PARTICIPANTS = new AuthEvaluator()
            .isEnrolledInStudy().isSelf().or()
            .canAccessStudy().hasAnyRole(STUDY_COORDINATOR).or()
            .hasAnyRole(RESEARCHER, ADMIN);
    
    /**
     * Can the caller import a shared assessment under the supplied organization? Yes if
     * the caller is a developer or admin, or if the caller is a study designer assigning
     * the assessment to their own organization. Note that this expects an orgId to be 
     * supplied, not a compound ownerId (appId + orgId), because it is verifying the final
     * assignment of the supplied orgId.
     */
    public static final AuthEvaluator CAN_IMPORT_SHARED_ASSESSMENTS = new AuthEvaluator()
            .isInOrg().hasAnyRole(STUDY_DESIGNER).or()
            .hasAnyRole(DEVELOPER, ADMIN);

    /**
     * Can the caller edit shared assessments? The caller must be a member of an organization 
     * expressed in the shared organization ID format, or "appId:orgId" (which is used in 
     * shared assessments so that organization IDs do not collide between applications). 
     */
    public static final AuthEvaluator CAN_EDIT_SHARED_ASSESSMENTS = new AuthEvaluator()
            .isSharedOwner().hasAnyRole(STUDY_DESIGNER).or()
            .hasAnyRole(DEVELOPER, ADMIN);

    /**
     * Can the caller read the schedules? They must be enrolled in the study, a study-scoped
     * role that can view schedules, or a developer. Note that when schedules are used in 
     * studies, additional people will have read access, but this hasn't been implemented
     * yet. 
     */
    public static final AuthEvaluator CAN_READ_SCHEDULES = new AuthEvaluator()
            .isInOrg().or()
            .isEnrolledInStudy().or()
            .hasAnyRole(DEVELOPER, ADMIN);

    /**
     * Study designers can create schedules without reference to their organization since
     * the schedule will just be in their organization.
     */
    public static final AuthEvaluator CAN_CREATE_SCHEDULES = new AuthEvaluator()
            .hasAnyRole(DEVELOPER, STUDY_DESIGNER, ADMIN);
    
    /**
     * Can the caller edit the schedules? They must be a study-scoped role that can view 
     * schedules, or a developer.
     */
    public static final AuthEvaluator CAN_EDIT_SCHEDULES = new AuthEvaluator()
            .isInOrg().hasAnyRole(STUDY_DESIGNER).or()
            .hasAnyRole(DEVELOPER, ADMIN);
    
    /**
     * Can the caller read and edit adherence data for a given user in a given study?
     * They must be a caller operating on their own account, or a study coordinator
     * for the study, or a researcher.
     */
    public static final AuthEvaluator CAN_ACCESS_ADHERENCE_DATA = new AuthEvaluator()
            .isSelf().isEnrolledInStudy().or()
            .canAccessStudy().hasAnyRole(STUDY_COORDINATOR).or()
            .hasAnyRole(RESEARCHER, ADMIN);
    
    /**
     * Is the caller in the provided role? Superadmins always pass this test.
     */
    public static boolean isInRole(Set<Roles> callerRoles, Roles requiredRole) {
        return callerRoles != null && requiredRole != null && 
                (callerRoles.contains(SUPERADMIN) || callerRoles.contains(requiredRole));
    }
    
    /**
     * Is the caller in any of the provided roles? Superadmins always pass this test.
     */
    public static boolean isInRole(Set<Roles> callerRoles, Set<Roles> requiredRoles) {
        return callerRoles != null && requiredRoles != null && 
                requiredRoles.stream().anyMatch(role -> isInRole(callerRoles, role));
    }
}