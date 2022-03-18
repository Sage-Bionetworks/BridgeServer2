package org.sagebionetworks.bridge.hibernate;

import com.fasterxml.jackson.core.type.TypeReference;

import org.sagebionetworks.bridge.models.apps.Exporter3Configuration;

public class Exporter3ConfigurationConverter extends BaseJsonAttributeConverter<Exporter3Configuration> {
    private static final TypeReference<Exporter3Configuration> TYPE_REF = new TypeReference<Exporter3Configuration>(){};

    @Override
    public String convertToDatabaseColumn(Exporter3Configuration exporter3Configuration) {
        return serialize(exporter3Configuration);
    }

    @Override
    public Exporter3Configuration convertToEntityAttribute(String json) {
        return deserialize(json, TYPE_REF);
    }
}
