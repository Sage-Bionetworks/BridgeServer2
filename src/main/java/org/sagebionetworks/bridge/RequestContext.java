package org.sagebionetworks.bridge;

import java.util.List;
import java.util.Set;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Sets;

import org.sagebionetworks.bridge.models.ClientInfo;
import org.sagebionetworks.bridge.models.Metrics;
import org.sagebionetworks.bridge.models.accounts.StudyParticipant;
import org.sagebionetworks.bridge.models.accounts.UserSession;
import org.sagebionetworks.bridge.services.SponsorService;

public class RequestContext {

    // ThreadLocals are weird. They are basically a container that allows us to hold "global variables" for each
    // thread. This can be used, for example, to provide the request ID to any class without having to plumb a
    // "request context" object into every method of every class.
    private static final ThreadLocal<RequestContext> REQUEST_CONTEXT_THREAD_LOCAL = ThreadLocal.withInitial(() -> null);
    
    public static final RequestContext NULL_INSTANCE = new RequestContext(null, null, null, null, ImmutableSet.of(),
            ImmutableSet.of(), ImmutableSet.of(), null, ImmutableList.of(), null,
            null);
    
    /** Gets the request context for the current thread. See also RequestInterceptor. */
    public static RequestContext get() {
        RequestContext context = REQUEST_CONTEXT_THREAD_LOCAL.get();
        if (context == null) {
            return RequestContext.NULL_INSTANCE; 
        }
        return context;
    }

    /** @see #get */
    public static void set(RequestContext context) {
        REQUEST_CONTEXT_THREAD_LOCAL.set(context);
    }

    public static RequestContext updateFromSession(UserSession session, SponsorService sponsorService) {
        RequestContext.Builder builder = get().toBuilder();
        builder.withCallerAppId(session.getAppId());

        StudyParticipant participant = session.getParticipant();
        if (participant.getOrgMembership() != null) {
            Set<String> orgSponsoredStudies = sponsorService.getSponsoredStudyIds(
                    session.getAppId(), participant.getOrgMembership());
            builder.withOrgSponsoredStudies(orgSponsoredStudies);
        }
        builder.withCallerLanguages(participant.getLanguages());
        builder.withCallerOrgMembership(participant.getOrgMembership());
        builder.withCallerEnrolledStudies(participant.getStudyIds());
        builder.withCallerRoles(participant.getRoles());
        builder.withCallerUserId(participant.getId());

        RequestContext reqContext = builder.build();
        set(reqContext);
        return reqContext;
    }
    
    private final String requestId;
    private final String callerAppId;
    private final String callerOrgMembership;
    private final Set<String> callerEnrolledStudies;
    private final Set<String> orgSponsoredStudies;
    private final Set<Roles> callerRoles;
    private final String callerUserId;
    private final ClientInfo callerClientInfo;
    private final List<String> callerLanguages;    
    private final Metrics metrics;
    private final String callerIpAddress;
    private final String userAgent;
    
    private RequestContext(Metrics metrics, String requestId, String callerAppId, String callerOrgMembership,
            Set<String> callerEnrolledStudies, Set<String> orgSponsoredStudies, Set<Roles> callerRoles,
            String callerUserId, List<String> callerLanguages, String callerIpAddress, String userAgent) {
        this.requestId = requestId;
        this.callerAppId = callerAppId;
        this.callerOrgMembership = callerOrgMembership;
        this.callerEnrolledStudies = callerEnrolledStudies;
        this.orgSponsoredStudies = orgSponsoredStudies;
        this.callerRoles = callerRoles;
        this.callerUserId = callerUserId;
        this.callerClientInfo = ClientInfo.fromUserAgentCache(userAgent);
        this.callerLanguages = callerLanguages;
        this.metrics = metrics;
        this.callerIpAddress = callerIpAddress;
        this.userAgent = userAgent;
    }
    
    public Metrics getMetrics() {
        return metrics;
    }
    public String getId() {
        return requestId;
    }
    public String getCallerAppId() {
        return callerAppId;
    }
    public String getCallerOrgMembership() {
        return callerOrgMembership;
    }
    public Set<String> getCallerEnrolledStudies() {
        return callerEnrolledStudies;
    }
    public Set<String> getOrgSponsoredStudies() {
        return orgSponsoredStudies;
    }
    // Only accessible to tests to verify
    Set<Roles> getCallerRoles() {
        return callerRoles;
    }
    public boolean isAdministrator() { 
        return !callerRoles.isEmpty();
    }
    public boolean isInRole(Roles... roles) {
        return AuthUtils.isInRole(callerRoles, Sets.newHashSet(roles));
    }
    public boolean isInRole(Set<Roles> roleSet) {
        return AuthUtils.isInRole(callerRoles, roleSet);
    }
    public String getCallerUserId() { 
        return callerUserId;
    }
    public ClientInfo getCallerClientInfo() {
        return callerClientInfo;
    }
    public List<String> getCallerLanguages() {
        return callerLanguages;
    }
    /** The user's IP Address, as reported by Amazon. */
    public String getCallerIpAddress() {
        return callerIpAddress;
    }

    /** The user's User-Agent header. */
    public String getUserAgent() {
        return userAgent;
    }

    public RequestContext.Builder toBuilder() {
        return new RequestContext.Builder()
                .withRequestId(requestId)
                .withCallerAppId(callerAppId)
                .withCallerOrgMembership(callerOrgMembership)
                .withCallerLanguages(callerLanguages)
                .withCallerRoles(callerRoles)
                .withCallerEnrolledStudies(callerEnrolledStudies)
                .withOrgSponsoredStudies(orgSponsoredStudies)
                .withCallerUserId(callerUserId)
                .withMetrics(metrics)
                .withCallerIpAddress(callerIpAddress)
                .withUserAgent(userAgent);
    }
    
    public static class Builder {
        private Metrics metrics;
        private String callerAppId;
        private String callerOrgMembership;
        private Set<String> callerEnrolledStudies;
        private Set<String> orgSponsoredStudies;
        private Set<Roles> callerRoles;
        private String requestId;
        private String callerUserId;
        private List<String> callerLanguages;
        private String callerIpAddress;
        private String userAgent;

        public Builder withMetrics(Metrics metrics) {
            this.metrics = metrics;
            return this;
        }
        public Builder withCallerAppId(String appId) {
            this.callerAppId = appId;
            return this;
        }
        public Builder withCallerOrgMembership(String orgId) {
            this.callerOrgMembership = orgId;
            return this;
        }
        public Builder withCallerEnrolledStudies(Set<String> callerEnrolledStudies) {
            this.callerEnrolledStudies = (callerEnrolledStudies == null) ? 
                    null : ImmutableSet.copyOf(callerEnrolledStudies);
            return this;
        }
        public Builder withOrgSponsoredStudies(Set<String> orgSponsoredStudies){ 
            this.orgSponsoredStudies = (orgSponsoredStudies == null) ? 
                    null : ImmutableSet.copyOf(orgSponsoredStudies);
            return this;
        }
        public Builder withCallerRoles(Set<Roles> roles) {
            this.callerRoles = (roles == null) ? null : ImmutableSet.copyOf(roles);
            return this;
        }
        public Builder withRequestId(String requestId) {
            this.requestId = requestId;
            return this;
        }
        public Builder withCallerUserId(String callerUserId) {
            this.callerUserId = callerUserId;
            return this;
        }
        public Builder withCallerLanguages(List<String> callerLanguages) {
            this.callerLanguages = callerLanguages;
            return this;
        }
        public Builder withCallerIpAddress(String callerIpAddress) {
            this.callerIpAddress = callerIpAddress;
            return this;
        }

        /** The user's User-Agent header. */
        public Builder withUserAgent(String userAgent) {
            this.userAgent = userAgent;
            return this;
        }
        
        public RequestContext build() {
            if (requestId == null) {
                requestId = BridgeUtils.generateGuid();
            }
            if (callerEnrolledStudies == null) {
                callerEnrolledStudies = ImmutableSet.of();
            }
            if (orgSponsoredStudies == null) {
                orgSponsoredStudies = ImmutableSet.of();
            }
            if (callerRoles == null) {
                callerRoles = ImmutableSet.of();
            }
            if (callerLanguages == null) {
                callerLanguages = ImmutableList.of();
            }
            if (metrics == null) {
                metrics = new Metrics(requestId);
            }
            return new RequestContext(metrics, requestId, callerAppId, callerOrgMembership, callerEnrolledStudies,
                    orgSponsoredStudies, callerRoles, callerUserId, callerLanguages, callerIpAddress,
                    userAgent);
        }
    }

    @Override
    public String toString() {
        return "RequestContext [requestId=" + requestId + ", callerAppId=" + callerAppId + ", callerOrgMembership="
                + callerOrgMembership + ", callerEnrolledStudies=" + callerEnrolledStudies + ", orgSponsoredStudies="
                + orgSponsoredStudies + ", callerRoles=" + callerRoles + ", callerUserId=" + callerUserId
                + ", callerIpAddress=" + callerIpAddress + ", callerLanguages=" + callerLanguages + ", metrics="
                + metrics + ", userAgent=" + userAgent + "]";
    }
}
