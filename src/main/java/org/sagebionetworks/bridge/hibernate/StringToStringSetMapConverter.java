package org.sagebionetworks.bridge.hibernate;

import java.util.Map;
import java.util.Set;

import javax.persistence.Converter;

import com.fasterxml.jackson.core.type.TypeReference;

@Converter
public class StringToStringSetMapConverter extends BaseJsonAttributeConverter<Map<String,Set<String>>> {
    private static final TypeReference<Map<String,Set<String>>> TYPE_REFERENCE = new TypeReference<Map<String,Set<String>>>() {};
    
    @Override
    public String convertToDatabaseColumn(Map<String, Set<String>> map) {
        return serialize(map);
    }

    @Override
    public Map<String, Set<String>> convertToEntityAttribute(String string) {
        return deserialize(string, TYPE_REFERENCE);
    }

}
