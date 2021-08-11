package org.sagebionetworks.bridge.hibernate;

import org.joda.time.DateTimeZone;

import javax.persistence.AttributeConverter;
import javax.persistence.Converter;

@Converter
public class DateTimeZoneIdConverter implements AttributeConverter<DateTimeZone, String> {
    @Override
    public String convertToDatabaseColumn(DateTimeZone dateTimeZone) {
        return dateTimeZone.getID();
    }

    @Override
    public DateTimeZone convertToEntityAttribute(String s) {
        return DateTimeZone.forID(s);
    }
}
