package org.sagebionetworks.bridge.hibernate;

import org.joda.time.DateTimeZone;

import javax.persistence.AttributeConverter;
import javax.persistence.Converter;

@Converter
public class DateTimeZoneIdConverter implements AttributeConverter<DateTimeZone, String> {
    @Override
    public String convertToDatabaseColumn(DateTimeZone dateTimeZone) {
        if (dateTimeZone == null) return null;
        return dateTimeZone.getID();
    }

    @Override
    public DateTimeZone convertToEntityAttribute(String s) {
        if (s == null) return null;
        return DateTimeZone.forID(s);
    }
}
