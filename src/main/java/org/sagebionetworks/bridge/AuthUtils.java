package org.sagebionetworks.bridge;

import static org.sagebionetworks.bridge.Roles.ADMIN;
import static org.sagebionetworks.bridge.Roles.DEVELOPER;
import static org.sagebionetworks.bridge.Roles.ORG_ADMIN;
import static org.sagebionetworks.bridge.Roles.RESEARCHER;
import static org.sagebionetworks.bridge.Roles.STUDY_COORDINATOR;
import static org.sagebionetworks.bridge.Roles.SUPERADMIN;
import static org.sagebionetworks.bridge.Roles.WORKER;

import java.util.Set;

/**
 * Utility methods to check caller authorization in service methods. Note that in all of these checks, 
 * ADMIN and SUPERADMIN roles will always pass and are implicit in the naming.
 */
public class AuthUtils {

    public static final AuthEvaluator IS_ADMIN = new AuthEvaluator().hasAnyRole(ADMIN);
    
    /**
     * Is the account a member of this organization? Note that this does not verify
     * the organization is in the caller's app...this can only be done by trying to load the 
     * organization with this ID.
     * 
     * @throws UnauthorizedException
     */
    public static final AuthEvaluator IS_ORG_MEMBER = new AuthEvaluator().isInOrg().or()
            .hasAnyRole(ADMIN);
    
    /**
     * Is the account an organization admin in the target organization?
     * 
     * @throws UnauthorizedException
     */
    public static final AuthEvaluator IS_ORGADMIN = new AuthEvaluator()
            .isInOrg().hasAnyRole(ORG_ADMIN).or()
            .hasAnyRole(ADMIN);
    
    public static final AuthEvaluator IS_SELF_AND_ORG_MEMBER_OR_ORGADMIN = new AuthEvaluator()
            .isInOrg().isSelf().or()
            .isInOrg().hasAnyRole(ORG_ADMIN).or()
            .hasAnyRole(ADMIN);
    
    /**
     * Does this caller have access to the study? 
     * 
     * @throws UnauthorizedException
     */
    public static final AuthEvaluator IS_STUDY_TEAM_OR_WORKER = new AuthEvaluator().canAccessStudy().or()
            .hasAnyRole(WORKER, ADMIN).or()
            .callerConsideredGlobal();
    
    /**
     * Is the caller 1) referring to their own account, 2) a member of an 
     * organization that can access the target study, or 3) a worker account? 
     */
    public static final AuthEvaluator IS_SELF_STUDY_TEAM_OR_WORKER = new AuthEvaluator().isSelf().or()
            .canAccessStudy().or()
            .hasAnyRole(WORKER, ADMIN).or()
            .callerConsideredGlobal();
    
    /**
     * Is the caller 1) referring to their own account, 2) and organization admin, or 3) a worker account? 
     */
    public static final AuthEvaluator IS_SELF_ORGADMIN_OR_WORKER = new AuthEvaluator().isSelf().or()
            .isInOrg().hasAnyRole(ORG_ADMIN).or()
            .hasAnyRole(WORKER, ADMIN).or()
            .callerConsideredGlobal();
    
    /**
     * Is the caller operating on their own account, or a person with the researcher role and access
     * to the indicated study? 
     */
    public static final AuthEvaluator IS_SELF_COORD_OR_RESEARCHER = new AuthEvaluator().isSelf().or()
            .canAccessStudy().hasAnyRole(STUDY_COORDINATOR).or()
            .hasAnyRole(RESEARCHER, ADMIN);

    public static final AuthEvaluator IS_COORD = new AuthEvaluator()
            .canAccessStudy().hasAnyRole(STUDY_COORDINATOR).or()
            .hasAnyRole(ADMIN);

    public static final AuthEvaluator IS_COORD_DEV_OR_RESEARCHER = new AuthEvaluator()
            .canAccessStudy().hasAnyRole(STUDY_COORDINATOR).or()
            .hasAnyRole(DEVELOPER, RESEARCHER, ADMIN);
    
    public static final AuthEvaluator IS_COORD_OR_RESEARCHER = new AuthEvaluator()
            .canAccessStudy().hasAnyRole(STUDY_COORDINATOR).or()
            .hasAnyRole(RESEARCHER, ADMIN);
    
    /**
     * This is only called from the ParticipantController and the ParticipantReportController, where 
     * the calls are not scoped, and instead, the search filters out accounts that are not visible to 
     * the caller. We are probably keeping this API, so will need an alternative set of study-specific 
     * roles for multi-study apps.
     * 
     * @throws UnauthorizedException
     */
    public static final AuthEvaluator IS_SELF_OR_RESEARCHER = new AuthEvaluator().isSelf().or()
            .hasAnyRole(RESEARCHER, ADMIN);
    
    /**
     * The caller must be a member of an organization expressed in the shared organization ID format, 
     * or "appId:orgId" (which is used in shared assessments so that organization IDs do not collide
     * between applications). 
     * 
     * @throws UnauthorizedException
     */
    public static final AuthEvaluator IS_ORG_MEMBER_IN_APP = new AuthEvaluator().isSharedOwner().or()
            .hasAnyRole(ADMIN);
    
    /**
     * Is the caller in the required role? Superadmins always pass this check, but not admins.
     */
    public static boolean isInRole(Set<Roles> callerRoles, Roles requiredRole) {
        return (callerRoles != null && requiredRole != null && 
            (callerRoles.contains(SUPERADMIN) || callerRoles.contains(requiredRole)));
    }
    
    /**
     * Is the caller in the required role? Superadmins always pass this check, but not admin.
     */
    public static boolean isInRole(Set<Roles> callerRoles, Set<Roles> requiredRoles) {
        return callerRoles != null && requiredRoles != null && 
                requiredRoles.stream().anyMatch(role -> isInRole(callerRoles, role));
    }
}