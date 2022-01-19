package org.sagebionetworks.bridge.hibernate;

import static org.sagebionetworks.bridge.hibernate.EventStreamDayMapConverter.TYPE_REF;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertNull;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.persistence.PersistenceException;

import org.sagebionetworks.bridge.json.BridgeObjectMapper;
import org.sagebionetworks.bridge.models.schedules2.adherence.eventstream.EventStreamDay;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import com.google.common.collect.ImmutableList;

public class EventStreamDayMapConverterTest {
    static final EventStreamDayMapConverter CONVERTER = new EventStreamDayMapConverter();
    
    Map<Integer, List<EventStreamDay>> map;
    
    @BeforeMethod
    public void beforeMethod() {
        EventStreamDay day = new EventStreamDay();
        day.setLabel("label");
        map = new HashMap<>();
        map.put(Integer.valueOf(1), ImmutableList.of(day));
    }
    
    @Test
    public void convertToDatabaseColumn() throws Exception {
        String json = CONVERTER.convertToDatabaseColumn(map);
        Map<Integer, List<EventStreamDay>> deser = BridgeObjectMapper.get().readValue(json, TYPE_REF);
        
        assertEquals(deser.get(1).get(0).getLabel(), "label");
    }

    @Test
    public void convertToDatabaseColumnNull() throws Exception { 
        String json = CONVERTER.convertToDatabaseColumn(null);
        assertNull(json);
    }
    
    @Test
    public void convertToEntityAttribute() throws Exception {
        String json = BridgeObjectMapper.get().writeValueAsString(map);
        
        Map<Integer, List<EventStreamDay>> deser = CONVERTER.convertToEntityAttribute(json);
        
        assertEquals(deser.get(1).get(0).getLabel(), "label");
    }

    @Test
    public void convertToEntityAttributeNull() throws Exception {
        Map<Integer, List<EventStreamDay>> deser = CONVERTER.convertToEntityAttribute(null);
        assertNull(deser);
    }

    @Test(expectedExceptions = PersistenceException.class)
    public void convertToEntityAttributeJsonErrorThrows() throws Exception {
        CONVERTER.convertToEntityAttribute("not json");
    }
}
