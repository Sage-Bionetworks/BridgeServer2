package org.sagebionetworks.bridge;

import static com.google.common.base.Preconditions.checkNotNull;
import static org.sagebionetworks.bridge.BridgeConstants.CALLER_NOT_MEMBER_ERROR;
import static org.sagebionetworks.bridge.Roles.ADMIN;
import static org.sagebionetworks.bridge.Roles.RESEARCHER;
import static org.sagebionetworks.bridge.Roles.SUPERADMIN;

import com.google.common.collect.ImmutableSet;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.sagebionetworks.bridge.exceptions.UnauthorizedException;
import org.sagebionetworks.bridge.models.accounts.UserSession;

public class AuthUtils {
    private static final Logger LOG = LoggerFactory.getLogger(AuthUtils.class);
    
    public static boolean checkSelfOrResearcher(String targetUserId) {
        RequestContext context = BridgeUtils.getRequestContext();
        String callerUserId = context.getCallerUserId();
        
        return context.isInRole(RESEARCHER) || targetUserId.equals(callerUserId);
    }
    
    public static void checkSelfOrResearcherAndThrow(String targetUserId) {
        if (!checkSelfOrResearcher(targetUserId)) {
            throw new UnauthorizedException();
        }
    }
    
    /**
     * Unless you are a superadmin, you can only list the members of your own organization.
     */
    public static boolean checkOrgMembership(String targetOrgId) {
        RequestContext context = BridgeUtils.getRequestContext();
        String callerOrgMembership = context.getCallerOrgMembership();
        
        return context.isInRole(SUPERADMIN) || targetOrgId.equals(callerOrgMembership);
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
    public static void checkAssessmentOwnership(String appId, String ownerId) {
        checkNotNull(appId);
        checkNotNull(ownerId);
        
        RequestContext rc = BridgeUtils.getRequestContext();
        String callerOrgMembership = rc.getCallerOrgMembership();
        if (rc.isInRole(ImmutableSet.of(SUPERADMIN, ADMIN)) || ownerId.equals(callerOrgMembership)) {
            return;
        }
        throw new UnauthorizedException(CALLER_NOT_MEMBER_ERROR);
    }
    
    /**
     * The same rules apply as checkOwnership, however you are examining the caller against a compound
     * value stored in the assessment's originId. The call succeeds if you're a superadmin (ther are 
     * no shared app admins), or if the caller is in the app and organization that owns the shared 
     * assessment. 
     */
    public static void checkSharedAssessmentOwnership(String callerAppId, String guid, String ownerId) {
        checkNotNull(callerAppId);
        checkNotNull(guid);
        checkNotNull(ownerId);

        RequestContext rc = BridgeUtils.getRequestContext();
        if (rc.isInRole(ImmutableSet.of(SUPERADMIN))) {
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
        String callerOrgMembership = rc.getCallerOrgMembership();
        if (originAppId.equals(callerAppId) && originOrgId.equals(callerOrgMembership)) {
            return;
        }
        throw new UnauthorizedException(CALLER_NOT_MEMBER_ERROR);
    }
}
