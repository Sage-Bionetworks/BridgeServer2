package org.sagebionetworks.bridge.json;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertNull;

import java.util.ArrayList;
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
        Demographic demographic1 = new Demographic(null, demographicUser, "category1", true, new ArrayList<>(),
                null);
        demographic1.getValues().add(new DemographicValue(-7));
        demographic1.getValues().add(new DemographicValue(-6.3));
        demographic1.getValues().add(new DemographicValue(1));
        demographic1.getValues().add(new DemographicValue("foo"));
        demographicUser.getDemographics().put("category1", demographic1);
        Demographic demographic2 = new Demographic(null, demographicUser, "category2", false, new ArrayList<>(),
                null);
        demographic2.getValues().add(new DemographicValue(5.3));
        demographicUser.getDemographics().put("category2", demographic2);
        Demographic demographic3 = new Demographic(null, demographicUser, "category3", true, new ArrayList<>(),
                null);
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
                DemographicUserAssessment.class).getDemographicUser().toString(),
                demographicUser.toString());
    }
}
