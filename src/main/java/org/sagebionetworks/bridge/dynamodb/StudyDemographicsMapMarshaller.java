package org.sagebionetworks.bridge.dynamodb;

import java.util.Map;

import org.sagebionetworks.bridge.models.studies.Demographic;

import com.fasterxml.jackson.core.type.TypeReference;

public class StudyDemographicsMapMarshaller extends StringKeyMapMarshaller<Map<String, Demographic>> {
    private static final TypeReference<Map<String, Map<String, Demographic>>> REF = new TypeReference<Map<String, Map<String, Demographic>>>() {
    };

    @Override
    public TypeReference<Map<String, Map<String, Demographic>>> getTypeReference() {
        return REF;
    }
}
