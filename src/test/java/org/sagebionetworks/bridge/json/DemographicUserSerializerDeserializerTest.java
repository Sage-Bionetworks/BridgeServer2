package org.sagebionetworks.bridge.json;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertTrue;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.sagebionetworks.bridge.models.studies.Demographic;
import org.sagebionetworks.bridge.models.studies.DemographicUser;
import org.sagebionetworks.bridge.models.studies.DemographicValue;
import org.testng.annotations.Test;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.exc.MismatchedInputException;
import com.google.common.collect.ImmutableList;

public class DemographicUserSerializerDeserializerTest {
    /**
     * Tests whether a DemographicUser is serialized correctly.
     */
    @Test
    public void serialize() throws JsonProcessingException {
        DemographicUser demographicUser = new DemographicUser("id1", "appid1", "studyid", "userid1", new HashMap<>());
        Demographic demographicNullUnitsEmptyValues = new Demographic("id1", demographicUser, "category1", true,
                ImmutableList.of(), null);
        demographicUser.getDemographics().put("category1", demographicNullUnitsEmptyValues);
        Demographic demographicMultipleValues = new Demographic("id2", demographicUser, "category2", true,
                ImmutableList.of(new DemographicValue("value1"), new DemographicValue("value2")), "units1");
        demographicUser.getDemographics().put("category2", demographicMultipleValues);
        Demographic demographicNotMultipleSelect = new Demographic("id3", demographicUser, "category3", false,
                ImmutableList.of(new DemographicValue("value3")), "units2");
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

    /**
     * Tests whether deserializing an array results in an error.
     */
    @Test(expectedExceptions = MismatchedInputException.class)
    public void deserializeArray() throws JsonProcessingException, JsonMappingException {
        new ObjectMapper().readValue("[]", DemographicUser.class);
    }

    /**
     * Tests whether deserializing an empty object succeeds but results in an empty
     * DemographicUser.
     */
    @Test
    public void deserializeEmpty() throws JsonProcessingException, JsonMappingException {
        DemographicUser demographicUser = new DemographicUser(null, null, null, null, null);

        assertEquals(new ObjectMapper().readValue("{}", DemographicUser.class).toString(), demographicUser.toString());
    }

    /**
     * Tests whether deserializing without demographics succeeds and results in a
     * DemographicUser without demographics.
     */
    @Test
    public void deserializeNoDemographics() throws JsonProcessingException, JsonMappingException {
        DemographicUser demographicUser = new DemographicUser(null, null, null, null, new HashMap<>());

        assertEquals(new ObjectMapper().readValue("{\"demographics\":{}}", DemographicUser.class).toString(),
                demographicUser.toString());
    }

    /**
     * Tests whether deserializing with null demographics succeeds and results in a
     * DemographicUser with null demographics (if this actually happens it should be
     * rejected by the validator but the deserialization should succeed).
     */
    @Test
    public void deserializeNullDemographics() throws JsonProcessingException, JsonMappingException {
        DemographicUser demographicUser = new DemographicUser(null, null, null, null, null);

        assertEquals(new ObjectMapper().readValue("{\"demographics\":null}", DemographicUser.class).toString(),
                demographicUser.toString());
    }

    /**
     * Tests whether deserializing with a demographics map that contains a null
     * value succeeds (if this actually happens it should be rejected by the
     * validator but the deserialization should succeed).
     */
    @Test
    public void deserializeNullDemographicsMapValue() throws JsonMappingException, JsonProcessingException {
        DemographicUser demographicUser = new DemographicUser(null, null, null, null, new HashMap<>());
        demographicUser.getDemographics().put("category1", null);

        assertEquals(new ObjectMapper().readValue("{\"demographics\":{\"category1\":null}}", DemographicUser.class)
                .toString(), demographicUser.toString());
    }

    /**
     * Tests whether deserializing with null list of values succeeds and results in
     * a Demographic with a null list of values (if this actually happens it should
     * be rejected by the validator but the deserialization should succeed).
     */
    @Test
    public void deserializeNullDemographicValues() throws JsonProcessingException, JsonMappingException {
        DemographicUser demographicUser = new DemographicUser(null, null, null, null, new HashMap<>());
        Demographic demographic = new Demographic(null, demographicUser, "category1", true, null, null);
        demographicUser.getDemographics().put("category1", demographic);

        assertEquals(new ObjectMapper()
                .readValue("{\"demographics\":{\"category1\":{\"values\":null}}}", DemographicUser.class).toString(),
                demographicUser.toString());
    }

    /**
     * Tests whether null values will be ignored when deserializing and nothing
     * else.
     */
    @Test
    public void deserializeNullDemographicValue() throws JsonMappingException, JsonProcessingException {
        DemographicUser demographicUser = new DemographicUser(null, null, null, null, new HashMap<>());
        Demographic demographic = new Demographic(null, demographicUser, "category1", true,
                ImmutableList.of(new DemographicValue("foo")), null);
        demographicUser.getDemographics().put("category1", demographic);

        assertEquals(
                new ObjectMapper().readValue("{\"demographics\":{\"category1\":{\"values\":[null, \"foo\", null]}}}",
                        DemographicUser.class).toString(),
                demographicUser.toString());
    }

    /**
     * Tests whether only DemographicValues with null inner values are removed when
     * setting the list of values.
     */
    @Test
    public void setNullDemographicValueInner() {
        Demographic demographic = new Demographic();
        List<DemographicValue> values = new ArrayList<>();
        values.add(new DemographicValue(null));
        values.add(new DemographicValue("foo"));
        values.add(new DemographicValue(null));

        demographic.setValues(values);

        assertEquals(values.size(), 1);
        assertEquals(values.get(0).getValue(), "foo");
    }

    /**
     * Tests whether deserializing without specifying multipleSelect defaults
     * multipleSelect to true.
     */
    @Test
    public void deserializeNoMultipleSelect() throws JsonProcessingException, JsonMappingException {
        DemographicUser demographicUser = new ObjectMapper().readValue(
                "{\"userId\":\"testuserid\",\"demographics\":{\"category1\":{\"values\":[5]}}}", DemographicUser.class);

        assertTrue(demographicUser.getDemographics().get("category1").isMultipleSelect());
    }

    /**
     * Tests deserializing a valid case with unknown fields.
     */
    @Test
    public void deserialize() throws JsonProcessingException, JsonMappingException {
        DemographicUser demographicUser = new DemographicUser(null, null, null, "testuserid", new LinkedHashMap<>());
        Demographic demographic1 = new Demographic(null, demographicUser, "category1", true,
                ImmutableList.of(new DemographicValue(-7), new DemographicValue(-6.3), new DemographicValue(1),
                        new DemographicValue("foo")),
                null);
        demographicUser.getDemographics().put("category1", demographic1);
        Demographic demographic2 = new Demographic(null, demographicUser, "category2", false,
                ImmutableList.of(new DemographicValue(5.3)),
                null);
        demographicUser.getDemographics().put("category2", demographic2);
        Demographic demographic3 = new Demographic(null, demographicUser, "category3", true, ImmutableList.of(),
                "testunits");
        demographicUser.getDemographics().put("category3", demographic3);

        DemographicUser deserialized = new ObjectMapper()
                .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false).readValue(
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
                                "            \"values\": []," +
                                "            \"units\": \"testunits\"" +
                                "        }" +
                                "    }" +
                                "}",
                        DemographicUser.class);
        assertEquals(deserialized.toString(), demographicUser.toString());
        for (Map.Entry<String, Demographic> entry : deserialized.getDemographics().entrySet()) {
            assertEquals(entry.getKey(), entry.getValue().getCategoryName());
        }
    }
}
