package org.sagebionetworks.bridge;

import static org.sagebionetworks.bridge.Roles.SUPERADMIN;

import org.sagebionetworks.bridge.exceptions.UnauthorizedException;

public class AuthUtils {
    
    /**
     * Unless you are a SUPERADMIN, you can only list the members of your own organization.
     */
    public static boolean checkOrgMembership(String proposedOrgId) {
        RequestContext context = BridgeUtils.getRequestContext();
        String callerOrgMembership = context.getCallerOrgMembership();
        
        System.out.println("context.isInRole(SUPERADMIN): " + context.isInRole(SUPERADMIN));
        System.out.println("proposedOrgId.equals(callerOrgMembership): " + proposedOrgId.equals(callerOrgMembership));
        
        return context.isInRole(SUPERADMIN) || proposedOrgId.equals(callerOrgMembership);
    }
    
    public static void checkOrgMembershipAndThrow(String proposedOrgId) {
        if (!checkOrgMembership(proposedOrgId)) {
            throw new UnauthorizedException("Caller is not a member of " + proposedOrgId);    
        }
    }
}
