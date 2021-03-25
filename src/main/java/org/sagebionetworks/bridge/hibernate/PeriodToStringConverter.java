package org.sagebionetworks.bridge.hibernate;

import javax.persistence.AttributeConverter;
import javax.persistence.Converter;

import org.joda.time.Period;

@Converter
public class PeriodToStringConverter implements AttributeConverter<Period, String> {
    @Override
    public String convertToDatabaseColumn(Period period) {
        // these converters do need to handle nulls
        return (period == null) ? null : period.toString();
    }
    @Override
    public Period convertToEntityAttribute(String string) {
        // these converters do need to handle nulls
        return (string == null) ? null : Period.parse(string);
    }
}
