package org.sagebionetworks.bridge.json;

import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import org.sagebionetworks.bridge.models.schedules.ScheduleType;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertNull;

import com.fasterxml.jackson.databind.ObjectMapper;

public class EnumSerializationTest {
    
    public static class EnumHolder {
        private ScheduleType type;
        public ScheduleType getType() {
            return type;
        }
        public void setType(ScheduleType type) {
            this.type = type;
        }
    }
    
    private ObjectMapper mapper;
    
    @BeforeMethod
    public void before() {
        mapper = BridgeObjectMapper.get();
    }
    
    @Test
    public void canRoundTripObjectWithEnumeration() throws Exception {
        
        EnumHolder holder = new EnumHolder();
        holder.setType(ScheduleType.ONCE);
        
        String json = mapper.writeValueAsString(holder);
        assertEquals(json, "{\"type\":\"once\"}");
        
        holder = mapper.readValue(json, EnumSerializationTest.EnumHolder.class);
        assertEquals(holder.getType(), ScheduleType.ONCE);
    }
    
    @Test
    public void canRoundtripNullEnum() throws Exception {
        EnumHolder holder = new EnumHolder();
        
        String json = mapper.writeValueAsString(holder);
        assertEquals(json, "{}");
        
        holder = mapper.readValue(json, EnumSerializationTest.EnumHolder.class);
        assertNull(holder.getType());
    }
    
    @Test
    public void canDeserializeUpperCaseEnums() throws Exception {
        String json = "{\"type\":\"ONCE\"}";
        
        EnumHolder holder = mapper.readValue(json, EnumSerializationTest.EnumHolder.class);
        assertEquals(holder.getType(), ScheduleType.ONCE);
    }

}
