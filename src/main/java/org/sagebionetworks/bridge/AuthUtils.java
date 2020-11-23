package org.sagebionetworks.bridge;

import static com.google.common.base.Preconditions.checkNotNull;
import static org.sagebionetworks.bridge.Roles.ADMIN;
import static org.sagebionetworks.bridge.Roles.ORG_ADMIN;
import static org.sagebionetworks.bridge.Roles.RESEARCHER;
import static org.sagebionetworks.bridge.Roles.SUPERADMIN;
import static org.sagebionetworks.bridge.Roles.WORKER;

import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.sagebionetworks.bridge.exceptions.UnauthorizedException;

/**
 * Utility methods to check caller authorization in service methods. Note that in all of these checks, 
 * ADMIN and SUPERADMIN roles will always pass and are implicit.  
 */
public class AuthUtils {
    private static final Logger LOG = LoggerFactory.getLogger(AuthUtils.class);
    
    private static final AuthEvaluator ORG_MEMBER = new AuthEvaluator().isInApp().isInOrg().or()
            .isInApp().hasAnyRole(ADMIN);
    
    private static final AuthEvaluator ORG_ADMINISTRATOR = new AuthEvaluator().isInApp().hasAnyRole(ADMIN).or()
            .isInApp().isInOrg().hasAnyRole(ORG_ADMIN);
    
    // Note that these tests do not include a check for appId, and so there may be security holes where consumers
    // are picking their own IDs. These need to be updated as well. 
    
    private static final AuthEvaluator STUDY_TEAM_MEMBER_OR_WORKER = new AuthEvaluator().canAccessStudy().or()
            .hasAnyRole(WORKER, ADMIN).or()
            .callerConsideredGlobal();
    
    private static final AuthEvaluator SELF_STUDY_TEAM_MEMBER_OR_WORKER = new AuthEvaluator().isSelf().or()
            .canAccessStudy().or()
            .hasAnyRole(WORKER, ADMIN).or()
            .callerConsideredGlobal();
    
    private static final AuthEvaluator SELF_WORKER_OR_ORG_ADMIN = new AuthEvaluator().isSelf().or()
            .hasAnyRole(WORKER, ADMIN).or()
            .hasAnyRole(ORG_ADMIN).isInOrg().or()
            .callerConsideredGlobal();
    
    private static final AuthEvaluator SELF_OR_STUDY_RESEARCHER = new AuthEvaluator().isSelf().or()
            .canAccessStudy().hasAnyRole(RESEARCHER).or()
            .hasAnyRole(ADMIN);
    
    private static final AuthEvaluator STUDY_RESEARCHER = new AuthEvaluator().canAccessStudy().hasAnyRole(RESEARCHER)
            .or().hasAnyRole(ADMIN);
    
    private static final AuthEvaluator SELF_OR_RESEARCHER = new AuthEvaluator().isSelf().or()
            .hasAnyRole(RESEARCHER, ADMIN);
    
    private static final AuthEvaluator ORG_MEMBER_OF_SHARED_OWNER = new AuthEvaluator().isInApp().isInOrg().or()
            .hasAnyRole(ADMIN);
    
    /**
     * Is the caller operating on their own account, or a person with the researcher role and access
     * to the indicated study? 
     * 
     * @throws UnauthorizedException
     */
    public static void checkSelfOrStudyResearcher(String userId, String studyId) {
        SELF_OR_STUDY_RESEARCHER.checkAndThrow("userId", userId, "studyId", studyId);
    }
    
    public static void checkStudyResearcher(String studyId) {
        STUDY_RESEARCHER.checkAndThrow("studyId", studyId);
    }
    
    /**
     * This is only called from the ParticipantController and the ParticipantReportController, where 
     * the calls are not scoped, and instead, the search filters out accounts that are not visible to 
     * the caller. We are probably keeping this API, so will need an alternative set of study-specific 
     * roles for multi-study apps.
     * 
     * @throws UnauthorizedException
     */
    public static void checkSelfOrResearcher(String targetUserId) {
        SELF_OR_RESEARCHER.checkAndThrow("userId", targetUserId);
    }
    
    /**
     * Is the account a member of this organization? Note that this does not verify
     * the organization is in the caller's app...this can only be done by trying to load the 
     * organization with this ID.
     * 
     * @throws UnauthorizedException
     */
    public static void checkOrgMember(String appId, String orgId) {
        ORG_MEMBER.checkAndThrow("appId", appId, "orgId", orgId);
    }
    
    /**
     * Is the account an organization admin in the target organization?
     * 
     * @throws UnauthorizedException
     */
    public static void checkOrgAdmin(String appId, String orgId) {
        ORG_ADMINISTRATOR.checkAndThrow("appId", appId, "orgId", orgId);    
    }
    
    /**
     * The same rules apply as checkOrgMember, however you are examining the caller against a compound
     * value stored in the assessment's originId. The call succeeds if the caller is in the app and 
     * organization that owns the shared assessment. 
     * 
     * @throws UnauthorizedException
     */
    public static void checkOrgMemberOfSharedAssessmentOwner(String callerAppId, String guid, String ownerId) {
        checkNotNull(callerAppId);
        checkNotNull(guid);
        checkNotNull(ownerId);
        
        String[] parts = ownerId.split(":", 2);
        // This happens in tests, we expect it to never happens in production. So log if it does.
        if (parts.length != 2) {
            LOG.error("Could not parse shared assessment ownerID, guid=" + guid + ", ownerId=" + ownerId);
            throw new UnauthorizedException();
        }
        String originAppId = parts[0];
        String originOrgId = parts[1];
        
        ORG_MEMBER_OF_SHARED_OWNER.checkAndThrow("appId", originAppId, "orgId", originOrgId);
    }
    
    /**
     * Does this caller have access to the study? 
     * 
     * @throws UnauthorizedException
     */
    public static void checkStudyTeamMemberOrWorker(String studyId) {
        STUDY_TEAM_MEMBER_OR_WORKER.checkAndThrow("studyId", studyId);
    }
    
    /**
     * Is the account a member of this organization? Note that this does not verify
     * the organization is in the caller's app...this can only be done by trying to load the 
     * organization with this ID. 
     */
    public static final boolean isOrgMember(String appId, String orgId) {
        return ORG_MEMBER.check("appId", appId, "orgId", orgId);
    }
    
    /**
     * Is the account an organization admin in the target organization?
     */
    public static boolean isOrgAdmin(String orgId) {
        return ORG_ADMINISTRATOR.check("orgId", orgId);    
    }
    
    /**
     * Does this caller have access to the study? 
     */
    public static final boolean isStudyTeamMemberOrWorker(String studyId) {
        return STUDY_TEAM_MEMBER_OR_WORKER.check("studyId", studyId);
    }
    
    /**
     * Is the caller 1) referring to their own account, 2) a member of an 
     * organization that can access the target study, or 3) a worker account? 
     */
    public static final boolean isSelfOrStudyTeamMemberOrWorker(String studyId, String userId) {
        return SELF_STUDY_TEAM_MEMBER_OR_WORKER.check("studyId", studyId, "userId", userId);
    }
    
    /**
     * Is the caller 1) referring to their own account, or 2) a worker account? 
     */
    public static final boolean isSelfWorkerOrOrgAdmin(String orgId, String userId) {
        return SELF_WORKER_OR_ORG_ADMIN.check("orgId", orgId, "userId", userId);
    }
    
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