package org.sagebionetworks.bridge.json;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertNull;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

import org.sagebionetworks.bridge.json.DemographicUserAssessmentDeserializer.DemographicAssessmentResultStep;
import org.sagebionetworks.bridge.json.DemographicUserAssessmentDeserializer.DemographicAssessmentResults;
import org.sagebionetworks.bridge.models.studies.Demographic;
import org.sagebionetworks.bridge.models.studies.DemographicUser;
import org.sagebionetworks.bridge.models.studies.DemographicUserAssessment;
import org.sagebionetworks.bridge.models.studies.DemographicValue;
import org.testng.annotations.Test;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.exc.MismatchedInputException;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;

@Test
public class DemographicUserAssessmentDeserializerTest {
    /**
     * Tests whether null is deserialized correctly.
     */
    @Test
    public void deserializeNull() throws JsonProcessingException, JsonMappingException {
        assertNull(new ObjectMapper().readValue("null", DemographicUserAssessment.class));
    }

    /**
     * Tests whether an array throws an exception when deserialized.
     */
    @Test(expectedExceptions = MismatchedInputException.class)
    public void deserializeArray() throws JsonProcessingException, JsonMappingException {
        new ObjectMapper().readValue("[]", DemographicUserAssessment.class);
    }

    /**
     * Tests whether deserializing an empty object will succeed but result in an
     * empty DemographicUser.
     */
    @Test
    public void deserializeEmpty() throws JsonProcessingException, JsonMappingException {
        DemographicUser demographicUser = new DemographicUser(null, null, null, null, new HashMap<>());

        assertEquals(new ObjectMapper().readValue("{}", DemographicUserAssessment.class).getDemographicUser()
                .toString(),
                demographicUser.toString());
    }

    /**
     * Tests whether deserializing JSON with a null stepHistory will succeed but
     * result in a DemographicUser without demographics.
     */
    @Test
    public void deserializeNullStepHistory() throws JsonMappingException, JsonProcessingException {
        DemographicUser demographicUser = new DemographicUser(null, null, null, null, new HashMap<>());

        assertEquals(new ObjectMapper().readValue("{\"stepHistory\": null}", DemographicUserAssessment.class)
                .getDemographicUser().toString(), demographicUser.toString());
    }

    /**
     * Tests whether deserializing JSON without a stepHistory will succeed but
     * result in a DemographicUser without demographics.
     */
    @Test
    public void deserializeNoSteps() throws JsonProcessingException, JsonMappingException {
        DemographicUser demographicUser = new DemographicUser(null, null, null, null, new HashMap<>());

        assertEquals(new ObjectMapper().readValue("{\"stepHistory\": []}", DemographicUserAssessment.class)
                .getDemographicUser().toString(),
                demographicUser.toString());
    }

    /**
     * Tests whether deserializing JSON will ignore null steps in the stepHistory
     * and nothing else.
     */
    @Test
    public void deserializeNullStep() throws JsonProcessingException, JsonMappingException {
        DemographicUser demographicUser = new DemographicUser(null, null, null, null, new HashMap<>());
        Demographic demographic = new Demographic(null, demographicUser, "category1", false,
                ImmutableList.of(new DemographicValue("foo")), null);
        demographicUser.getDemographics().put("category1", demographic);

        assertEquals(new ObjectMapper().readValue(
                "{" +
                        "    \"stepHistory\": [" +
                        "        null," +
                        "        {" +
                        "            \"identifier\": \"category1\"," +
                        "            \"answerType\": {" +
                        "                \"type\": \"string\"" +
                        "            }," +
                        "            \"value\": \"foo\"" +
                        "        }," +
                        "        null" +
                        "    ]" +
                        "}",
                DemographicUserAssessment.class).getDemographicUser().toString(),
                demographicUser.toString());
    }

    /**
     * Tests whether deserializing JSON with identifier as null results in an error.
     */
    @Test(expectedExceptions = JsonMappingException.class)
    public void deserializeNullIdentifier() throws JsonProcessingException, JsonMappingException {
        new ObjectMapper().readValue(
                "{" +
                        "    \"stepHistory\": [" +
                        "        {" +
                        "            \"identifier\": null," +
                        "            \"answerType\": {" +
                        "                \"type\": \"string\"" +
                        "            }," +
                        "            \"value\": \"foo\"" +
                        "        }" +
                        "    ]" +
                        "}",
                DemographicUserAssessment.class);
    }

    /**
     * Tests whether deserializing JSON will ignore null values and nothing else.
     */
    @Test
    public void deserializeNullValue() throws JsonProcessingException, JsonMappingException {
        DemographicUser demographicUser = new DemographicUser(null, null, null, null, new HashMap<>());
        Demographic demographic1 = new Demographic(null, demographicUser, "category1", true,
                ImmutableList.of(new DemographicValue("foo")), null);
        Demographic demographic2 = new Demographic(null, demographicUser, "category2", false,
                ImmutableList.of(), null);
        demographicUser.getDemographics().put("category1", demographic1);
        demographicUser.getDemographics().put("category2", demographic2);

        assertEquals(new ObjectMapper().readValue(
                "{" +
                        "    \"stepHistory\": [" +
                        "        {" +
                        "            \"identifier\": \"category1\"," +
                        "            \"answerType\": {" +
                        "                \"type\": \"array\"" +
                        "            }," +
                        "            \"value\": [" +
                        "                null," +
                        "                \"foo\"," +
                        "                null" +
                        "            ]" +
                        "        }," +
                        "        {" +
                        "            \"identifier\": \"category2\"," +
                        "            \"answerType\": {" +
                        "                \"type\": \"string\"" +
                        "            }," +
                        "            \"value\": null" +
                        "        }" +
                        "    ]" +
                        "}",
                DemographicUserAssessment.class).getDemographicUser().toString(),
                demographicUser.toString());
    }

    /**
     * Tests whether null inner values of DemographicValues will be removed. Not
     * sure if this case is actually possible.
     */
    @Test
    public void deserializeNullInnerValue() throws IOException {
        JsonParser mockParser = mock(JsonParser.class);
        DemographicAssessmentResults mockResults = mock(DemographicAssessmentResults.class);
        DemographicAssessmentResultStep step = new DemographicAssessmentResultStep();
        List<DemographicValue> values = new ArrayList<>();
        values.add(new DemographicValue(null));
        values.add(new DemographicValue("foo"));
        values.add(new DemographicValue(null));
        step.setValue(values);
        step.setIdentifier("id");
        step.setAnswerType(ImmutableMap.of("type", "array"));
        when(mockParser.readValueAs(DemographicAssessmentResults.class)).thenReturn(mockResults);
        when(mockResults.getStepHistory()).thenReturn(ImmutableList.of(step));

        DemographicUserAssessmentDeserializer deserializer = new DemographicUserAssessmentDeserializer();
        deserializer.deserialize(mockParser, null);

        assertEquals(values.size(), 1);
        assertEquals(values.get(0).getValue(), "foo");
    }

    /**
     * Tests whether deserializing JSON without answerType results in an error
     * (cannot know whether value is an array or not).
     */
    @Test(expectedExceptions = JsonMappingException.class)
    public void deserializeNoAnswerType() throws JsonProcessingException, JsonMappingException {
        new ObjectMapper().readValue("{\"stepHistory\":[{\"identifier\":\"category1\",\"value\":5}]}",
                DemographicUserAssessment.class);
    }

    /**
     * Tests whether deserializing JSON without type results in an error
     * (cannot know whether value is an array or not).
     */
    @Test(expectedExceptions = JsonMappingException.class)
    public void deserializeNoType() throws JsonProcessingException, JsonMappingException {
        new ObjectMapper().readValue(
                "{\"stepHistory\": [{\"identifier\":\"category1\",\"value\":5,\"answerType\":{}}]}",
                DemographicUserAssessment.class);
    }

    /**
     * Tests deserializing a valid case with unknown fields.
     */
    @Test
    public void deserialize() throws JsonProcessingException, JsonMappingException {
        DemographicUser demographicUser = new DemographicUser(null, null, null, null, new HashMap<>());
        Demographic demographic1 = new Demographic(null, demographicUser, "category1", true,
                ImmutableList.of(new DemographicValue(-7), new DemographicValue(-6.3), new DemographicValue(1),
                        new DemographicValue("foo")),
                null);
        demographicUser.getDemographics().put("category1", demographic1);
        Demographic demographic2 = new Demographic(null, demographicUser, "category2", false,
                ImmutableList.of(new DemographicValue(5.3)), null);
        demographicUser.getDemographics().put("category2", demographic2);
        Demographic demographic3 = new Demographic(null, demographicUser, "category3", true, ImmutableList.of(),
                null);
        demographicUser.getDemographics().put("category3", demographic3);

        assertEquals(new ObjectMapper().readValue(
                "{" +
                        "    \"unknown field\": null," +
                        "    \"stepHistory\": [" +
                        "        {" +
                        "            \"unknown field\": null," +
                        "            \"identifier\": \"category1\"," +
                        "            \"answerType\": {" +
                        "                \"type\": \"ArRaY\"," +
                        "                \"unknown field\": null" +
                        "            }," +
                        "            \"value\": [" +
                        "                -7," +
                        "                -6.3," +
                        "                1," +
                        "                \"foo\"" +
                        "            ]" +
                        "        }," +
                        "        {" +
                        "            \"unknown field\": null," +
                        "            \"identifier\": \"category2\"," +
                        "            \"answerType\": {" +
                        "                \"type\": \"NuMbEr\"," +
                        "                \"unknown field\": null" +
                        "            }," +
                        "            \"value\": 5.3" +
                        "        }," +
                        "        {" +
                        "            \"unknown field\": null," +
                        "            \"identifier\": \"category3\"," +
                        "            \"answerType\": {" +
                        "                \"type\": \"ArRaY\"," +
                        "                \"unknown field\": null" +
                        "            }," +
                        "            \"value\": []" +
                        "        }" +
                        "    ]" +
                        "}",
                DemographicUserAssessment.class).getDemographicUser().toString(),
                demographicUser.toString());
    }
}
