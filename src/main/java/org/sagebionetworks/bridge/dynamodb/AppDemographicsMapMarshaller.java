package org.sagebionetworks.bridge.dynamodb;

import java.util.Map;

import org.sagebionetworks.bridge.models.studies.Demographic;

import com.fasterxml.jackson.core.type.TypeReference;

public class AppDemographicsMapMarshaller extends StringKeyMapMarshaller<Demographic> {
    private static final TypeReference<Map<String, Demographic>> REF = new TypeReference<Map<String, Demographic>>() {
    };

    @Override
    public TypeReference<Map<String, Demographic>> getTypeReference() {
        return REF;
    }
}
