package org.sagebionetworks.bridge.models.studies;

import java.util.Objects;

import org.sagebionetworks.bridge.models.BridgeEntity;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

public final class OAuthProvider implements BridgeEntity {
    private final String clientId;
    private final String secret;
    private final String endpoint;
    private final String callbackUrl;
    private final String introspectEndpoint;

    @JsonCreator
    public OAuthProvider(@JsonProperty("clientId") String clientId, @JsonProperty("secret") String secret,
            @JsonProperty("endpoint") String endpoint, @JsonProperty("callbackUrl") String callbackUrl,
            @JsonProperty("introspectEndpoint") String introspectEndpoint) {
        this.clientId = clientId;
        this.secret = secret;
        this.endpoint = endpoint;
        this.callbackUrl = callbackUrl;
        this.introspectEndpoint = introspectEndpoint;
    }
    public String getClientId() {
        return clientId;
    }
    public String getSecret() {
        return secret;
    }
    public String getEndpoint() {
        return endpoint;
    }
    public String getCallbackUrl() {
        return callbackUrl;
    }

    /** OAuth introspect URL, as defined by RFC7662. Optional but recommended. */
    public String getIntrospectEndpoint() {
        return introspectEndpoint;
    }

    @Override
    public int hashCode() {
        return Objects.hash(clientId, endpoint, secret, callbackUrl, introspectEndpoint);
    }
    @Override
    public boolean equals(Object obj) {
        if (this == obj)
            return true;
        if (obj == null || getClass() != obj.getClass())
            return false;
        OAuthProvider other = (OAuthProvider) obj;
        return Objects.equals(clientId, other.clientId) 
               && Objects.equals(endpoint, other.endpoint) 
               && Objects.equals(secret, other.secret)
               && Objects.equals(callbackUrl, other.callbackUrl)
               && Objects.equals(introspectEndpoint, other.introspectEndpoint);
    }
    @Override
    public String toString() {
        return "OAuthProvider [clientId=" + clientId + ", endpoint=" + endpoint + ", callbackUrl=" + callbackUrl
                + ", introspectEndpoint=" + introspectEndpoint + ", secret=REDACTED]";
    }
}
