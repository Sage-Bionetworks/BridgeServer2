package org.sagebionetworks.bridge.hibernate;

import javax.persistence.AttributeConverter;

import org.joda.time.LocalTime;

public class LocalTimeToStringConverter implements AttributeConverter<LocalTime, String> {
    @Override
    public String convertToDatabaseColumn(LocalTime time) {
        return (time == null) ? null : time.toString();
    }
    @Override
    public LocalTime convertToEntityAttribute(String string) {
        return (string == null) ? null : LocalTime.parse(string);
    }
}
