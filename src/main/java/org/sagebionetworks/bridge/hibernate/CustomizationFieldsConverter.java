package org.sagebionetworks.bridge.hibernate;

import java.util.Map;
import java.util.Set;

import javax.persistence.Converter;

import com.fasterxml.jackson.core.type.TypeReference;

import org.sagebionetworks.bridge.models.assessments.config.PropertyInfo;

@Converter
public class CustomizationFieldsConverter extends BaseJsonAttributeConverter<Map<String,Set<PropertyInfo>>> {
    private static final TypeReference<Map<String,Set<PropertyInfo>>> TYPE_REFERENCE = new TypeReference<Map<String,Set<PropertyInfo>>>() {};
    
    @Override
    public String convertToDatabaseColumn(Map<String, Set<PropertyInfo>> map) {
        return serialize(map);
    }

    @Override
    public Map<String, Set<PropertyInfo>> convertToEntityAttribute(String string) {
        return deserialize(string, TYPE_REFERENCE);
    }

}
