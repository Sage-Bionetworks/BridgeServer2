package org.sagebionetworks.bridge;

import static org.sagebionetworks.bridge.models.ClientInfo.UNKNOWN_CLIENT;

import java.util.List;
import java.util.Set;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;

import org.sagebionetworks.bridge.models.ClientInfo;
import org.sagebionetworks.bridge.models.Metrics;
import org.sagebionetworks.bridge.models.studies.StudyIdentifier;
import org.sagebionetworks.bridge.models.studies.StudyIdentifierImpl;

public class RequestContext {
    
    public static final RequestContext NULL_INSTANCE = new RequestContext(null, null, null, ImmutableSet.of(),
            ImmutableSet.of(), null, UNKNOWN_CLIENT, ImmutableList.of(), null, null, ImmutableSet.of());

    private final String requestId;
    private final ClientInfo callerClientInfo;
    private final List<String> callerLanguages;    
    // Authenticated requests can acquire additional information
    private final StudyIdentifier callerStudyId;
    private final String callerUserId;
    private final Set<String> callerSubstudies;
    private final Set<Roles> callerRoles;
    private final String callerHealthCode;
    private final String callerIpAddress;
    private final Set<String> callerDataGroups;
    private final Metrics metrics;
    
    private RequestContext(Metrics metrics, String requestId, String callerStudyId, Set<String> callerSubstudies,
            Set<Roles> callerRoles, String callerUserId, ClientInfo callerClientInfo, List<String> callerLanguages,
            String callerHealthCode, String callerIpAddress, Set<String> callerDataGroups) {
        this.requestId = requestId;
        this.callerStudyId = (callerStudyId == null) ? null : new StudyIdentifierImpl(callerStudyId);
        this.callerSubstudies = callerSubstudies;
        this.callerRoles = callerRoles;
        this.callerUserId = callerUserId;
        this.callerClientInfo = callerClientInfo;
        this.callerLanguages = callerLanguages;
        this.callerHealthCode = callerHealthCode;
        this.callerIpAddress = callerIpAddress;
        this.callerDataGroups = callerDataGroups;
        this.metrics = metrics;
    }
    
    public Metrics getMetrics() {
        return metrics;
    }
    public String getId() {
        return requestId;
    }
    public String getCallerStudyId() {
        return (callerStudyId == null) ? null : callerStudyId.getIdentifier();
    }
    public StudyIdentifier getCallerStudyIdentifier() {
        return callerStudyId;
    }
    public Set<String> getCallerSubstudies() {
        return callerSubstudies;
    }
    public Set<Roles> getCallerRoles() {
        return callerRoles;
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
    public String getCallerHealthCode() {
        return callerHealthCode;
    }
    public String getCallerIpAddress() { 
        return callerIpAddress;
    }
    public Set<String> getCallerDataGroups() {
        return callerDataGroups;
    }
    
    public RequestContext.Builder toBuilder() {
        return new RequestContext.Builder()
            .withRequestId(requestId)
            .withCallerClientInfo(callerClientInfo)
            .withCallerStudyId(callerStudyId)
            .withCallerLanguages(callerLanguages)
            .withCallerRoles(callerRoles)
            .withCallerSubstudies(callerSubstudies)
            .withCallerUserId(callerUserId)
            .withCallerHealthCode(callerHealthCode)
            .withCallerIpAddress(callerIpAddress)
            .withCallerDataGroups(callerDataGroups)
            .withMetrics(metrics);
    }
    
    public static class Builder {
        private Metrics metrics;
        private String callerStudyId;
        private Set<String> callerSubstudies;
        private Set<Roles> callerRoles;
        private String requestId;
        private String callerUserId;
        private ClientInfo callerClientInfo;
        private List<String> callerLanguages;
        private String callerHealthCode;
        private String callerIpAddress;
        private Set<String> callerDataGroups;

        public Builder withMetrics(Metrics metrics) {
            this.metrics = metrics;
            return this;
        }
        public Builder withCallerStudyId(StudyIdentifier studyId) {
            this.callerStudyId = (studyId == null) ? null : studyId.getIdentifier();
            return this;
        }
        public Builder withCallerSubstudies(Set<String> callerSubstudies) {
            this.callerSubstudies = (callerSubstudies == null) ? null : ImmutableSet.copyOf(callerSubstudies);
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
        public Builder withCallerHealthCode(String callerHealthCode) {
            this.callerHealthCode = callerHealthCode;
            return this;
        }
        public Builder withCallerIpAddress(String callerIpAddress) {
            this.callerIpAddress = callerIpAddress;
            return this;
        }
        public Builder withCallerDataGroups(Set<String> callerDataGroups) {
            this.callerDataGroups = (callerDataGroups == null) ? null : ImmutableSet.copyOf(callerDataGroups);
            return this;
        }
        
        public RequestContext build() {
            if (requestId == null) {
                requestId = BridgeUtils.generateGuid();
            }
            if (callerSubstudies == null) {
                callerSubstudies = ImmutableSet.of();
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
            if (callerDataGroups == null) {
                callerDataGroups = ImmutableSet.of();
            }
            return new RequestContext(metrics, requestId, callerStudyId, callerSubstudies, callerRoles, callerUserId,
                    callerClientInfo, callerLanguages, callerHealthCode, callerIpAddress, callerDataGroups);
        }
    }

    @Override
    public String toString() {
        return "RequestContext [requestId=" + requestId + ", callerStudyId=" + callerStudyId + ", callerSubstudies="
                + callerSubstudies + ", callerRoles=" + callerRoles + ", callerUserId=" + callerUserId
                + ", callerClientInfo=" + callerClientInfo + ", callerLanguages=" + callerLanguages + ", metrics="
                + metrics + ", callerHealthCode=[REDACTED], callerIpAddress=" + callerIpAddress + ", callerDataGroups="
                + callerDataGroups + "]";
    }
}
