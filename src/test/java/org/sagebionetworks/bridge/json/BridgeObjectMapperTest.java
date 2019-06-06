package org.sagebionetworks.bridge.json;

import org.testng.annotations.Test;

import org.sagebionetworks.bridge.TestUtils;
import org.sagebionetworks.bridge.models.subpopulations.ConsentSignature;
import org.sagebionetworks.bridge.models.surveys.Survey;
import org.sagebionetworks.bridge.models.surveys.TestSurvey;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertTrue;

import com.fasterxml.jackson.databind.JsonNode;

public class BridgeObjectMapperTest {

    @Test
    public void addsTypeField() {
        final class NotAnnotated {
            @SuppressWarnings("unused") public String field;
        }
        
        @BridgeTypeName("AnnotationName")
        final class Annotated {
            @SuppressWarnings("unused") public String field;
        }
        
        BridgeObjectMapper mapper = new BridgeObjectMapper();
        
        JsonNode node = mapper.valueToTree(new NotAnnotated());
        assertEquals(node.get("type").asText(), "NotAnnotated", "Type is NotAnnotated");
        
        node = mapper.valueToTree(new Annotated());
        assertEquals(node.get("type").asText(), "AnnotationName", "Type is AnnotationName");
    }
    
    // TODO: The serializer doubles up the type property on objects that have a filter. I 
    // have not found a way to prevent this, that doesn't break the object mapper for many, 
    // many objects. 
    @Test
    public void doesNotAddTypeFieldTwice() throws Exception {
        ConsentSignature signature = new ConsentSignature.Builder().withName("Jack Aubrey").withBirthdate("1970-12-02").build();
        String json = BridgeObjectMapper.get().writeValueAsString(signature);
        assertTrue(json.indexOf("type") == json.lastIndexOf("type") && json.indexOf("type") > -1);

        json = ConsentSignature.SIGNATURE_WRITER.writeValueAsString(signature);
        assertTrue(json.indexOf("type") == json.lastIndexOf("type") && json.indexOf("type") > -1);
        
        // This object does not have a filter, should still write a type property
        json = BridgeObjectMapper.get().writeValueAsString(TestUtils.getActivity1());
        
        String prop = "\"type\":\"Activity\"";
        assertTrue(json.indexOf(prop) == json.lastIndexOf(prop) && json.indexOf(prop) > -1);
        
        json = BridgeObjectMapper.get().writeValueAsString(new TestSurvey(BridgeObjectMapperTest.class, true));
        prop = "\"type\":\"Survey\"";
        assertTrue(json.indexOf(prop) == json.lastIndexOf(prop) && json.indexOf(prop) > -1);
    }
    
    @Test
    public void doesNotOverrideExistingTypeField() {
        @BridgeTypeName("WrongName")
        final class NotAnnotated {
            private final String type;
            public NotAnnotated(String type) {
                this.type = type;
            }
            @SuppressWarnings("unused") public String getType() {
                return type;
            }
        }
        
        BridgeObjectMapper mapper = new BridgeObjectMapper();
        
        JsonNode node = mapper.valueToTree(new NotAnnotated("ThisIsTheName"));
        assertEquals(node.get("type").asText(), "ThisIsTheName", "Type is ThisIsTheName");
    }
        
    /**
     * It should be possible to send a null for any field, including fields that are deserialized into 
     * primitive longs, but this causes an exception in Jackson during deserialization. Instead, use the 
     * default field value of 0L and then validate whether or not this value is valid (in the case of 
     * our system, we ignore all such values sent from clients in favor of setting the value on the server, 
     * but that doesn't mean the client won't send them, set to null even).
     * @throws Exception
     */
    @Test
    public void canDeserializeTimestampNullsForPrimitiveLongFields() throws Exception {
        String json = "{\"createdOn\":null,\"modifiedOn\":null}";
        
        Survey survey = new BridgeObjectMapper().readValue(json, Survey.class);
        assertEquals(survey.getCreatedOn(), 0);
        assertEquals(survey.getModifiedOn(), 0);
        
        // No field works as well
        json = "{}";
        survey = new BridgeObjectMapper().readValue(json, Survey.class);
        assertEquals(survey.getCreatedOn(), 0);
        assertEquals(survey.getModifiedOn(), 0);
        
        // If you were to supply zeroes, you do get a deserialization error, as you would any other 
        // value not recognizable as a date.
    }
}
