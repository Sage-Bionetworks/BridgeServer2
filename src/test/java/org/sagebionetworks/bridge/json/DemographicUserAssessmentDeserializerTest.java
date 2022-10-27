package org.sagebionetworks.bridge.json;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertNull;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;

import org.sagebionetworks.bridge.models.demographics.Demographic;
import org.sagebionetworks.bridge.models.demographics.DemographicUser;
import org.sagebionetworks.bridge.models.demographics.DemographicUserAssessment;
import org.sagebionetworks.bridge.models.demographics.DemographicValue;
import org.testng.annotations.Test;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.google.common.collect.ImmutableList;

@Test
public class DemographicUserAssessmentDeserializerTest {
    /**
     * Tests whether null is deserialized as null.
     */
    @Test
    public void deserializeNull() throws JsonProcessingException, JsonMappingException {
        assertNull(BridgeObjectMapper.get().readValue("null", DemographicUserAssessment.class));
    }

    /**
     * Tests whether deserializing a JSON value throws an exception.
     */
    @Test(expectedExceptions = JsonMappingException.class)
    public void deserializeValue() throws JsonMappingException, JsonProcessingException {
        BridgeObjectMapper.get().readValue("5", DemographicUserAssessment.class);
    }

    /**
     * Tests whether an array throws an exception when deserialized.
     */
    @Test(expectedExceptions = JsonMappingException.class)
    public void deserializeArray() throws JsonProcessingException, JsonMappingException {
        BridgeObjectMapper.get().readValue("[]", DemographicUserAssessment.class);
    }

    /**
     * Tests whether deserializing an empty object will succeed but result in an
     * empty DemographicUser.
     */
    @Test
    public void deserializeEmpty() throws JsonProcessingException, JsonMappingException {
        DemographicUser demographicUser = new DemographicUser(null, null, null, null, new HashMap<>());

        assertEquals(BridgeObjectMapper.get().readValue("{}", DemographicUserAssessment.class).getDemographicUser()
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

        assertEquals(BridgeObjectMapper.get().readValue("{\"stepHistory\": null}", DemographicUserAssessment.class)
                .getDemographicUser().toString(), demographicUser.toString());
    }

    /**
     * Tests whether deserializing step history as a JSON value throws an exception.
     */
    @Test(expectedExceptions = JsonMappingException.class)
    public void deserializeValueStepHistory() throws JsonMappingException, JsonProcessingException {
        BridgeObjectMapper.get().readValue("{\"stepHistory\": 5}", DemographicUserAssessment.class);
    }

    /**
     * Test whether deserializing step history as an object throws an exception.
     */
    @Test(expectedExceptions = JsonMappingException.class)
    public void deserializeObjectStepHistory() throws JsonMappingException, JsonProcessingException {
        BridgeObjectMapper.get().readValue("{\"stepHistory\": {}}", DemographicUserAssessment.class);
    }

    /**
     * Tests whether deserializing JSON without children will succeed but result in
     * a DemographicUser without demographics.
     */
    @Test
    public void deserializeNoChildren() throws JsonProcessingException, JsonMappingException {
        DemographicUser demographicUser = new DemographicUser(null, null, null, null, new HashMap<>());

        assertEquals(BridgeObjectMapper.get().readValue("{\"stepHistory\": [5]}", DemographicUserAssessment.class)
                .getDemographicUser().toString(),
                demographicUser.toString());
    }

    /**
     * Tests whether deserializing children as null succeeds.
     */
    @Test
    public void deserializeChildrenIsNull() throws JsonMappingException, JsonProcessingException {
        DemographicUser demographicUser = new DemographicUser(null, null, null, null, new HashMap<>());

        assertEquals(
                BridgeObjectMapper.get()
                        .readValue("{\"stepHistory\": [{\"children\": null}]}", DemographicUserAssessment.class)
                        .getDemographicUser().toString(),
                demographicUser.toString());
    }

    /**
     * Tests whether deserializing children as a value throws an exception.
     */
    @Test(expectedExceptions = JsonMappingException.class)
    public void deserializeChildrenValue() throws JsonMappingException, JsonProcessingException {
        BridgeObjectMapper.get().readValue("{\"stepHistory\": [{\"children\": 5}]}", DemographicUserAssessment.class);
    }

    /**
     * Tests whether deserializing children as an object throws an exception.
     */
    @Test(expectedExceptions = JsonMappingException.class)
    public void deserializeChildrenObject() throws JsonMappingException, JsonProcessingException {
        BridgeObjectMapper.get().readValue("{\"stepHistory\": [{\"children\": {}}]}", DemographicUserAssessment.class);
    }

    /**
     * Tests whether deserilaizing children as an array of null throws an exception.
     */
    @Test(expectedExceptions = JsonMappingException.class)
    public void deserializeNullChildren() throws JsonMappingException, JsonProcessingException {
        BridgeObjectMapper.get().readValue("{\"stepHistory\": [{\"children\": [null]}]}", DemographicUserAssessment.class);
    }

    /**
     * Tests whether deserializing children as an array of JSON value throws an
     * exception.
     */
    @Test(expectedExceptions = JsonMappingException.class)
    public void deserializeValueChildren() throws JsonMappingException, JsonProcessingException {
        BridgeObjectMapper.get().readValue("{\"stepHistory\": [{\"children\": [5]}]}", DemographicUserAssessment.class);
    }

    /**
     * Tests whether deserializing children as a nested array throws an exception.
     */
    @Test(expectedExceptions = JsonMappingException.class)
    public void deserializeArrayChildren() throws JsonMappingException, JsonProcessingException {
        BridgeObjectMapper.get().readValue("{\"stepHistory\": [{\"children\": [[]]}]}", DemographicUserAssessment.class);
    }

    /**
     * Tests whether deserializing JSON with without identifier results in an error.
     */
    @Test(expectedExceptions = JsonMappingException.class)
    public void deserializeMissingIdentifier() throws JsonProcessingException, JsonMappingException {
        BridgeObjectMapper.get().readValue(
                "{" +
                        "    \"stepHistory\": [" +
                        "        {" +
                        "            \"children\": [" +
                        "                {" +
                        "                    \"value\": \"foo\"" +
                        "                }" +
                        "            ]" +
                        "        }" +
                        "    ]" +
                        "}",
                DemographicUserAssessment.class);
    }

    /**
     * Tests whether deserializing JSON with identifier as null results in an error.
     */
    @Test(expectedExceptions = JsonMappingException.class)
    public void deserializeNullIdentifier() throws JsonProcessingException, JsonMappingException {
        BridgeObjectMapper.get().readValue(
                "{" +
                        "    \"stepHistory\": [" +
                        "        {" +
                        "            \"children\": [" +
                        "                {" +
                        "                    \"identifier\": null," +
                        "                    \"value\": \"foo\"" +
                        "                }" +
                        "            ]" +
                        "        }" +
                        "    ]" +
                        "}",
                DemographicUserAssessment.class);
    }

    /**
     * Tests whether deserializing JSON with identifier as a non-string results in
     * an error.
     */
    @Test(expectedExceptions = JsonMappingException.class)
    public void deserializeNonStringIdentifier() throws JsonProcessingException, JsonMappingException {
        BridgeObjectMapper.get().readValue(
                "{" +
                        "    \"stepHistory\": [" +
                        "        {" +
                        "            \"children\": [" +
                        "                {" +
                        "                    \"identifier\": 5," +
                        "                    \"value\": \"foo\"" +
                        "                }" +
                        "            ]" +
                        "        }" +
                        "    ]" +
                        "}",
                DemographicUserAssessment.class);
    }

    /**
     * Tests whether deserializing JSON with missing value results in an error.
     */
    @Test(expectedExceptions = JsonMappingException.class)
    public void deserializeMissingValue() throws JsonProcessingException, JsonMappingException {
        BridgeObjectMapper.get().readValue(
                "{" +
                        "    \"stepHistory\": [" +
                        "        {" +
                        "            \"children\": [" +
                        "                {" +
                        "                    \"identifier\": \"foo\"" +
                        "                }" +
                        "            ]" +
                        "        }" +
                        "    ]" +
                        "}",
                DemographicUserAssessment.class);
    }

    /**
     * Tests whether deserializing JSON with an object within value of type object
     * results in an error.
     */
    @Test(expectedExceptions = JsonMappingException.class)
    public void deserializeNestedObjectWithNullValue() throws JsonProcessingException, JsonMappingException {
        BridgeObjectMapper.get().readValue(
                "{" +
                        "    \"stepHistory\": [" +
                        "        {" +
                        "            \"children\": [" +
                        "                {" +
                        "                    \"identifier\": \"foo\"," +
                        "                    \"value\": null" +
                        "                }" +
                        "            ]" +
                        "        }" +
                        "    ]" +
                        "}",
                DemographicUserAssessment.class);
    }

    /**
     * Tests whether deserializing JSON with an object within value of type object
     * results in an error.
     */
    @Test(expectedExceptions = JsonMappingException.class)
    public void deserializeNestedObjectWithObjectValue() throws JsonProcessingException, JsonMappingException {
        BridgeObjectMapper.get().readValue(
                "{" +
                        "    \"stepHistory\": [" +
                        "        {" +
                        "            \"children\": [" +
                        "                {" +
                        "                    \"identifier\": \"foo\"," +
                        "                    \"value\": {\"foo\": {}}" +
                        "                }" +
                        "            ]" +
                        "        }" +
                        "    ]" +
                        "}",
                DemographicUserAssessment.class);
    }

    /**
     * Tests whether deserializing JSON with an array within value of type object
     * results in an error.
     */
    @Test(expectedExceptions = JsonMappingException.class)
    public void deserializeNestedArrayWithObjectValue() throws JsonProcessingException, JsonMappingException {
        BridgeObjectMapper.get().readValue(
                "{" +
                        "    \"stepHistory\": [" +
                        "        {" +
                        "            \"children\": [" +
                        "                {" +
                        "                    \"identifier\": \"foo\"," +
                        "                    \"value\": {\"foo\": []}" +
                        "                }" +
                        "            ]" +
                        "        }" +
                        "    ]" +
                        "}",
                DemographicUserAssessment.class);
    }

    /**
     * Tests whether deserializing JSON with an object within value of type array
     * results in an error.
     */
    @Test(expectedExceptions = JsonMappingException.class)
    public void deserializeNestedObjectWithArrayValue() throws JsonProcessingException, JsonMappingException {
        BridgeObjectMapper.get().readValue(
                "{" +
                        "    \"stepHistory\": [" +
                        "        {" +
                        "            \"children\": [" +
                        "                {" +
                        "                    \"identifier\": \"foo\"," +
                        "                    \"value\": [{}]" +
                        "                }" +
                        "            ]" +
                        "        }" +
                        "    ]" +
                        "}",
                DemographicUserAssessment.class);
    }

    /**
     * Tests whether deserializing JSON with an array within value of type array
     * results in an error.
     */
    @Test(expectedExceptions = JsonMappingException.class)
    public void deserializeNestedArrayWithArrayValue() throws JsonProcessingException, JsonMappingException {
        BridgeObjectMapper.get().readValue(
                "{" +
                        "    \"stepHistory\": [" +
                        "        {" +
                        "            \"children\": [" +
                        "                {" +
                        "                    \"identifier\": \"foo\"," +
                        "                    \"value\": [[]]" +
                        "                }" +
                        "            ]" +
                        "        }" +
                        "    ]" +
                        "}",
                DemographicUserAssessment.class);
    }

    /**
     * Tests deserializing a valid case with unknown fields and a repeated category
     * (the last specified value for a category should be the one that is used).
     */
    @Test
    public void deserialize() throws JsonProcessingException, JsonMappingException {
        DemographicUser demographicUser = new DemographicUser(null, null, null, null, new HashMap<>());
        Demographic demographic1 = new Demographic(null, demographicUser, "category1", true,
                ImmutableList.of(new DemographicValue(new BigDecimal("-7")), new DemographicValue(new BigDecimal("-6.3")), new DemographicValue(new BigDecimal("1")),
                        new DemographicValue("foo")),
                null);
        demographicUser.getDemographics().put("category1", demographic1);
        Demographic demographic2 = new Demographic(null, demographicUser, "category2", false,
                ImmutableList.of(new DemographicValue(new BigDecimal("5.3"))), null);
        demographicUser.getDemographics().put("category2", demographic2);
        Demographic demographic3 = new Demographic(null, demographicUser, "category3", true,
                ImmutableList.of(new DemographicValue("null")), "cm");
        demographicUser.getDemographics().put("category3", demographic3);
        Demographic demographic4 = new Demographic(null, demographicUser, "category4", true,
                ImmutableList.of(new DemographicValue("foo", "1.7"), new DemographicValue("bar", "null"),
                        new DemographicValue("baz", "qux")),
                null);
        demographicUser.getDemographics().put("category4", demographic4);

        assertEquals(BridgeObjectMapper.get().readValue(
                "{" +
                        "    \"unknown field\": null," +
                        "    \"stepHistory\": [" +
                        "        {" +
                        "            \"unknown field\": null" +
                        "        }," +
                        "        {" +
                        "            \"children\": [" +
                        "                {" +
                        "                    \"unknown field\": null," +
                        "                    \"identifier\": \"category1\"," +
                        "                    \"value\": [" +
                        "                        -7," +
                        "                        -6.3," +
                        "                        1," +
                        "                        \"foo\"" +
                        "                    ]" +
                        "                }," +
                        "                {" +
                        "                    \"unknown field\": null," +
                        "                    \"identifier\": \"category2\"," +
                        "                    \"answerType\": null," +
                        "                    \"value\": 5.3" +
                        "                }," +
                        "                {" +
                        "                    \"unknown field\": null," +
                        "                    \"identifier\": \"category3\"," +
                        "                    \"answerType\": {" +
                        "                        \"type\": \"ArRaY\"," +
                        "                        \"unit\": \"cm\"," +
                        "                        \"unknown field\": null" +
                        "                    }," +
                        "                    \"value\": [" +
                        "                        null" +
                        "                    ]" +
                        "                }," +
                        "                {" +
                        "                    \"unknown field\": null," +
                        "                    \"identifier\": \"category4\"," +
                        "                    \"answerType\": {" +
                        "                        \"type\": \"ObJeCt\"," +
                        "                        \"unknown field\": null" +
                        "                    }," +
                        "                    \"value\": {" +
                        "                        \"foo\": 1.7," +
                        "                        \"bar\": null," +
                        "                        \"baz\": \"qux\"" +
                        "                    }" +
                        "                }" +
                        "            ]" +
                        "        }" +
                        "    ]" +
                        "}",
                DemographicUserAssessment.class).getDemographicUser().toString(),
                demographicUser.toString());
    }

    /**
     * Tests whether the example JSON from the Bridge mobile client assessment
     * schema can be deserialized successfully
     * (https://github.com/Sage-Bionetworks/mobile-client-json/blob/d59ba3a12739f8d6dfea5143c02fa1c987bb4b5a/schemas/v2/AssessmentResultObject.json#L109-L328)
     */
    @Test
    public void deserializeMobileClientExample() throws JsonMappingException, JsonProcessingException {
        // currently losing float precision because JsonNode uses float for number
        // representation, we could use
        // DeserializationFeature.USE_BIG_DECIMAL_FOR_FLOATS but that would reduce
        // performance
        DemographicUser demographicUser = new DemographicUser(null, null, null, null, null);
        Demographic demographic1 = new Demographic(null, demographicUser, "question1", false,
                ImmutableList.of(new DemographicValue(true)), null);
        Demographic demographic2 = new Demographic(null, demographicUser, "question2", false,
                ImmutableList.of(new DemographicValue(new BigDecimal("42"))), null);
        Demographic demographic3 = new Demographic(null, demographicUser, "question3", false,
                ImmutableList.of(new DemographicValue(new BigDecimal("3.14"))), null);
        Demographic demographic4 = new Demographic(null, demographicUser, "question4", true,
                ImmutableList.of(new DemographicValue("foo", "ba")), null);
        Demographic demographic5 = new Demographic(null, demographicUser, "question5", false,
                ImmutableList.of(new DemographicValue("foo")), null);
        Demographic demographic6 = new Demographic(null, demographicUser, "question6", true,
                ImmutableList.of(new DemographicValue(new BigDecimal("3.2")), new DemographicValue(new BigDecimal("5.1"))),
                null);
        Demographic demographic7 = new Demographic(null, demographicUser, "question7", true,
                ImmutableList.of(new DemographicValue(new BigDecimal("1")), new DemographicValue(new BigDecimal("5"))), null);
        Demographic demographic8 = new Demographic(null, demographicUser, "question8", true, ImmutableList
                .of(new DemographicValue("foo"), new DemographicValue("ba"), new DemographicValue("lalala")), null);
        Demographic demographic9 = new Demographic(null, demographicUser, "question9", false,
                ImmutableList.of(new DemographicValue("2020-04")), null);
        Demographic demographic10 = new Demographic(null, demographicUser, "question10", false,
                ImmutableList.of(new DemographicValue("08:30")), null);
        Demographic demographic11 = new Demographic(null, demographicUser, "question11", false,
                ImmutableList.of(new DemographicValue("2017-10-16T22:28:09.000-07:00")), null);
        Demographic demographic12 = new Demographic(null, demographicUser, "question12", false,
                ImmutableList.of(new DemographicValue("22:28:00.000")), null);
        Demographic demographic13 = new Demographic(null, demographicUser, "question13", false,
                ImmutableList.of(new DemographicValue(new BigDecimal("75"))), null);
        Demographic demographic14 = new Demographic(null, demographicUser, "question14", false,
                ImmutableList.of(new DemographicValue(new BigDecimal("170.2"))), "cm");
        Map<String, Demographic> demographics = new HashMap<>();
        demographics.put("question1", demographic1);
        demographics.put("question2", demographic2);
        demographics.put("question3", demographic3);
        demographics.put("question4", demographic4);
        demographics.put("question5", demographic5);
        demographics.put("question6", demographic6);
        demographics.put("question7", demographic7);
        demographics.put("question8", demographic8);
        demographics.put("question9", demographic9);
        demographics.put("question10", demographic10);
        demographics.put("question11", demographic11);
        demographics.put("question12", demographic12);
        demographics.put("question13", demographic13);
        demographics.put("question14", demographic14);
        demographicUser.setDemographics(demographics);
        assertEquals(BridgeObjectMapper.get().readValue(
                "{" +
                        "    \"stepHistory\": [" +
                        "        {" +
                        "            \"endDate\": \"2017-10-16T22:28:29.000-07:00\"," +
                        "            \"identifier\": \"introduction\"," +
                        "            \"startDate\": \"2017-10-16T22:28:09.000-07:00\"," +
                        "            \"type\": \"base\"" +
                        "        }," +
                        "        {" +
                        "            \"children\": [" +
                        "                {" +
                        "                    \"answerType\": {" +
                        "                        \"type\": \"boolean\"" +
                        "                    }," +
                        "                    \"endDate\": \"2017-10-16T22:28:09.000-07:00\"," +
                        "                    \"identifier\": \"question1\"," +
                        "                    \"startDate\": \"2017-10-16T22:28:09.000-07:00\"," +
                        "                    \"type\": \"answer\"," +
                        "                    \"value\": true" +
                        "                }," +
                        "                {" +
                        "                    \"answerType\": {" +
                        "                        \"type\": \"integer\"" +
                        "                    }," +
                        "                    \"endDate\": \"2017-10-16T22:28:09.000-07:00\"," +
                        "                    \"identifier\": \"question2\"," +
                        "                    \"startDate\": \"2017-10-16T22:28:09.000-07:00\"," +
                        "                    \"type\": \"answer\"," +
                        "                    \"value\": 42" +
                        "                }," +
                        "                {" +
                        "                    \"answerType\": {" +
                        "                        \"type\": \"number\"" +
                        "                    }," +
                        "                    \"endDate\": \"2017-10-16T22:28:09.000-07:00\"," +
                        "                    \"identifier\": \"question3\"," +
                        "                    \"startDate\": \"2017-10-16T22:28:09.000-07:00\"," +
                        "                    \"type\": \"answer\"," +
                        "                    \"value\": 3.1400000000000001" +
                        "                }," +
                        "                {" +
                        "                    \"answerType\": {" +
                        "                        \"type\": \"object\"" +
                        "                    }," +
                        "                    \"endDate\": \"2017-10-16T22:28:09.000-07:00\"," +
                        "                    \"identifier\": \"question4\"," +
                        "                    \"startDate\": \"2017-10-16T22:28:09.000-07:00\"," +
                        "                    \"type\": \"answer\"," +
                        "                    \"value\": {" +
                        "                        \"foo\": \"ba\"" +
                        "                    }" +
                        "                }," +
                        "                {" +
                        "                    \"answerType\": {" +
                        "                        \"type\": \"string\"" +
                        "                    }," +
                        "                    \"endDate\": \"2017-10-16T22:28:09.000-07:00\"," +
                        "                    \"identifier\": \"question5\"," +
                        "                    \"startDate\": \"2017-10-16T22:28:09.000-07:00\"," +
                        "                    \"type\": \"answer\"," +
                        "                    \"value\": \"foo\"" +
                        "                }," +
                        "                {" +
                        "                    \"answerType\": {" +
                        "                        \"baseType\": \"number\"," +
                        "                        \"type\": \"array\"" +
                        "                    }," +
                        "                    \"endDate\": \"2017-10-16T22:28:09.000-07:00\"," +
                        "                    \"identifier\": \"question6\"," +
                        "                    \"startDate\": \"2017-10-16T22:28:09.000-07:00\"," +
                        "                    \"type\": \"answer\"," +
                        "                    \"value\": [" +
                        "                        3.2000000000000002," +
                        "                        5.0999999999999996" +
                        "                    ]" +
                        "                }," +
                        "                {" +
                        "                    \"answerType\": {" +
                        "                        \"baseType\": \"integer\"," +
                        "                        \"type\": \"array\"" +
                        "                    }," +
                        "                    \"endDate\": \"2017-10-16T22:28:09.000-07:00\"," +
                        "                    \"identifier\": \"question7\"," +
                        "                    \"startDate\": \"2017-10-16T22:28:09.000-07:00\"," +
                        "                    \"type\": \"answer\"," +
                        "                    \"value\": [" +
                        "                        1," +
                        "                        5" +
                        "                    ]" +
                        "                }," +
                        "                {" +
                        "                    \"answerType\": {" +
                        "                        \"baseType\": \"string\"," +
                        "                        \"type\": \"array\"" +
                        "                    }," +
                        "                    \"endDate\": \"2017-10-16T22:28:09.000-07:00\"," +
                        "                    \"identifier\": \"question8\"," +
                        "                    \"startDate\": \"2017-10-16T22:28:09.000-07:00\"," +
                        "                    \"type\": \"answer\"," +
                        "                    \"value\": [" +
                        "                        \"foo\"," +
                        "                        \"ba\"," +
                        "                        \"lalala\"" +
                        "                    ]" +
                        "                }," +
                        "                {" +
                        "                    \"answerType\": {" +
                        "                        \"codingFormat\": \"yyyy-MM\"," +
                        "                        \"type\": \"date-time\"" +
                        "                    }," +
                        "                    \"endDate\": \"2017-10-16T22:28:09.000-07:00\"," +
                        "                    \"identifier\": \"question9\"," +
                        "                    \"startDate\": \"2017-10-16T22:28:09.000-07:00\"," +
                        "                    \"type\": \"answer\"," +
                        "                    \"value\": \"2020-04\"" +
                        "                }," +
                        "                {" +
                        "                    \"answerType\": {" +
                        "                        \"codingFormat\": \"HH:mm\"," +
                        "                        \"type\": \"date-time\"" +
                        "                    }," +
                        "                    \"endDate\": \"2017-10-16T22:28:09.000-07:00\"," +
                        "                    \"identifier\": \"question10\"," +
                        "                    \"startDate\": \"2017-10-16T22:28:09.000-07:00\"," +
                        "                    \"type\": \"answer\"," +
                        "                    \"value\": \"08:30\"" +
                        "                }," +
                        "                {" +
                        "                    \"answerType\": {" +
                        "                        \"codingFormat\": \"yyyy-MM-dd'T'HH:mm:ss.SSSZZZZZ\"," +
                        "                        \"type\": \"date-time\"" +
                        "                    }," +
                        "                    \"endDate\": \"2017-10-16T22:28:09.000-07:00\"," +
                        "                    \"identifier\": \"question11\"," +
                        "                    \"startDate\": \"2017-10-16T22:28:09.000-07:00\"," +
                        "                    \"type\": \"answer\"," +
                        "                    \"value\": \"2017-10-16T22:28:09.000-07:00\"" +
                        "                }," +
                        "                {" +
                        "                    \"answerType\": {" +
                        "                        \"codingFormat\": \"HH:mm:ss.SSS\"," +
                        "                        \"type\": \"time\"" +
                        "                    }," +
                        "                    \"endDate\": \"2017-10-16T22:28:09.000-07:00\"," +
                        "                    \"identifier\": \"question12\"," +
                        "                    \"startDate\": \"2017-10-16T22:28:09.000-07:00\"," +
                        "                    \"type\": \"answer\"," +
                        "                    \"value\": \"22:28:00.000\"" +
                        "                }," +
                        "                {" +
                        "                    \"answerType\": {" +
                        "                        \"displayUnits\": [" +
                        "                            \"hour\"," +
                        "                            \"minute\"" +
                        "                        ]," +
                        "                        \"significantDigits\": 0," +
                        "                        \"type\": \"duration\"" +
                        "                    }," +
                        "                    \"endDate\": \"2017-10-16T22:28:09.000-07:00\"," +
                        "                    \"identifier\": \"question13\"," +
                        "                    \"startDate\": \"2017-10-16T22:28:09.000-07:00\"," +
                        "                    \"type\": \"answer\"," +
                        "                    \"value\": 75" +
                        "                }," +
                        "                {" +
                        "                    \"answerType\": {" +
                        "                        \"type\": \"measurement\"," +
                        "                        \"unit\": \"cm\"" +
                        "                    }," +
                        "                    \"endDate\": \"2017-10-16T22:28:09.000-07:00\"," +
                        "                    \"identifier\": \"question14\"," +
                        "                    \"startDate\": \"2017-10-16T22:28:09.000-07:00\"," +
                        "                    \"type\": \"answer\"," +
                        "                    \"value\": 170.19999999999999" +
                        "                }" +
                        "            ]," +
                        "            \"endDate\": \"2017-10-16T22:30:29.000-07:00\"," +
                        "            \"identifier\": \"answers\"," +
                        "            \"startDate\": \"2017-10-16T22:28:29.000-07:00\"," +
                        "            \"type\": \"collection\"" +
                        "        }," +
                        "        {" +
                        "            \"endDate\": \"2017-10-16T22:30:49.000-07:00\"," +
                        "            \"identifier\": \"conclusion\"," +
                        "            \"startDate\": \"2017-10-16T22:30:29.000-07:00\"," +
                        "            \"type\": \"base\"" +
                        "        }" +
                        "    ]," +
                        "    \"type\": \"assessment\"," +
                        "    \"asyncResults\": [" +
                        "        {" +
                        "            \"contentType\": \"application/json\"," +
                        "            \"endDate\": \"2017-10-16T22:30:29.000-07:00\"," +
                        "            \"identifier\": \"fileResult\"," +
                        "            \"jsonSchema\": \"file://temp/foo.schema.json\"," +
                        "            \"relativePath\": \"/foo.json\"," +
                        "            \"startDate\": \"2017-10-16T22:28:29.000-07:00\"," +
                        "            \"startUptime\": 1234.567," +
                        "            \"type\": \"file\"" +
                        "        }" +
                        "    ]," +
                        "    \"identifier\": \"example\"," +
                        "    \"path\": [" +
                        "        {" +
                        "            \"direction\": \"forward\"," +
                        "            \"identifier\": \"introduction\"" +
                        "        }," +
                        "        {" +
                        "            \"direction\": \"forward\"," +
                        "            \"identifier\": \"answers\"" +
                        "        }," +
                        "        {" +
                        "            \"direction\": \"forward\"," +
                        "            \"identifier\": \"conclusion\"" +
                        "        }" +
                        "    ]," +
                        "    \"startDate\": \"2017-10-16T22:28:09.000-07:00\"," +
                        "    \"taskRunUUID\": \"6112F03C-D52A-42F7-8634-74403DA59554\"," +
                        "    \"endDate\": \"2017-10-16T22:30:49.000-07:00\"" +
                        "}",
                DemographicUserAssessment.class).getDemographicUser().toString(), demographicUser.toString());
    }
}
