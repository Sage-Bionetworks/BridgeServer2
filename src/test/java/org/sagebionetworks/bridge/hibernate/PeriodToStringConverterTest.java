package org.sagebionetworks.bridge.hibernate;

import static org.testng.Assert.assertEquals;

import org.joda.time.Period;
import org.mockito.Mockito;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

public class PeriodToStringConverterTest extends Mockito {
    
    PeriodToStringConverter converter;

    @BeforeMethod
    public void beforeMethod() {
        converter = new PeriodToStringConverter();
    }
    
    @Test
    public void convertToDatabaseColumn() {
        String retValue = converter.convertToDatabaseColumn(Period.parse("P2Y"));
        assertEquals(retValue, "P2Y");
    }
    
    @Test
    public void convertToEntityAttribute() {
        Period retValue = converter.convertToEntityAttribute("P2Y3DT10H");
        assertEquals(retValue.getYears(), 2);
        assertEquals(retValue.getDays(), 3);
        assertEquals(retValue.getHours(), 10);
    }

}
