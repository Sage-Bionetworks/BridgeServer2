package org.sagebionetworks.bridge;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.function.Predicate;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;

import org.sagebionetworks.bridge.exceptions.UnauthorizedException;

/**
 * Utility for creating rules that can evaluate authorization for a caller. Currently referenced
 * by the AuthUtils class, but could be used elsewhere.
 */
public class AuthEvaluator {
    private final Set<Predicate<Map<String,String>>> predicates;
    
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
            return (roles.length == 0) ?
                    context.isAdministrator() :
                    context.isInRole(ImmutableSet.copyOf(roles));
        });
        return this;
    }
    /**
     * The caller is a member of an organization that sponsors the target study.
     */
    public AuthEvaluator canAccessStudy() {
        predicates.add((factMap) -> {
            String studyId = factMap.get("studyId");
            return RequestContext.get().getOrgSponsoredStudies().contains(studyId);
        });
        return this;
    }
    
    /**
     * This remains a huge loophole that we have to eliminate. We might want to
     * verify that the study they are manipulating/reading is in their list of
     * callerEnrolledStudies, instead of making exceptions for the empty study
     * array.
     */
    public AuthEvaluator callerConsideredGlobal() {
        predicates.add((factMap) -> {
            return RequestContext.get().getOrgSponsoredStudies().isEmpty();
        });
        return this;
    }
    
    /**
     * The caller’s session is bound to the target app.
     */
    public AuthEvaluator isInApp() {
        predicates.add((factMap) -> {
            String appId = factMap.get("appId");
            return appId != null && appId.equals(RequestContext.get().getCallerAppId()); 
        });
        return this;
    }
    /**
     * The caller is a member of the target organization.
     */
    public AuthEvaluator isInOrg() {
        predicates.add((factMap) -> {
            String orgId = factMap.get("orgId");
            return orgId != null && orgId.equals(RequestContext.get().getCallerOrgMembership()); 
        });
        return this;
    }
    /**
     * The caller is operating on their own account (the target user ID).
     */
    public AuthEvaluator isSelf() {
        predicates.add((factMap) -> {
            String userId = factMap.get("userId");
            return userId != null && userId.equals(RequestContext.get().getCallerUserId()); 
        });
        return this;
    }
    public AuthEvaluator isSharedOwner() {
        predicates.add((factMap) -> {
            String ownerId = factMap.get("ownerId");
            String[] parts = ownerId.split(":", 2);
            if (parts.length != 2) {
                return false;
            }
            String appId = parts[0];
            String orgId = parts[1];
            return appId != null && appId.equals(RequestContext.get().getCallerAppId()) &&
                orgId != null && orgId.equals(RequestContext.get().getCallerOrgMembership());
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
    public void check() {
        if (!verify()) {
            throw new UnauthorizedException();
        }
    }
    /**
     * Check the authorization rule and throw an exception if it fails.
     * 
     * @throws org.sagebionetworks.bridge.exceptions.UnauthorizedException
     */
    private void check(String arg1, String val1) {
        if (!verify(arg1, val1)) {
            throw new UnauthorizedException();
        }
    }
    /**
     * Check the authorization rule and throw an exception if it fails.
     * 
     * @throws org.sagebionetworks.bridge.exceptions.UnauthorizedException
     */
    private void check(String arg1, String val1, String arg2, String val2) {
        if (!verify(arg1, val1, arg2, val2)) {
            throw new UnauthorizedException();
        }
    }
    /**
     * Return true if the authorization rule passes, false otherwise. Missing, blank, and 
     * null values fail authorization tests.
     */
    public boolean verify() {
        return verifyInternal(ImmutableMap.of());
    }
    /**
     * Return true if the authorization rule passes, false otherwise. Missing, blank, and 
     * null values fail authorization tests.
     */
    public boolean verify(String arg1, String val1) {
        Map<String,String> factMap = new HashMap<>(); // can contain nulls
        factMap.put(arg1, val1);
        return verifyInternal(factMap);
    }
    /**
     * Return true if the authorization rule passes, false otherwise. Missing, blank, and 
     * null values fail authorization tests.
     */
    public boolean verify(String arg1, String val1, String arg2, String val2) {
        Map<String,String> factMap = new HashMap<>(); // can contain nulls
        factMap.put(arg1, val1);
        factMap.put(arg2, val2);
        return verifyInternal(factMap);
    }
    protected boolean verifyInternal(Map<String,String> factMap) {
        // this happens on the stack and should be thread-safe. 
        for (Predicate<Map<String,String>> predicate : predicates) {
            if (!predicate.test(factMap)) {
                return false;
            }
        }
        return true;
    }
    
    // UTILITY METHODS (it turns out we only have this many argument combinations).
    
    public boolean verifyOrgId(String orgId) {
        return verify("orgId", orgId);
    }
    public boolean verifyOrgAndUserIds(String orgId, String userId) {
        return verify("orgId", orgId, "userId", userId);
    }
    public boolean verifyStudyAndUserIds(String studyId, String userId) {
        return verify("studyId", studyId, "userId", userId);
    }
    public boolean verifyStudyId(String studyId) {
        return verify("studyId", studyId);
    }
    public void checkOrgId(String orgId) {
        check("orgId", orgId);
    }
    public void checkStudyId(String studyId) {
        check("studyId", studyId);
    }
    public void checkUserId(String userId) {
        check("userId", userId);
    }
    public void checkOwnerId(String ownerId) {
        check("ownerId", ownerId);
    }
    public void checkOrgAndUserIds(String orgId, String userId) {
        check("orgId", orgId, "userId", userId);
    }
    public void checkStudyAndUserIds(String studyId, String userId) {
        check("studyId", studyId, "userId", userId);
    }
    
    private static class OrAuthEvaluator extends AuthEvaluator {
        AuthEvaluator evaluator;
        
        private OrAuthEvaluator(AuthEvaluator evaluator) {
            this.evaluator = evaluator;
        }
        @Override
        protected boolean verifyInternal(Map<String, String> factMap) {
            return super.verifyInternal(factMap) || evaluator.verifyInternal(factMap);
        }
    }
}