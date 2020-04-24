package org.sagebionetworks.bridge.models.accounts;

import org.sagebionetworks.bridge.models.BridgeEntity;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonProperty;

public class PasswordReset implements BridgeEntity {

    private final String password;
    private final String sptoken;
    private final String appId;
    
    public PasswordReset(@JsonProperty("password") String password, @JsonProperty("sptoken") String sptoken,
            @JsonAlias("study") @JsonProperty("appId") String appId) {
        this.password = password;
        this.sptoken = sptoken;
        this.appId = appId;
    }
    
    public String getAppId() {
        return appId;
    }
    
    public String getPassword() {
        return password;
    }

    public String getSptoken() {
        return sptoken;
    }

}
