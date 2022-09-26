package org.sagebionetworks.bridge.dynamodb;

import static org.testng.Assert.assertEquals;

import java.util.HashMap;
import java.util.Map;

import org.sagebionetworks.bridge.models.studies.Demographic;
import org.sagebionetworks.bridge.models.studies.DemographicUser;
import org.sagebionetworks.bridge.models.studies.DemographicValue;
import org.testng.annotations.Test;

import com.google.common.collect.ImmutableList;

public class AppDemographicsMapMarshallerTest {
    private static final AppDemographicsMapMarshaller MARSHALLER = new AppDemographicsMapMarshaller();

    @Test
    public void testConvert() {
        DemographicUser demographicUser = new DemographicUser();
        Map<String, Demographic> demographics = new HashMap<>();
        Demographic demographicNullUnitsEmptyValues = new Demographic("id1", demographicUser, "category1", true,
                ImmutableList.of(), null);
        demographics.put("category1", demographicNullUnitsEmptyValues);
        Demographic demographicMultipleValues = new Demographic("id2", demographicUser, "category2", true,
                ImmutableList.of(new DemographicValue("value1"), new DemographicValue("value2"),
                        new DemographicValue(true), new DemographicValue(false),
                        new DemographicValue(5), new DemographicValue(-7.2)),
                "units1");
        demographics.put("category2", demographicMultipleValues);
        Demographic demographicNotMultipleSelect = new Demographic("id3", demographicUser, "category3", false,
                ImmutableList.of(new DemographicValue("value3")), "units2");
        demographics.put("category3", demographicNotMultipleSelect);

        String serialized = MARSHALLER.convert(demographics);
        Map<String, Demographic> deserializedDemographics = MARSHALLER.unconvert(serialized);

        for (Map.Entry<String, Demographic> entry : demographics.entrySet()) {
            // these fields will be null when deserialized
            entry.getValue().setDemographicUser(null);
            entry.getValue().setCategoryName(null);
        }

        assertEquals(deserializedDemographics.toString(), demographics.toString());
    }
}
