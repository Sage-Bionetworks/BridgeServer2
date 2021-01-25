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
 * Utility methods to check caller authorization in service methods. Given the way the code and the 
 * authorization rules are written, ADMIN and SUPERADMIN roles will currently always pass.
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
            .hasAnyRole(ORG_ADMIN, STUDY_COORDINATOR).hasNoRole(DEVELOPER, RESEARCHER, ADMIN, WORKER);

    /**
     * Is the caller an admin? 
     */
    public static final AuthEvaluator IS_ADMIN = new AuthEvaluator().hasAnyRole(ADMIN);
    
    /**
     * Is caller a member of the target organization? This does not verify the orgId itself...
     * this can only be done by trying to load the organization with this ID.
     */
    public static final AuthEvaluator IS_ORG_MEMBER = new AuthEvaluator().isInOrg().or()
            .hasAnyRole(ADMIN);
    
    /**
     * Is the caller an organization admin of the target organization?
     */
    public static final AuthEvaluator IS_ORGADMIN = new AuthEvaluator()
            .isInOrg().hasAnyRole(ORG_ADMIN).or()
            .hasAnyRole(ADMIN);

    /**
     * Is the caller operating on themselves and a member of the target organization, or an 
     * organization admin of the target organization? This allows a person to call many of
     * the same APIs an administrator can call on their behalf.
     */
    public static final AuthEvaluator IS_SELF_AND_ORG_MEMBER_OR_ORGADMIN = new AuthEvaluator()
            .isInOrg().isSelf().or()
            .isInOrg().hasAnyRole(ORG_ADMIN).or()
            .hasAnyRole(ADMIN);
    
    /**
     * Does this caller have access to the study, or is the caller a worker? 
     */
    public static final AuthEvaluator IS_STUDY_TEAM_OR_WORKER = new AuthEvaluator().canAccessStudy().or()
            .hasAnyRole(WORKER, ADMIN).or()
            .callerConsideredGlobal();
    
    /**
     * Is the caller referring to their account, do they have access to the study, or are they a worker?
     * (NOTE: this is suspiciously broad).
     */
    public static final AuthEvaluator IS_SELF_STUDY_TEAM_OR_WORKER = new AuthEvaluator().isSelf().or()
            .canAccessStudy().or()
            .hasAnyRole(WORKER, ADMIN).or()
            .callerConsideredGlobal();
    
    /**
     * Is the caller referring to their own account, an organization admin, or a worker account? 
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

    /**
     * Is the caller a study coordinator?
     */
    public static final AuthEvaluator IS_COORD = new AuthEvaluator()
            .canAccessStudy().hasAnyRole(STUDY_COORDINATOR).or()
            .hasAnyRole(ADMIN);

    /**
     * Is the caller a study coordinator, researcher, or developer?
     */
    public static final AuthEvaluator IS_COORD_DEV_OR_RESEARCHER = new AuthEvaluator()
            .canAccessStudy().hasAnyRole(STUDY_COORDINATOR).or()
            .hasAnyRole(DEVELOPER, RESEARCHER, ADMIN);
    
    public static final AuthEvaluator IS_COORD_OR_DEV = new AuthEvaluator()
            .canAccessStudy().hasAnyRole(STUDY_COORDINATOR).or()
            .hasAnyRole(DEVELOPER, ADMIN);
    
    /**
     * Is the caller a study coordinator or researcher?
     */
    public static final AuthEvaluator IS_COORD_OR_RESEARCHER = new AuthEvaluator()
            .canAccessStudy().hasAnyRole(STUDY_COORDINATOR).or()
            .hasAnyRole(RESEARCHER, ADMIN);
    
    /**
     * Is the caller operating on their own account, or a researcher?
     * 
     */
    public static final AuthEvaluator IS_SELF_OR_RESEARCHER = new AuthEvaluator().isSelf().or()
            .hasAnyRole(RESEARCHER, ADMIN);
    
    /**
     * The caller must be a member of an organization expressed in the shared organization ID format, 
     * or "appId:orgId" (which is used in shared assessments so that organization IDs do not collide
     * between applications). 
     */
    public static final AuthEvaluator IS_ORG_MEMBER_IN_APP = new AuthEvaluator().isSharedOwner().or()
            .hasAnyRole(ADMIN);

    /**
     * Is the caller a study coordinator or an organization admin?
     */
    public static final AuthEvaluator IS_COORD_OR_ORGADMIN = new AuthEvaluator()
            .canAccessStudy().hasAnyRole(STUDY_COORDINATOR, ORG_ADMIN).or()
            .hasAnyRole(ADMIN);
    
    /**
     * Is the caller in the required role? Superadmins always pass this check, but admins 
     * have to be expressed in the rules (which is weird and might change).
     */
    public static boolean isInRole(Set<Roles> callerRoles, Roles requiredRole) {
        return (callerRoles != null && requiredRole != null && 
            (callerRoles.contains(SUPERADMIN) || callerRoles.contains(requiredRole)));
    }
    
    /**
     * Is the caller in the required role? Superadmins always pass this check, but admins 
     * have to be expressed in the rules (which is weird and might change).
     */
    public static boolean isInRole(Set<Roles> callerRoles, Set<Roles> requiredRoles) {
        return callerRoles != null && requiredRoles != null && 
                requiredRoles.stream().anyMatch(role -> isInRole(callerRoles, role));
    }
}