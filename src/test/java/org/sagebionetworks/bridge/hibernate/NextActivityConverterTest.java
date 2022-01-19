package org.sagebionetworks.bridge.hibernate;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertNull;

import javax.persistence.PersistenceException;

import org.sagebionetworks.bridge.json.BridgeObjectMapper;
import org.sagebionetworks.bridge.models.schedules2.adherence.eventstream.EventStreamDay;
import org.sagebionetworks.bridge.models.schedules2.adherence.weekly.NextActivity;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

public class NextActivityConverterTest {
    static final NextActivityConverter CONVERTER = new NextActivityConverter();
    
    NextActivity activity;
    
    @BeforeMethod
    public void beforeMethod( ) {
        EventStreamDay day = new EventStreamDay();
        day.setLabel("label");
        
        activity = NextActivity.create(day);
    }
    
    @Test
    public void convertToDatabaseColumn() throws Exception {
        String json = CONVERTER.convertToDatabaseColumn(activity);
        NextActivity deser = BridgeObjectMapper.get().readValue(json, NextActivity.class);
        
        assertEquals(deser.getLabel(), "label");
    }
    
    @Test
    public void convertToDatabaseColumnNull() throws Exception { 
        String json = CONVERTER.convertToDatabaseColumn(null);
        assertNull(json);
    }

    @Test
    public void convertToEntityAttribute() throws Exception {
        String json = BridgeObjectMapper.get().writeValueAsString(activity);
        
        NextActivity deser = CONVERTER.convertToEntityAttribute(json);
        
        assertEquals(deser.getLabel(), "label");
    }
    
    @Test
    public void convertToEntityAttributeNull() throws Exception {
        NextActivity deser = CONVERTER.convertToEntityAttribute(null);
        assertNull(deser);
    }

    @Test(expectedExceptions = PersistenceException.class)
    public void convertToEntityAttributeJsonErrorThrows() throws Exception {
        CONVERTER.convertToEntityAttribute("not json");
    }
}
