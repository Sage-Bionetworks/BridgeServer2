package org.sagebionetworks.bridge.hibernate;

import javax.persistence.AttributeConverter;
import javax.persistence.Converter;

import org.sagebionetworks.bridge.models.studies.StudyIdentifier;
import org.sagebionetworks.bridge.models.studies.StudyIdentifierImpl;

@Converter
public class StudyIdentifierConverter implements AttributeConverter<StudyIdentifier, String> {

    @Override
    public String convertToDatabaseColumn(StudyIdentifier studyId) {
        return studyId.getIdentifier();
    }

    @Override
    public StudyIdentifier convertToEntityAttribute(String value) {
        return new StudyIdentifierImpl(value);
    }
}
