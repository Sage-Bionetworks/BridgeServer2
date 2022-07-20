package org.sagebionetworks.bridge.json;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertNull;

import java.util.ArrayList;
import java.util.HashMap;

import org.sagebionetworks.bridge.models.studies.Demographic;
import org.sagebionetworks.bridge.models.studies.DemographicId;
import org.sagebionetworks.bridge.models.studies.DemographicUser;
import org.sagebionetworks.bridge.models.studies.DemographicValue;
import org.testng.annotations.Test;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.exc.MismatchedInputException;

@Test
public class DemographicUserSerializerDeserializerTest {
    @Test
    public void serialize() throws JsonProcessingException {
        DemographicUser demographicUser = new DemographicUser("id1", "appid1", null, "userid1", new HashMap<>());
        Demographic demographicNullUnitsEmptyValues = new Demographic(new DemographicId("id1", "category1"),
                demographicUser, true, new ArrayList<>(), null);
        demographicUser.getDemographics().put("category1", demographicNullUnitsEmptyValues);
        Demographic demographicMultipleValues = new Demographic(new DemographicId("id1", "category2"), demographicUser,
                true, new ArrayList<>(), "units1");
        demographicMultipleValues.getValues().add(new DemographicValue("value1"));
        demographicMultipleValues.getValues().add(new DemographicValue("value2"));
        demographicUser.getDemographics().put("category2", demographicMultipleValues);
        Demographic demographicNotMultipleSelect = new Demographic(new DemographicId("id1", "category3"),
                demographicUser, false, new ArrayList<>(), "units2");
        demographicNotMultipleSelect.getValues().add(new DemographicValue("value3"));
        demographicUser.getDemographics().put("category3", demographicNotMultipleSelect);

        assertEquals(new ObjectMapper().writeValueAsString(demographicUser),
                "{\"userId\":\"userid1\",\"demographics\":{\"category2\":{\"multipleSelect\":true,\"values\":[\"value1\",\"value2\"],\"units\":\"units1\"},\"category3\":{\"multipleSelect\":false,\"values\":[\"value3\"],\"units\":\"units2\"},\"category1\":{\"multipleSelect\":true,\"values\":[]}}}");
    }

    @Test
    public void deserializeNull() throws JsonProcessingException, JsonMappingException {
        assertNull(new ObjectMapper().readValue("null", DemographicUser.class));
    }

    @Test(expectedExceptions = MismatchedInputException.class)
    public void deserializeArray() throws JsonProcessingException, JsonMappingException {
        new ObjectMapper().readValue("[]", DemographicUser.class);
    }

    @Test
    public void deserializeEmpty() throws JsonProcessingException, JsonMappingException {
        DemographicUser demographicUser = new DemographicUser(null, null, null, null, new HashMap<>());

        assertEquals(new ObjectMapper().readValue("{}", DemographicUser.class).toString(), demographicUser.toString());
    }

    @Test
    public void deserializeNoSteps() throws JsonProcessingException, JsonMappingException {
        DemographicUser demographicUser = new DemographicUser(null, null, null, null, new HashMap<>());

        assertEquals(new ObjectMapper().readValue("{\"stepHistory\": []}", DemographicUser.class).toString(),
                demographicUser.toString());
    }

    @Test
    public void deserialize() throws JsonProcessingException, JsonMappingException {
        DemographicUser demographicUser = new DemographicUser(null, null, null, null, new HashMap<>());
        Demographic demographic1 = new Demographic(new DemographicId(null, "category1"), demographicUser, true,
                new ArrayList<>(), null);
        demographic1.getValues().add(new DemographicValue(-7));
        demographic1.getValues().add(new DemographicValue(-6.3));
        demographic1.getValues().add(new DemographicValue(1));
        demographic1.getValues().add(new DemographicValue("foo"));
        demographicUser.getDemographics().put("category1", demographic1);
        Demographic demographic2 = new Demographic(new DemographicId(null, "category2"), demographicUser, false,
                new ArrayList<>(), null);
        demographic2.getValues().add(new DemographicValue(5.3));
        demographicUser.getDemographics().put("category2", demographic2);
        Demographic demographic3 = new Demographic(new DemographicId(null, "category3"), demographicUser, true,
                new ArrayList<>(), null);
        demographic3.getValues().add(new DemographicValue(null));
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
                        "            \"value\": [" +
                        "                null" +
                        "            ]" +
                        "        }" +
                        "    ]" +
                        "}",
                DemographicUser.class).toString(), demographicUser.toString());
    }
}
