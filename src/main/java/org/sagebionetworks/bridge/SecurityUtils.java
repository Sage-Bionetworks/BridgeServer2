package org.sagebionetworks.bridge;

import static java.util.stream.Collectors.toSet;
import static org.apache.commons.lang3.StringUtils.isBlank;
import static org.sagebionetworks.bridge.BridgeConstants.CALLER_NOT_MEMBER_ERROR;
import static org.sagebionetworks.bridge.Roles.SUPERADMIN;

import java.util.Collection;
import java.util.Map;
import java.util.Set;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;

import org.sagebionetworks.bridge.exceptions.UnauthorizedException;
import org.sagebionetworks.bridge.models.accounts.Account;
import org.sagebionetworks.bridge.models.accounts.ExternalIdentifier;
import org.sagebionetworks.bridge.models.substudies.AccountSubstudy;

public class SecurityUtils {
    private static final SubstudyAssociations NO_ASSOCIATIONS = new SubstudyAssociations(ImmutableSet.of(),
            ImmutableMap.of());
    
    public static class SubstudyAssociations {
        private final Set<String> substudyIdsVisibleToCaller;
        private final Map<String, String> externalIdsVisibleToCaller;
        SubstudyAssociations(Set<String> substudyIdsVisibleToCaller, Map<String, String> externalIdsVisibleToCaller) {
            this.substudyIdsVisibleToCaller = substudyIdsVisibleToCaller;
            this.externalIdsVisibleToCaller = externalIdsVisibleToCaller;
        }
        public Set<String> getSubstudyIdsVisibleToCaller() {
            return substudyIdsVisibleToCaller;
        }
        public Map<String, String> getExternalIdsVisibleToCaller() {
            return externalIdsVisibleToCaller;
        }
    }
    
    /**
     * If the caller has no organizational membership, then they can set any organization (however they 
     * must set one, unlike the implementation of substudy relationships to user accounts). In this 
     * case we check the org ID to ensure it's valid. If the caller has organizational memberships, 
     * then the caller must be a member of the organization being cited. At that point we do not need 
     * to validate the org ID since it was validated when it was set as an organizational relationship
     * on the account. 
     */
    public static void checkOwnership(String appId, String ownerId) {
        if (isBlank(ownerId)) {
            throw new UnauthorizedException(CALLER_NOT_MEMBER_ERROR);
        }
        Set<String> callerSubstudies = BridgeUtils.getRequestContext().getCallerSubstudies();
        if (callerSubstudies.isEmpty() || callerSubstudies.contains(ownerId)) {
            return;
        }
        throw new UnauthorizedException(CALLER_NOT_MEMBER_ERROR);
    }
    
    public static Account filterForSubstudy(Account account) {
        if (account != null) {
            RequestContext context = BridgeUtils.getRequestContext();
            Set<String> callerSubstudies = context.getCallerSubstudies();
            if (BridgeUtils.isEmpty(callerSubstudies)) {
                return account;
            }
            Set<AccountSubstudy> matched = account.getAccountSubstudies().stream()
                    .filter(as -> callerSubstudies.isEmpty() || callerSubstudies.contains(as.getSubstudyId()))
                    .collect(toSet());
            
            if (!matched.isEmpty()) {
                // Hibernate managed objects use a collection implementation that tracks changes,
                // and shouldn't be set with a Java library collection. Here it is okay because 
                // we're filtering an object to return through the API, and it won't be persisted.
                account.setAccountSubstudies(matched);
                return account;
            }
        }
        return null;
    }
    
    /**
     * Callers only see the accountSubstudy records they themselves are assigned to, unless they have no
     * substudy memberships (then they are global and see everything).
     */
    public static SubstudyAssociations substudyAssociationsVisibleToCaller(Collection<? extends AccountSubstudy> accountSubstudies) {
        if (accountSubstudies == null || accountSubstudies.isEmpty()) {
            return NO_ASSOCIATIONS;
        }
        ImmutableSet.Builder<String> substudyIds = new ImmutableSet.Builder<>();
        ImmutableMap.Builder<String,String> externalIds = new ImmutableMap.Builder<>();
        Set<String> callerSubstudies = BridgeUtils.getRequestContext().getCallerSubstudies();
        for (AccountSubstudy acctSubstudy : accountSubstudies) {
            if (callerSubstudies.isEmpty() || callerSubstudies.contains(acctSubstudy.getSubstudyId())) {
                substudyIds.add(acctSubstudy.getSubstudyId());
                if (acctSubstudy.getExternalId() != null) {
                    externalIds.put(acctSubstudy.getSubstudyId(), acctSubstudy.getExternalId());
                }
            }
        }
        return new SubstudyAssociations(substudyIds.build(), externalIds.build()); 
    }

    public static ExternalIdentifier filterForSubstudy(ExternalIdentifier externalId) {
        if (externalId != null) {
            RequestContext context = BridgeUtils.getRequestContext();
            Set<String> callerSubstudies = context.getCallerSubstudies();
            if (BridgeUtils.isEmpty(callerSubstudies)) {
                return externalId;
            }
            if (callerSubstudies.contains(externalId.getSubstudyId())) {
                return externalId;
            }
        }
        return null;
    }
    
    public static boolean isInRole(Set<Roles> callerRoles, Roles requiredRole) {
        return (callerRoles != null && requiredRole != null && 
                (callerRoles.contains(SUPERADMIN) || callerRoles.contains(requiredRole)));
    }
    
    public static boolean isInRole(Set<Roles> callerRoles, Set<Roles> requiredRoles) {
        return callerRoles != null && requiredRoles != null && 
                requiredRoles.stream().anyMatch(role -> isInRole(callerRoles, role));
    }    
}
