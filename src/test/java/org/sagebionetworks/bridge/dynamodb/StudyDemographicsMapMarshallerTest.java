package org.sagebionetworks.bridge.dynamodb;

import static org.testng.Assert.assertEquals;

import java.util.HashMap;
import java.util.Map;

import org.sagebionetworks.bridge.models.studies.Demographic;
import org.sagebionetworks.bridge.models.studies.DemographicUser;
import org.sagebionetworks.bridge.models.studies.DemographicValue;
import org.testng.annotations.Test;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;

public class StudyDemographicsMapMarshallerTest {
    private static final StudyDemographicsMapMarshaller MARSHALLER = new StudyDemographicsMapMarshaller();

    @Test
    public void convert() {
        DemographicUser demographicUser = new DemographicUser();
        Map<String, Demographic> study1Demographics = new HashMap<>();
        Demographic demographicNullUnitsEmptyValues = new Demographic("id1", demographicUser, "category1", true,
                ImmutableList.of(), null);
        study1Demographics.put("category1", demographicNullUnitsEmptyValues);
        Demographic demographicMultipleValues = new Demographic("id2", demographicUser, "category2", true,
                ImmutableList.of(new DemographicValue("value1"), new DemographicValue("value2").withInvalidity("invalid data"),
                        new DemographicValue("true"), new DemographicValue("false"),
                        new DemographicValue("5"), new DemographicValue("-7.2")),
                "units1");
        study1Demographics.put("category2", demographicMultipleValues);
        Map<String, Demographic> study2Demographics = new HashMap<>();
        Demographic demographicNotMultipleSelect = new Demographic("id3", demographicUser, "category3", false,
                ImmutableList.of(new DemographicValue("value3")), "units2");
        study2Demographics.put("category3", demographicNotMultipleSelect);

        Map<String, Map<String, Demographic>> allStudyDemographics = ImmutableMap.of("study1-id", study1Demographics,
                "study2-id", study2Demographics);

        String serialized = MARSHALLER.convert(allStudyDemographics);
        Map<String, Map<String, Demographic>> deserializedDemographics = MARSHALLER.unconvert(serialized);

        for (Map<String, Demographic> demographics : allStudyDemographics.values()) {
            for (Map.Entry<String, Demographic> entry : demographics.entrySet()) {
                // these fields will be null when deserialized
                entry.getValue().setDemographicUser(null);
                entry.getValue().setCategoryName(null);
            }
        }

        assertEquals(deserializedDemographics.toString(), allStudyDemographics.toString());
    }
}
