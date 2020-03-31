package org.sagebionetworks.bridge.validators;

import static org.testng.Assert.assertEquals;

import java.util.List;
import java.util.Map;

import com.google.common.collect.Maps;

import org.springframework.validation.Errors;
import org.springframework.validation.MapBindingResult;
import org.testng.annotations.Test;

public class ValidateTest {
    
    @Test
    public void rejectValueDifferentFormattedErrorMessages() {
        Errors errors = getErrors();
        
        errors.rejectValue("myField", "%s is a mess ");
        errors.rejectValue("myField2", "is a mess");
        errors.rejectValue("myField3", " is a mess");
        errors.rejectValue("myField4", " %s is a mess");
        
        Map<String,List<String>> map = Validate.convertErrorsToSimpleMap(errors);
        
        assertEquals(map.get("myField").get(0), "myField is a mess");
        assertEquals(map.get("myField2").get(0), "myField2 is a mess");
        assertEquals(map.get("myField3").get(0), "myField3 is a mess");
        assertEquals(map.get("myField4").get(0), "myField4 is a mess");
    }
    
    private Errors getErrors() { 
        return new MapBindingResult(Maps.newHashMap(), "Object");
    }
}
