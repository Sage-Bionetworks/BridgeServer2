package org.sagebionetworks.bridge;

import static com.google.common.base.Preconditions.checkNotNull;
import static org.sagebionetworks.bridge.BridgeConstants.CALLER_NOT_MEMBER_ERROR;
import static org.sagebionetworks.bridge.Roles.ADMIN;
import static org.sagebionetworks.bridge.Roles.RESEARCHER;

import java.util.Set;

import com.google.common.collect.ImmutableSet;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.sagebionetworks.bridge.exceptions.UnauthorizedException;

public class AuthUtils {
    private static final Logger LOG = LoggerFactory.getLogger(AuthUtils.class);

    public static boolean checkResearcherOrAdmin(String studyId) {
        return isInRoles(studyId, ADMIN, RESEARCHER);
    }
    
    public static void checkResearcherOrAdminAndThrow(String studyId) {
        if (!checkResearcherOrAdmin(studyId)) {
            throw new UnauthorizedException();
        }
    }
    
    public static boolean checkSelfResearcherOrAdmin(String studyId, String targetUserId) {
        return isSelf(targetUserId) || isInRoles(studyId, ADMIN, RESEARCHER);
    }
    
    public static void checkSelfResearcherOrAdminAndThrow(String studyId, String targetUserId) {
        if (!checkSelfResearcherOrAdmin(studyId, targetUserId)) {
            throw new UnauthorizedException();
        }
    }
    
    public static boolean checkOrgMembership(String targetOrgId) {
        return isInOrganization(targetOrgId);
    }
    
    public static void checkOrgMembershipAndThrow(String targetOrgId) {
        if (!checkOrgMembership(targetOrgId)) {
            throw new UnauthorizedException("Caller is not a member of " + targetOrgId);    
        }
    }
    
    /**
     * If the caller is an app admin or superadmin, they can set any organization. Otherwise, the 
     * organization must match the caller's organization. We do not need to validate the org ID since 
     * it was validated when it was set as an organizational relationship on the account. 
     */
    public static boolean checkAssessmentOwnership(String appId, String ownerId) {
        return isInOrganization(ownerId);
    }

    public static void checkAssessmentOwnershipAndThrow(String appId, String ownerId) {
        if (!checkAssessmentOwnership(appId, ownerId)) {
            throw new UnauthorizedException(CALLER_NOT_MEMBER_ERROR);
        }
    }

    /**
     * The same rules apply as checkOwnership, however you are examining the caller against a compound
     * value stored in the assessment's originId. The call succeeds if you're a superadmin (there are 
     * no shared app admins), or if the caller is in the app and organization that owns the shared 
     * assessment. 
     */
    public static void checkSharedAssessmentOwnership(String callerAppId, String guid, String ownerId) {
        checkNotNull(callerAppId);
        checkNotNull(guid);
        checkNotNull(ownerId);

        RequestContext context = RequestContext.get();
        if (context.isInRole(ImmutableSet.of(ADMIN))) {
            return;
        }
        String[] parts = ownerId.split(":", 2);
        // This happens in tests, we expect it to never happens in production. So log if it does.
        if (parts.length != 2) {
            LOG.error("Could not parse shared assessment ownerID, guid=" + guid + ", ownerId=" + ownerId);
            throw new UnauthorizedException(CALLER_NOT_MEMBER_ERROR);
        }
        String originAppId = parts[0];
        String originOrgId = parts[1];
        String callerOrgMembership = context.getCallerOrgMembership();
        if (originAppId.equals(callerAppId) && originOrgId.equals(callerOrgMembership)) {
            return;
        }
        throw new UnauthorizedException(CALLER_NOT_MEMBER_ERROR);
    }
    
    /**
     * If the user is an admin or a superadmin, this returns true. For other roles, the 
     * caller must be in one of the roles, and they must be a member of an organization 
     * that gives them access to the study.
     */
    private static final boolean isInRoles(String studyId, Roles... roles) {
        Set<Roles> roleSet = ImmutableSet.copyOf(roles);
        RequestContext context = RequestContext.get();
        if (roleSet.contains(ADMIN) && context.isInRole(ADMIN)) {
            return true;
        }
        Set<String> sponsoredStudies = context.getOrgSponsoredStudies();
        boolean canAccessStudy = sponsoredStudies.isEmpty() || studyId == null 
                || sponsoredStudies.contains(studyId);
        return (canAccessStudy && context.isInRole(roleSet));
    }
    
    private static final boolean isInOrganization(String orgId) {
        RequestContext context = RequestContext.get();
        return context.isInRole(ADMIN) || orgId.equals(context.getCallerOrgMembership());
    }
    
    private static final boolean isSelf(String userId) {
        return userId.equals(RequestContext.get().getCallerUserId());
    }
}
