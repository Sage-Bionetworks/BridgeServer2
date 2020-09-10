package org.sagebionetworks.bridge;

import static org.sagebionetworks.bridge.models.ClientInfo.UNKNOWN_CLIENT;

import java.util.List;
import java.util.Set;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;

import org.sagebionetworks.bridge.models.ClientInfo;
import org.sagebionetworks.bridge.models.Metrics;

public class RequestContext {
    
    public static final RequestContext NULL_INSTANCE = new RequestContext(null, null, null, null, ImmutableSet.of(),
            ImmutableSet.of(), ImmutableSet.of(), null, UNKNOWN_CLIENT, ImmutableList.of(), null);
    
    private final String requestId;
    private final String callerAppId;
    private final String callerOrgMembership;
    private final Set<String> callerStudies;
    private final Set<String> orgSponsoredStudies;
    private final Set<Roles> callerRoles;
    private final String callerUserId;
    private final ClientInfo callerClientInfo;
    private final List<String> callerLanguages;    
    private final Metrics metrics;
    private final String callerIpAddress;
    
    private RequestContext(Metrics metrics, String requestId, String callerAppId, String callerOrgMembership,
            Set<String> callerStudies, Set<String> orgSponsoredStudies, Set<Roles> callerRoles, String callerUserId,
            ClientInfo callerClientInfo, List<String> callerLanguages, String callerIpAddress) {
        this.requestId = requestId;
        this.callerAppId = callerAppId;
        this.callerOrgMembership = callerOrgMembership;
        this.callerStudies = callerStudies;
        this.orgSponsoredStudies = orgSponsoredStudies;
        this.callerRoles = callerRoles;
        this.callerUserId = callerUserId;
        this.callerClientInfo = callerClientInfo;
        this.callerLanguages = callerLanguages;
        this.metrics = metrics;
        this.callerIpAddress = callerIpAddress;
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
    public Set<String> getCallerStudies() {
        return callerStudies;
    }
    public Set<String> getOrgSponsoredStudies() {
        return orgSponsoredStudies;
    }
    // Only accessible to tests to verify
    Set<Roles> getCallerRoles() {
        return callerRoles;
    }
    public boolean isAdministrator() { 
        return callerRoles != null && !callerRoles.isEmpty();
    }
    public boolean isInRole(Roles role) {
        return BridgeUtils.isInRole(callerRoles, role);
    }
    public boolean isInRole(Set<Roles> roleSet) {
        return BridgeUtils.isInRole(callerRoles, roleSet);
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
    public RequestContext.Builder toBuilder() {
        return new RequestContext.Builder()
            .withRequestId(requestId)
            .withCallerClientInfo(callerClientInfo)
            .withCallerAppId(callerAppId)
            .withCallerOrgMembership(callerOrgMembership)
            .withCallerLanguages(callerLanguages)
            .withCallerRoles(callerRoles)
            .withCallerStudies(callerStudies)
            .withOrgSponsoredStudies(orgSponsoredStudies)
            .withCallerUserId(callerUserId)
            .withMetrics(metrics)
            .withCallerIpAddress(callerIpAddress);
    }
    
    public static class Builder {
        private Metrics metrics;
        private String callerAppId;
        private String callerOrgMembership;
        private Set<String> callerStudies;
        private Set<String> orgSponsoredStudies;
        private Set<Roles> callerRoles;
        private String requestId;
        private String callerUserId;
        private ClientInfo callerClientInfo;
        private List<String> callerLanguages;
        private String callerIpAddress;

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
        public Builder withCallerStudies(Set<String> callerStudies) {
            this.callerStudies = (callerStudies == null) ? null : ImmutableSet.copyOf(callerStudies);
            return this;
        }
        public Builder withOrgSponsoredStudies(Set<String> orgSponsoredStudies){ 
            this.orgSponsoredStudies = (orgSponsoredStudies == null) ? null : ImmutableSet.copyOf(orgSponsoredStudies);
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
        public Builder withCallerClientInfo(ClientInfo callerClientInfo) {
            this.callerClientInfo = callerClientInfo;
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
        
        public RequestContext build() {
            if (requestId == null) {
                requestId = BridgeUtils.generateGuid();
            }
            if (callerStudies == null) {
                callerStudies = ImmutableSet.of();
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
            if (callerClientInfo == null) {
                callerClientInfo = ClientInfo.UNKNOWN_CLIENT;
            }
            if (metrics == null) {
                metrics = new Metrics(requestId);
            }
            return new RequestContext(metrics, requestId, callerAppId, callerOrgMembership, callerStudies,
                    orgSponsoredStudies, callerRoles, callerUserId, callerClientInfo, callerLanguages, callerIpAddress);
        }
    }

    @Override
    public String toString() {
        return "RequestContext [requestId=" + requestId + ", callerAppId=" + callerAppId + ", callerOrgMembership="
                + callerOrgMembership + ", callerStudies=" + callerStudies + ", orgSponsoredStudies="
                + orgSponsoredStudies + ", callerRoles=" + callerRoles + ", callerUserId=" + callerUserId
                + ", callerClientInfo=" + callerClientInfo + ", callerIpAddress=" + callerIpAddress
                + ", callerLanguages=" + callerLanguages + ", metrics=" + metrics + "]";
    }
}
