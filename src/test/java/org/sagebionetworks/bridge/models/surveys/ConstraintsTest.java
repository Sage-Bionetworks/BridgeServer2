package org.sagebionetworks.bridge.models.surveys;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertTrue;

import com.fasterxml.jackson.databind.JsonNode;

import org.joda.time.DateTime;
import org.testng.annotations.Test;

import org.sagebionetworks.bridge.json.BridgeObjectMapper;

public class ConstraintsTest {

    /**
     * It is possible to configure the constraints sub-typing information such that dataType 
     * is serialized twice, which is invalid. Test here that this no longer happens.
     * @throws Exception
     */
    @Test
    public void constraintsDoNotSerializeDataTypeTwice() throws Exception {
        DateTimeConstraints constraints = new DateTimeConstraints();
        constraints.setEarliestValue(DateTime.parse("2015-01-01T10:10:10-07:00"));
        constraints.setLatestValue(DateTime.parse("2015-12-31T10:10:10-07:00"));

        String json = BridgeObjectMapper.get().writeValueAsString(constraints);
        assertEquals(json.lastIndexOf("\"dataType\""), json.indexOf("\"dataType\""));
    }
    
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
    
    @Test
    public void requiredIsFalseByDefault() {
        MultiValueConstraints c = new MultiValueConstraints();
        
        JsonNode node = BridgeObjectMapper.get().valueToTree(c);
        assertFalse(node.get("required").booleanValue());
        
        c.setRequired(true);
        node = BridgeObjectMapper.get().valueToTree(c);
        assertTrue(node.get("required").booleanValue());
    }
    

}
