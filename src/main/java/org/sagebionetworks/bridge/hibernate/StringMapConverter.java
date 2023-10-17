package org.sagebionetworks.bridge.hibernate;

import java.util.Map;
import javax.persistence.Converter;

import com.fasterxml.jackson.core.type.TypeReference;

/** Hibernate converter that serializes a string map to JSON for storage in SQL. */
@Converter
public class StringMapConverter extends BaseJsonAttributeConverter<Map<String, String>> {
    static final TypeReference<Map<String, String>> TYPE_REFERENCE = new TypeReference<Map<String, String>>() {};

    @Override
    public String convertToDatabaseColumn(Map<String, String> map) {
        return serialize(map);
    }

    @Override
    public Map<String, String> convertToEntityAttribute(String value) {
        return deserialize(value, TYPE_REFERENCE);
    }
}
