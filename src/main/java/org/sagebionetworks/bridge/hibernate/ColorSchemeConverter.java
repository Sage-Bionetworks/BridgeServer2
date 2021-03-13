package org.sagebionetworks.bridge.hibernate;

import javax.persistence.Converter;

import com.fasterxml.jackson.core.type.TypeReference;

import org.sagebionetworks.bridge.models.assessments.ColorScheme;

@Converter
public class ColorSchemeConverter extends BaseJsonAttributeConverter<ColorScheme> {

    private static final TypeReference<ColorScheme> TYPE_REF = new TypeReference<ColorScheme>() {};
    
    @Override
    public String convertToDatabaseColumn(ColorScheme colorScheme) {
        return serialize(colorScheme);
    }
    @Override
    public ColorScheme convertToEntityAttribute(String json) {
        return deserialize(json, TYPE_REF);
    }
}
