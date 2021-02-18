package org.sagebionetworks.bridge;

import static com.google.common.base.Preconditions.checkNotNull;
import static org.sagebionetworks.bridge.AuthEvaluatorField.APP_ID;
import static org.sagebionetworks.bridge.AuthEvaluatorField.ORG_ID;
import static org.sagebionetworks.bridge.AuthEvaluatorField.OWNER_ID;
import static org.sagebionetworks.bridge.AuthEvaluatorField.STUDY_ID;
import static org.sagebionetworks.bridge.AuthEvaluatorField.USER_ID;
import static org.sagebionetworks.bridge.BridgeUtils.COMMA_SPACE_JOINER;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.function.Predicate;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.sagebionetworks.bridge.exceptions.UnauthorizedException;

/**
 * Utility for creating rules that can evaluate authorization for a caller. Currently referenced
 * by the AuthUtils class, but could be used elsewhere.
 */
public class AuthEvaluator {
    
    private static final Logger LOG = LoggerFactory.getLogger(AuthEvaluator.class);
    
    private final Set<Predicate<Map<AuthEvaluatorField,String>>> predicates;
    
    public AuthEvaluator() {
        predicates = new HashSet<>();
    }
    /**
     * Caller must have one of the supplied roles. If no roles are included, the caller must 
     * have any role (they must be an administrative account).
     */
    public AuthEvaluator hasAnyRole(Roles...roles) {
        predicates.add((factMap) -> {
            RequestContext context = RequestContext.get();
            boolean result = (roles.length == 0) ?
                    context.isAdministrator() :
                    context.isInRole(ImmutableSet.copyOf(roles));
            if (LOG.isTraceEnabled()) {
                LOG.trace("hasAnyRole, roles = " + roles + ", context.roles = " 
                        + COMMA_SPACE_JOINER.join(context.getCallerRoles()) 
                        + ", result = " + result);
            }
            return result;
        });
        return this;
    }
    /**
     * 
     */
    public AuthEvaluator hasNoRole(Roles... roles) {
        predicates.add((factMap) -> {
            RequestContext context = RequestContext.get();
            boolean result = (roles.length == 0) ? true : !context.isInRole(ImmutableSet.copyOf(roles));
            if (LOG.isTraceEnabled()) {
                LOG.trace("hasNoRole, roles = " + COMMA_SPACE_JOINER.join(roles) 
                    + ", context.callerRoles = " + context.getCallerRoles() 
                    + ", result = " + result);
            }
            return result;
        });
        return this;
    }
    /**
     * The caller is a member of an organization that sponsors the target study.
     */
    public AuthEvaluator canAccessStudy() {
        predicates.add((factMap) -> {
            RequestContext context = RequestContext.get();
            String studyId = factMap.get(STUDY_ID);
            boolean result = context.getOrgSponsoredStudies().contains(studyId);
            if (LOG.isTraceEnabled()) {
                LOG.trace("canAccessStudy, studyId = " + studyId + ", context.orgSponsoredStudies = " + 
                        context.getOrgSponsoredStudies() + ", result = " + result);
            }
            return result;
        });
        return this;
    }
    
    /**
     * The caller’s session is bound to the target app.
     */
    public AuthEvaluator isInApp() {
        predicates.add((factMap) -> {
            RequestContext context = RequestContext.get();
            String appId = factMap.get(APP_ID);
            boolean result = appId != null && appId.equals(context.getCallerAppId());
            if (LOG.isTraceEnabled()) {
                LOG.trace("isInApp, appId = " + appId + ", context.callerAppId = " + 
                        context.getCallerAppId() + ", result = " + result);
            }
            return result;
        });
        return this;
    }
    /**
     * The caller is a member of the target organization.
     */
    public AuthEvaluator isInOrg() {
        predicates.add((factMap) -> {
            RequestContext context = RequestContext.get();
            String orgId = factMap.get(ORG_ID);
            boolean result = orgId != null && orgId.equals(context.getCallerOrgMembership());
            if (LOG.isTraceEnabled()) {
                LOG.trace("isInOrg, orgId = " + orgId + ", context.callerOrgMembership = " + 
                        context.getCallerOrgMembership() + ", result = " + result);
            }
            return result;
        });
        return this;
    }
    
    public AuthEvaluator isEnrolledInStudy() {
        predicates.add((factMap) -> {
            RequestContext context = RequestContext.get();
            String studyId = factMap.get(STUDY_ID);
            boolean result = studyId != null && context.getCallerEnrolledStudies().contains(studyId);
            if (LOG.isTraceEnabled()) {
                LOG.trace("isEnrolledInStudy, studyId = " + studyId + ", context.callerEnrolledStudies = " + 
                        COMMA_SPACE_JOINER.join(context.getCallerEnrolledStudies()) + ", result = " + result);
            }
            return result;
        });
        return this;        
    }
    
    /**
     * The caller is operating on their own account (the target user ID).
     */
    public AuthEvaluator isSelf() {
        predicates.add((factMap) -> {
            RequestContext context = RequestContext.get();
            String userId = factMap.get(USER_ID);
            String callerUserId = context.getCallerUserId();
            // Calls like signUp happen without a session so there is no caller user ID in the 
            // request context. In this case, if we’re also not comparing the user ID to a 
            // known ID, allow this test to pass. This removes some special case code elsewhere
            // in the system.
            boolean result = (userId == null && callerUserId == null) ||
                   (userId != null && userId.equals(callerUserId));
            if (LOG.isTraceEnabled()) {
                LOG.trace("isSelf, userId = " + userId + ", context.callerUserId = " + 
                        context.getCallerUserId() + ", result = " + result);
            }
            return result;
        });
        return this;
    }
    public AuthEvaluator isSharedOwner() {
        predicates.add((factMap) -> {
            String ownerId = factMap.get(OWNER_ID);
            if (ownerId == null) {
                return false;
            }
            String[] parts = ownerId.split(":", 2);
            if (parts.length != 2) {
                return false;
            }
            String appId = parts[0];
            String orgId = parts[1];
            return appId.equals(RequestContext.get().getCallerAppId()) &&
                orgId.equals(RequestContext.get().getCallerOrgMembership());
        });
        return this;
    }
    /**
     * Either the left- or right-hand portion of the expression needs to be true (but not both).
     */
    public AuthEvaluator or() {
        return new OrAuthEvaluator(this);
    }
    /**
     * Check the authorization rule and throw an exception if it fails.
     * 
     * @throws org.sagebionetworks.bridge.exceptions.UnauthorizedException
     */
    public void checkAndThrow() {
        if (!check()) {
            throw new UnauthorizedException();
        }
    }
    /**
     * Check the authorization rule and throw an exception if it fails.
     * 
     * @throws org.sagebionetworks.bridge.exceptions.UnauthorizedException
     */
    public void checkAndThrow(AuthEvaluatorField arg1, String val1) {
        checkNotNull(arg1);
        if (!check(arg1, val1)) {
            throw new UnauthorizedException();
        }
    }
    /**
     * Check the authorization rule and throw an exception if it fails.
     * 
     * @throws org.sagebionetworks.bridge.exceptions.UnauthorizedException
     */
    public void checkAndThrow(AuthEvaluatorField arg1, String val1, AuthEvaluatorField arg2, String val2) {
        checkNotNull(arg1);
        checkNotNull(arg2);
        if (!check(arg1, val1, arg2, val2)) {
            throw new UnauthorizedException();
        }
    }
    /**
     * Return true if the authorization rule passes, false otherwise. Missing, blank, and 
     * null values fail authorization tests.
     */
    public boolean check() {
        return checkInternal(ImmutableMap.of());
    }
    /**
     * Return true if the authorization rule passes, false otherwise. Missing, blank, and 
     * null values fail authorization tests.
     */
    public boolean check(AuthEvaluatorField arg1, String val1) {
        checkNotNull(arg1);
        Map<AuthEvaluatorField,String> factMap = new HashMap<>(); // can contain nulls
        factMap.put(arg1, val1);
        return checkInternal(factMap);
    }
    /**
     * Return true if the authorization rule passes, false otherwise. Missing, blank, and 
     * null values fail authorization tests.
     */
    public boolean check(AuthEvaluatorField arg1, String val1, AuthEvaluatorField arg2, String val2) {
        checkNotNull(arg1);
        checkNotNull(arg2);
        Map<AuthEvaluatorField,String> factMap = new HashMap<>(); // can contain nulls
        factMap.put(arg1, val1);
        factMap.put(arg2, val2);
        return checkInternal(factMap);
    }
    protected boolean checkInternal(Map<AuthEvaluatorField,String> factMap) {
        // this happens on the stack and should be thread-safe. 
        for (Predicate<Map<AuthEvaluatorField,String>> predicate : predicates) {
            if (!predicate.test(factMap)) {
                return false;
            }
        }
        return true;
    }
    
    private static class OrAuthEvaluator extends AuthEvaluator {
        AuthEvaluator evaluator;
        
        private OrAuthEvaluator(AuthEvaluator evaluator) {
            this.evaluator = evaluator;
        }
        @Override
        protected boolean checkInternal(Map<AuthEvaluatorField, String> factMap) {
            return super.checkInternal(factMap) || evaluator.checkInternal(factMap);
        }
    }
}