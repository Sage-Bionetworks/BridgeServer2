package org.sagebionetworks.bridge.models.surveys;

import static org.testng.Assert.assertEquals;

import org.testng.annotations.Test;

import org.sagebionetworks.bridge.json.BridgeObjectMapper;

public class ConstraintTest {

    @Test
    public void constraintsWithUnits() throws Exception {
        IntegerConstraints c = new IntegerConstraints();
        
        c.setUnit(Unit.MILLILITERS);
        c.setMinValue(1d);
        c.setMaxValue(10000000d);
        
        String json = BridgeObjectMapper.get().writeValueAsString(c);
        c = BridgeObjectMapper.get().readValue(json, IntegerConstraints.class);
        
        assertEquals(c.getDataType(), DataType.INTEGER);
        assertEquals(c.getUnit(), Unit.MILLILITERS);
        assertEquals(c.getMinValue(), new Double(1.0d));
        assertEquals(c.getMaxValue(), new Double(10000000d));
    }
    
}
