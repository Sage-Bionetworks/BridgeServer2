package org.sagebionetworks.bridge.dynamodb;

import java.util.Map;

import com.fasterxml.jackson.core.type.TypeReference;

import org.sagebionetworks.bridge.models.apps.OAuthProvider;

public class OAuthProviderMapMarshaller extends StringKeyMapMarshaller<OAuthProvider> {
    private static final TypeReference<Map<String, OAuthProvider>> REF = new TypeReference<Map<String, OAuthProvider>>() {
    };

    @Override
    public TypeReference<Map<String, OAuthProvider>> getTypeReference() {
        return REF;
    }
}
