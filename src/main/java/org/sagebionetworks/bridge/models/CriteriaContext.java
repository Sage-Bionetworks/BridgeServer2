package org.sagebionetworks.bridge.models;

import static com.google.common.base.Preconditions.checkNotNull;

import java.util.List;
import java.util.Objects;
import java.util.Set;

import org.sagebionetworks.bridge.models.accounts.AccountId;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;

public final class CriteriaContext {
    
    private final String appId;
    private final String healthCode;
    private final String userId;
    private final ClientInfo clientInfo;
    private final Set<String> userDataGroups;
    private final Set<String> userStudyIds;
    // This set has ordered keys (most to least preferential)
    private final List<String> languages;
    
    private CriteriaContext(String appId, String healthCode, String userId, ClientInfo clientInfo,
            Set<String> userDataGroups, Set<String> userStudyIds, List<String> languages) {
        this.appId = appId;
        this.healthCode = healthCode;
        this.userId = userId;
        this.clientInfo = clientInfo;
        this.userDataGroups = (userDataGroups == null) ? ImmutableSet.of() : ImmutableSet.copyOf(userDataGroups);
        this.userStudyIds = (userStudyIds == null) ? ImmutableSet.of() : ImmutableSet.copyOf(userStudyIds);
        this.languages = (languages == null) ? ImmutableList.of() : languages;
    }
    
    public AccountId getAccountId() {
        return AccountId.forId(appId, userId);
    }

    /**
    * Client information based on the supplied User-Agent header.
    */
    public ClientInfo getClientInfo() {
        return clientInfo;
    }

    public Set<String> getUserDataGroups() {
        return userDataGroups;
    }
    
    public Set<String> getUserStudyIds() {
        return userStudyIds;
    }
    
    public String getAppId() {
        return appId;
    }
    
    public String getHealthCode() {
        return healthCode;
    }
    
    public String getUserId() {
        return userId;
    }
    
    /**
     * Languages are sorted in order of descending preference of the LanguageRange objects 
     * submitted by the request via the Accept-Language HTTP header. That is to say, the 
     * client prefers the first language more than the second language, the second more than 
     * the third, etc. However, this isn't a SortedSet, because it is sorted by something 
     * (LanguageRange quality) that is external to the contents of the Set.
     */
    public List<String> getLanguages() {
        return languages;
    }

    @Override
    public int hashCode() {
        return Objects.hash(appId, healthCode, userId, clientInfo, userDataGroups, userStudyIds, languages);
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj)
            return true;
        if (obj == null || getClass() != obj.getClass())
            return false;
        CriteriaContext other = (CriteriaContext)obj;
        return (Objects.equals(clientInfo, other.clientInfo) &&
                Objects.equals(userDataGroups, other.userDataGroups) &&
                Objects.equals(userStudyIds, other.userStudyIds) &&
                Objects.equals(appId, other.appId) && 
                Objects.equals(healthCode, other.healthCode) && 
                Objects.equals(userId, other.userId) &&
                Objects.equals(languages, other.languages));
    }

    @Override
    public String toString() {
        return "CriteriaContext [appId=" + appId + ", userId=" + userId + ", clientInfo=" + clientInfo
                + ", userDataGroups=" + userDataGroups + ", userStudies=" + userStudyIds + ", languages="
                + languages + "]";
    }

    public static class Builder {
        private String appId;
        private String healthCode;
        private String userId;
        private ClientInfo clientInfo;
        private Set<String> userDataGroups;
        private Set<String> userStudyIds;
        private List<String> languages;

        public Builder withAppId(String appId) {
            this.appId = appId;
            return this;
        }
        public Builder withHealthCode(String healthCode) {
            this.healthCode = healthCode;
            return this;
        }
        public Builder withUserId(String userId) {
            this.userId = userId;
            return this;
        }
        public Builder withClientInfo(ClientInfo clientInfo) {
            this.clientInfo = clientInfo;
            return this;
        }
        public Builder withUserDataGroups(Set<String> userDataGroups) {
            this.userDataGroups = userDataGroups;
            return this;
        }
        public Builder withUserStudyIds(Set<String> userStudyIds) {
            this.userStudyIds = userStudyIds;
            return this;
        }
        public Builder withLanguages(List<String> languages) {
            this.languages = languages;
            return this;
        }

        public Builder withContext(CriteriaContext context) {
            this.appId = context.appId;
            this.healthCode = context.healthCode;
            this.userId = context.userId;
            this.clientInfo = context.clientInfo;
            this.userDataGroups = context.userDataGroups;
            this.userStudyIds = context.userStudyIds;
            this.languages = context.languages;
            return this;
        }

        public CriteriaContext build() {
            checkNotNull(appId, "appId cannot be null");
            if (clientInfo == null) {
                clientInfo = ClientInfo.UNKNOWN_CLIENT;
            }
            return new CriteriaContext(appId, healthCode, userId, clientInfo, userDataGroups,
                    userStudyIds, languages);
        }
    }
}
