package org.sagebionetworks.bridge.models.oauth;

import java.util.Collection;
import java.util.Objects;
import java.util.Set;

import com.google.common.collect.ImmutableSet;
import org.joda.time.DateTime;

import org.sagebionetworks.bridge.BridgeUtils;
import org.sagebionetworks.bridge.json.DateTimeDeserializer;
import org.sagebionetworks.bridge.json.DateTimeSerializer;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;

/**
 * A representation of the access grant as returned through the API to consumers. 
 */
public final class OAuthAccessToken {
    private final String vendorId;
    private final String accessToken;
    private final DateTime expiresOn;
    private final String providerUserId;
    private final Set<String> scopes;

    @JsonCreator
    public OAuthAccessToken(@JsonProperty("vendorId") String vendorId, @JsonProperty("accessToken") String accessToken,
            @JsonProperty("expiresOn") DateTime expiresOn, @JsonProperty("providerUserId") String providerUserId,
            @JsonProperty("scopes") Collection<String> scopes) {
        this.vendorId = vendorId;
        this.accessToken = accessToken;
        this.expiresOn = expiresOn;
        this.providerUserId = providerUserId;
        this.scopes = scopes != null ? ImmutableSet.copyOf(scopes) : ImmutableSet.of();
    }

    public String getVendorId() {
        return vendorId;
    }

    public String getAccessToken() {
        return accessToken;
    }

    @JsonSerialize(using = DateTimeSerializer.class)
    @JsonDeserialize(using = DateTimeDeserializer.class)
    public DateTime getExpiresOn() {
        return expiresOn;
    }
    
    public String getProviderUserId() {
        return providerUserId;
    }

    /** Set of scopes attached to the OAuth access grant. Never null, but may be empty. */
    public Set<String> getScopes() {
        return scopes;
    }

    @Override
    public int hashCode() {
        return Objects.hash(vendorId, accessToken, expiresOn, providerUserId, scopes);
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj)
            return true;
        if (obj == null || getClass() != obj.getClass())
            return false;
        OAuthAccessToken other = (OAuthAccessToken) obj;
        return Objects.equals(vendorId, other.vendorId)
                && Objects.equals(accessToken, other.accessToken)
                && Objects.equals(expiresOn, other.expiresOn)
                && Objects.equals(providerUserId, other.providerUserId)
                && Objects.equals(scopes, other.scopes);
    }

    @Override
    public String toString() {
        return "OAuthAccessToken [vendorId=" + vendorId + ", accessToken=" + accessToken + 
                ", expiresOn=" + expiresOn + ", providerUserId=" + providerUserId + ", scopes=[" +
                BridgeUtils.COMMA_SPACE_JOINER.join(scopes) +"]]";
    }
}
