package org.sagebionetworks.bridge.json;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertNull;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;

import org.sagebionetworks.bridge.models.studies.Demographic;
import org.sagebionetworks.bridge.models.studies.DemographicUser;
import org.sagebionetworks.bridge.models.studies.DemographicValue;
import org.testng.annotations.Test;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.exc.MismatchedInputException;

public class DemographicUserSerializerDeserializerTest {
    @Test
    public void serialize() throws JsonProcessingException {
        DemographicUser demographicUser = new DemographicUser("id1", "appid1", null, "userid1",
                new HashMap<>());
        Demographic demographicNullUnitsEmptyValues = new Demographic("id1", demographicUser, "category1", true,
                new ArrayList<>(), null);
        demographicUser.getDemographics().put("category1", demographicNullUnitsEmptyValues);
        Demographic demographicMultipleValues = new Demographic("id2", demographicUser, "category2", true,
                new ArrayList<>(), "units1");
        demographicMultipleValues.getValues().add(new DemographicValue("value1"));
        demographicMultipleValues.getValues().add(new DemographicValue("value2"));
        demographicUser.getDemographics().put("category2", demographicMultipleValues);
        Demographic demographicNotMultipleSelect = new Demographic("id3", demographicUser, "category3", false,
                new ArrayList<>(), "units2");
        demographicNotMultipleSelect.getValues().add(new DemographicValue("value3"));
        demographicUser.getDemographics().put("category3", demographicNotMultipleSelect);

        // {
        //     "userId": "userid1",
        //     "demographics": {
        //         "category2": {
        //             "id": "id2",
        //             "multipleSelect": true,
        //             "units": "units1",
        //             "values": [
        //                 "value1",
        //                 "value2"
        //             ]
        //         },
        //         "category3": {
        //             "id": "id3",
        //             "multipleSelect": false,
        //             "units": "units2",
        //             "values": [
        //                 "value3"
        //             ]
        //         },
        //         "category1": {
        //             "id": "id1",
        //             "multipleSelect": true,
        //             "values": []
        //         }
        //     }
        // }
        assertEquals(new ObjectMapper().writeValueAsString(demographicUser),
                "{\"userId\":\"userid1\",\"demographics\":{\"category2\":{\"id\":\"id2\",\"multipleSelect\":true,\"units\":\"units1\",\"values\":[\"value1\",\"value2\"]},\"category3\":{\"id\":\"id3\",\"multipleSelect\":false,\"units\":\"units2\",\"values\":[\"value3\"]},\"category1\":{\"id\":\"id1\",\"multipleSelect\":true,\"values\":[]}}}");
    }

    @Test(expectedExceptions = MismatchedInputException.class)
    public void deserializeArray() throws JsonProcessingException, JsonMappingException {
        new ObjectMapper().readValue("[]", DemographicUser.class);
    }

    @Test
    public void deserializeEmpty() throws JsonProcessingException, JsonMappingException {
        DemographicUser demographicUser = new DemographicUser(null, null, null, null, null);

        assertEquals(new ObjectMapper().readValue("{}", DemographicUser.class).toString(), demographicUser.toString());
    }

    @Test
    public void deserializeNoDemographics() throws JsonProcessingException, JsonMappingException {
        DemographicUser demographicUser = new DemographicUser(null, null, null, null, new HashMap<>());

        assertEquals(new ObjectMapper().readValue("{\"demographics\":{}}", DemographicUser.class).toString(),
                demographicUser.toString());
    }

    @Test
    public void deserializeNoMultipleSelect() throws JsonProcessingException, JsonMappingException {
        new ObjectMapper().readValue("{\"userId\":\"testuserid\",\"demographics\":{\"category1\":{\"values\":[5]}}}",
                DemographicUser.class);
    }

    @Test
    public void deserialize() throws JsonProcessingException, JsonMappingException {
        DemographicUser demographicUser = new DemographicUser(null, null, null, "testuserid", new LinkedHashMap<>());
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
                "testunits");
        demographic3.getValues().add(new DemographicValue(null));
        demographicUser.getDemographics().put("category3", demographic3);

        assertEquals(new ObjectMapper().configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false).readValue(
                "{" +
                        "    \"unknown field\": null," +
                        "    \"userId\": \"testuserid\"," +
                        "    \"demographics\": {" +
                        "        \"category1\": {" +
                        "            \"unknown field\": null," +
                        "            \"multipleSelect\": true," +
                        "            \"values\": [" +
                        "                -7," +
                        "                -6.3," +
                        "                1," +
                        "                \"foo\"" +
                        "            ]," +
                        "            \"units\": null" +
                        "        }," +
                        "        \"category2\": {" +
                        "            \"unknown field\": null," +
                        "            \"multipleSelect\": false," +
                        "            \"values\": [" +
                        "                5.3" +
                        "            ]" +
                        "        }," +
                        "        \"category3\": {" +
                        "            \"unknown field\": null," +
                        "            \"multipleSelect\": true," +
                        "            \"values\": [" +
                        "                null" +
                        "            ]," +
                        "            \"units\": \"testunits\"" +
                        "        }" +
                        "    }" +
                        "}",
                DemographicUser.class).toString(),
                demographicUser.toString());
    }
}
