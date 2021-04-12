package org.sagebionetworks.bridge.hibernate;

import javax.persistence.AttributeConverter;

import org.joda.time.LocalDate;

public class LocalDateToStringConverter implements AttributeConverter<LocalDate, String> {
    @Override
    public String convertToDatabaseColumn(LocalDate date) {
        return (date == null) ? null : date.toString();
    }

    @Override
    public LocalDate convertToEntityAttribute(String string) {
        return (string == null) ? null : LocalDate.parse(string); 
    }
}
