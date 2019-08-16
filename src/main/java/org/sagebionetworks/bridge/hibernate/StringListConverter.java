package org.sagebionetworks.bridge.hibernate;

import java.util.List;

import javax.persistence.Converter;

import com.fasterxml.jackson.core.type.TypeReference;

@Converter
public class StringListConverter extends BaseJsonAttributeConverter<List<String>> {
    private static final TypeReference<List<String>> TYPE_REFERENCE = new TypeReference<List<String>>() {};
    @Override
    public String convertToDatabaseColumn(List<String> list) {
        return serialize(list);
    }
    @Override
    public List<String> convertToEntityAttribute(String value) {
        return deserialize(value, TYPE_REFERENCE);
    }
}
