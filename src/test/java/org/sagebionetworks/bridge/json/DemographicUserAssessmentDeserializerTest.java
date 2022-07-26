package org.sagebionetworks.bridge.json;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertNull;

import java.util.HashMap;

import org.sagebionetworks.bridge.models.studies.Demographic;
import org.sagebionetworks.bridge.models.studies.DemographicUser;
import org.sagebionetworks.bridge.models.studies.DemographicUserAssessment;
import org.sagebionetworks.bridge.models.studies.DemographicValue;
import org.testng.annotations.Test;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.exc.MismatchedInputException;
import com.google.common.collect.ImmutableList;

@Test
public class DemographicUserAssessmentDeserializerTest {
    @Test
    public void deserializeNull() throws JsonProcessingException, JsonMappingException {
        assertNull(new ObjectMapper().readValue("null", DemographicUserAssessment.class));
    }

    @Test(expectedExceptions = MismatchedInputException.class)
    public void deserializeArray() throws JsonProcessingException, JsonMappingException {
        new ObjectMapper().readValue("[]", DemographicUserAssessment.class);
    }

    @Test
    public void deserializeEmpty() throws JsonProcessingException, JsonMappingException {
        DemographicUser demographicUser = new DemographicUser(null, null, null, null, new HashMap<>());

        assertEquals(new ObjectMapper().readValue("{}", DemographicUserAssessment.class).getDemographicUser()
                .toString(),
                demographicUser.toString());
    }

    @Test
    public void deserializeNoSteps() throws JsonProcessingException, JsonMappingException {
        DemographicUser demographicUser = new DemographicUser(null, null, null, null, new HashMap<>());

        assertEquals(new ObjectMapper().readValue("{\"stepHistory\": []}", DemographicUserAssessment.class)
                .getDemographicUser().toString(),
                demographicUser.toString());
    }

    @Test
    public void deserializeNoAnswerType() throws JsonProcessingException, JsonMappingException {
        
    }

    @Test
    public void deserializeNoType() throws JsonProcessingException, JsonMappingException {

    }

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
