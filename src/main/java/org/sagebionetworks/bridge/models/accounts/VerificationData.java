package org.sagebionetworks.bridge.models.accounts;

import static com.google.common.base.Preconditions.checkArgument;
import static org.apache.commons.lang3.StringUtils.isNotBlank;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;

import org.sagebionetworks.bridge.services.AuthenticationService.ChannelType;

@JsonDeserialize(builder = VerificationData.Builder.class)
public class VerificationData {
    private final String appId;
    private final String userId;
    private final ChannelType type;
    private final long expiresOn;
    
    private VerificationData(String appId, ChannelType type, String userId, long expiresOn) {
        checkArgument(isNotBlank(appId));
        checkArgument(isNotBlank(userId));
        this.appId = appId;
        this.userId = userId;
        this.type = type;
        this.expiresOn = expiresOn;
    }
    public String getAppId() {
        return appId;
    }
    public String getUserId() {
        return userId;
    }
    public ChannelType getType() {
        return type;
    }
    public long getExpiresOn() {
        return expiresOn;
    }
    
    // The builder pattern deals cleanly with deserializing studyId 
    // and/or appId to the appId property
    public static class Builder {
        private String appId;
        private String userId;
        private ChannelType type;
        private long expiresOn;
        
        public Builder withStudyId(String studyId) {
            this.appId = studyId;
            return this;
        }
        public Builder withAppId(String appId) {
            this.appId = appId;
            return this;
        }
        public Builder withUserId(String userId) {
            this.userId = userId;
            return this;
        }
        public Builder withType(ChannelType type) {
            this.type = type;
            return this;
        }
        public Builder withExpiresOn(long expiresOn) {
            this.expiresOn = expiresOn;
            return this;
        }
        public VerificationData build() {
            return new VerificationData(appId, type, userId, expiresOn);
        }
    }
}
