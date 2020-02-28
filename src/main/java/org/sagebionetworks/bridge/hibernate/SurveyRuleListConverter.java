package org.sagebionetworks.bridge.hibernate;

import java.util.List;

import javax.persistence.Converter;

import org.sagebionetworks.bridge.models.surveys.SurveyRule;

import com.fasterxml.jackson.core.type.TypeReference;

@Converter
public class SurveyRuleListConverter extends BaseJsonAttributeConverter<List<SurveyRule>> {
    private static final TypeReference<List<SurveyRule>> TYPE_REFERENCE = new TypeReference<List<SurveyRule>>() {};
    @Override
    public String convertToDatabaseColumn(List<SurveyRule> list) {
        return serialize(list);
    }
    @Override
    public List<SurveyRule> convertToEntityAttribute(String value) {
        return deserialize(value, TYPE_REFERENCE);
    }
}
