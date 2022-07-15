package org.sagebionetworks.bridge.json;

import java.util.ArrayList;
import java.util.List;

import org.sagebionetworks.bridge.models.studies.Demographic;
import org.sagebionetworks.bridge.models.studies.DemographicUser;
import org.testng.annotations.Test;
import static org.testng.Assert.assertEquals;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.ObjectMapper;

@Test
public class DemographicCollectionSerializerDeserializerTest {
    // @Test
    // public void serialize() throws JsonProcessingException {
    //     Demographic demographic1 = new Demographic("study1", "user1", "category1", "value1", "units1");
    //     Demographic demographic2 = new Demographic("study2", "user2", "category2", "value2", "units2");
    //     List<Demographic> demographicsList = new ArrayList<>();
    //     demographicsList.add(demographic1);
    //     demographicsList.add(demographic2);
    //     DemographicCollection demographics = new DemographicCollection(demographicsList);
    //     assertEquals(new ObjectMapper().writeValueAsString(demographics),
    //             "{\"category1\":{\"value\":\"value1\",\"units\":\"units1\"},\"category2\":{\"value\":\"value2\",\"units\":\"units2\"}}");
    // }

    // @Test
    // public void serializeNullUnits() throws JsonProcessingException {
    //     Demographic demographic1 = new Demographic("study1", "user1", "category1", "value1", null);
    //     Demographic demographic2 = new Demographic("study2", "user2", "category2", "value2", null);
    //     List<Demographic> demographicsList = new ArrayList<>();
    //     demographicsList.add(demographic1);
    //     demographicsList.add(demographic2);
    //     DemographicCollection demographics = new DemographicCollection(demographicsList);
    //     assertEquals(new ObjectMapper().writeValueAsString(demographics),
    //             "{\"category1\":{\"value\":\"value1\"},\"category2\":{\"value\":\"value2\"}}");
    // }

    // @Test
    // public void deserialize() throws JsonProcessingException, JsonMappingException {
    //     Demographic demographic1 = new Demographic(null, null, "category1", "value1", "units1");
    //     Demographic demographic2 = new Demographic(null, null, "category2", "value2", "units2");
    //     List<Demographic> demographicsList = new ArrayList<>();
    //     demographicsList.add(demographic1);
    //     demographicsList.add(demographic2);
    //     DemographicCollection demographics = new DemographicCollection(demographicsList);
    //     DemographicCollection demographicsTest = new ObjectMapper().readValue(
    //             "{\"category1\":{\"value\":\"value1\",\"units\":\"units1\"},\"category2\":{\"value\":\"value2\",\"units\":\"units2\"}}",
    //             DemographicCollection.class);
    //     assertEquals(demographicsTest.toString(), demographics.toString());
    // }
}
