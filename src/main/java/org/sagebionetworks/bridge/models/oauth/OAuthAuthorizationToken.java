package org.sagebionetworks.bridge.models.oauth;

import java.util.Objects;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * The payload of the authorization token that is sent by the client to retrieve an OAuth 2.0 access token. 
 */
public final class OAuthAuthorizationToken {
    private final String appId;
    private final String vendorId;
    private final String authToken;
    private final String callbackUrl;

    @JsonCreator
    public OAuthAuthorizationToken(@JsonProperty("appId") @JsonAlias("study") String appId,
            @JsonProperty("vendorId") String vendorId, @JsonProperty("authToken") String authToken,
            @JsonProperty("callbackUrl") String callbackUrl) {
        this.appId = appId;
        this.vendorId = vendorId;
        this.authToken = authToken;
        this.callbackUrl = callbackUrl;
    }
    
    public String getAppId() {
        return appId;
    }
    
    public String getVendorId() {
        return vendorId;
    }

    public String getAuthToken() {
        return authToken;
    }
    
    public String getCallbackUrl() { 
        return callbackUrl;
    }

    @Override
    public int hashCode() {
        return Objects.hash(appId, authToken, vendorId, callbackUrl);
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj)
            return true;
        if (obj == null || getClass() != obj.getClass())
            return false;
        OAuthAuthorizationToken other = (OAuthAuthorizationToken) obj;
        return Objects.equals(appId, other.appId) &&
               Objects.equals(authToken, other.authToken) && 
               Objects.equals(vendorId, other.vendorId) &&
               Objects.equals(callbackUrl, other.callbackUrl);
    }

    @Override
    public String toString() {
        return "OAuthAuthorizationToken [appId = " + appId + ", vendorId=" + vendorId + ", authToken=" + authToken
                + ", callbackUrl=" + callbackUrl + "]";
    }
}
