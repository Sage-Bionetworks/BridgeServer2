package org.sagebionetworks.bridge.hibernate;

import java.util.Set;

import javax.persistence.Converter;

import com.fasterxml.jackson.core.type.TypeReference;

@Converter
public class StringSetConverter extends BaseJsonAttributeConverter<Set<String>> {
    private static final TypeReference<Set<String>> TYPE_REFERENCE = new TypeReference<Set<String>>() {};

    @Override
    public String convertToDatabaseColumn(Set<String> set) {
        return serialize(set);
    }
    @Override
    public Set<String> convertToEntityAttribute(String value) {
        return deserialize(value, TYPE_REFERENCE);
    }
}
