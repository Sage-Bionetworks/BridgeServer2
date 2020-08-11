package org.sagebionetworks.bridge.models.accounts;

import static org.apache.commons.lang3.StringUtils.isBlank;
import static org.apache.commons.lang3.StringUtils.isNotBlank;

import org.sagebionetworks.bridge.dynamodb.DynamoExternalIdentifier;
import org.sagebionetworks.bridge.exceptions.BadRequestException;
import org.sagebionetworks.bridge.json.BridgeTypeName;
import org.sagebionetworks.bridge.models.BridgeEntity;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;

@BridgeTypeName("ExternalIdentifier")
@JsonDeserialize(builder = ExternalIdentifier.Builder.class)
public interface ExternalIdentifier extends BridgeEntity {

    static ExternalIdentifier create(String appId, String identifier) {
        if (isBlank(appId)) {
            throw new BadRequestException("appId cannot be null or blank");
        }
        return new DynamoExternalIdentifier(appId, identifier);
    }
    
    String getAppId();
    void setAppId(String appId);
    
    String getStudyId();
    void setStudyId(String studyId);
    
    String getIdentifier();
    void setIdentifier(String identifier);
    
    String getHealthCode();
    void setHealthCode(String healthCode);
    
    public static class Builder {
        private String appId;
        private String studyId;
        private String substudyId;
        private String identifier;
        private String healthCode;
        
        public Builder withAppId(String appId) {
            this.appId = appId;
            return this;
        }
        public Builder withStudyId(String studyId) {
            this.studyId = studyId;
            return this;
        }
        public Builder withSubstudyId(String substudyId) {
            this.substudyId = substudyId;
            return this;
        }
        public Builder withIdentifier(String identifier) {
            this.identifier = identifier;
            return this;
        }
        public Builder withHealthCode(String healthCode) {
            this.healthCode = healthCode;
            return this;
        }
        public ExternalIdentifier build() {
            DynamoExternalIdentifier extId = new DynamoExternalIdentifier();
            extId.setIdentifier(identifier);
            extId.setHealthCode(healthCode);
            // We can't do this with @JsonAlias because we're shifting the studyId
            // two places at this point, from substudyId -> studyId and studyId -> appId. 
            if (isNotBlank(studyId) && isNotBlank(substudyId)) {
                extId.setAppId(studyId);
                extId.setStudyId(substudyId);
            } else {
                extId.setAppId(appId);
                extId.setStudyId(studyId);
            }
            return extId;
        }
    }
}
