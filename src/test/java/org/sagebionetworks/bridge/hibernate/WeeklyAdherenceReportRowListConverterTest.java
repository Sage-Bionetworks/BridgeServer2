package org.sagebionetworks.bridge.hibernate;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertNull;

import java.util.List;

import org.sagebionetworks.bridge.models.schedules2.adherence.weekly.WeeklyAdherenceReportRow;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import com.google.common.collect.ImmutableList;

public class WeeklyAdherenceReportRowListConverterTest {
    
    private static String SER = "[{\"type\":\"WeeklyAdherenceReportRow\"},{\"type\":\"WeeklyAdherenceReportRow\"}]";

    WeeklyAdherenceReportRowListConverter converter;
    
    @BeforeMethod
    public void beforeMethod() {
        converter = new WeeklyAdherenceReportRowListConverter();
    }
    
    @Test
    public void convertToDatabaseColumn() {
        WeeklyAdherenceReportRow row1 = new WeeklyAdherenceReportRow();
        WeeklyAdherenceReportRow row2 = new WeeklyAdherenceReportRow();
        List<WeeklyAdherenceReportRow> list = ImmutableList.of(row1, row2);
        
        String json = converter.convertToDatabaseColumn(list);

        assertEquals(json, SER);
    }
    
    @Test
    public void convertToEntityAttribute() {
        List<WeeklyAdherenceReportRow> list = converter.convertToEntityAttribute(SER);
        assertEquals(list.size(), 2);
    }
    
    @Test
    public void convertToDatabaseColumn_handlesNull() {
        assertNull( converter.convertToDatabaseColumn(null) );
    }
    
    @Test
    public void convertToEntityAttribute_handlesNull() {
        assertNull( converter.convertToEntityAttribute(null) );
    }

}
